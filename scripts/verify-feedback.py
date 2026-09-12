#!/usr/bin/env python3
"""수정 파일 검사와 작업 시작 이후의 전체 변경 검사를 실행한다."""

import argparse
import ast
import json
from pathlib import Path
import subprocess
import sys
import tomllib


ROOT = Path(__file__).resolve().parents[1]
MODULES = {"domain", "application", "adapter-in-web", "adapter-out-persistence", "bootstrap"}


def run(*args, capture=False, allowed=(0,)):
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE if capture else None)
    if result.returncode not in allowed:
        raise subprocess.CalledProcessError(result.returncode, args)
    return result.stdout if capture else None


def git_paths(*args):
    return run("git", *args, "-z", capture=True).decode().rstrip("\0").split("\0")


def reject_json_constant(value):
    raise ValueError(f"JSON에서 허용하지 않는 값입니다: {value}")


def check_files(paths, allow_deleted=False):
    tasks = set()
    structural = False
    for name in sorted(set(paths) - {""}):
        path = ROOT / name
        print(f"검사: {name}", flush=True)
        if path.is_file():
            # no-index의 1은 차이가 있다는 뜻이며 공백 오류는 별도 종료 코드다.
            run("git", "diff", "--no-index", "--check", "--", "/dev/null", name, allowed=(0, 1))
            if path.suffix == ".sh":
                run("bash", "-n", name)
            elif path.suffix == ".py":
                ast.parse(path.read_text(), filename=name)
            elif path.suffix == ".toml":
                tomllib.loads(path.read_text())
            elif path.suffix == ".json":
                json.loads(path.read_text(), parse_constant=reject_json_constant)
        elif not path.exists() and not allow_deleted:
            run("git", "ls-files", "--error-unmatch", "--", name, capture=True)

        parts = Path(name).parts
        if name.endswith((".kt", ".java")) and len(parts) > 2 and parts[0] in MODULES:
            task = "testClasses" if parts[1:3] == ("src", "test") else "classes"
            tasks.add(f":{parts[0]}:{task}")
            structural = True
        elif name.endswith(".gradle.kts") or name.startswith("gradle/") or name in {"gradlew", "gradle.properties"}:
            tasks.add("help")
            structural = True
    return tasks, structural


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="mode", required=True)
    files = commands.add_parser("files", help="저장소 기준 경로로 수정 파일 검사")
    files.add_argument("paths", nargs="+")
    final = commands.add_parser("final", help="작업 시작 커밋 이후 전체 diff와 구조 검사")
    final.add_argument("base", help="작업 시작 시 기록한 커밋")
    args = parser.parse_args()

    if args.mode == "files":
        paths = [str((ROOT / path).resolve().relative_to(ROOT)) for path in args.paths]
    else:
        base = run("git", "rev-parse", "--verify", args.base + "^{commit}", capture=True).decode().strip()
        run("git", "diff", "--check", base, "--")
        run("git", "diff", "--cached", "--check")
        print("작업 시작 이후 현재 파일 변경:", flush=True)
        run("git", "diff", "--no-ext-diff", "--no-color", base, "--")
        print("현재 스테이징한 변경:", flush=True)
        run("git", "diff", "--cached", "--no-ext-diff", "--no-color", "--")
        untracked = list(filter(None, git_paths("ls-files", "--others", "--exclude-standard")))
        if untracked:
            print("새 파일:", flush=True)
        for path in untracked:
            run("git", "diff", "--no-index", "--no-color", "--", "/dev/null", path, allowed=(0, 1))
        paths = (git_paths("diff", "--name-only", "--no-renames", base)
                 + git_paths("diff", "--cached", "--name-only", "--no-renames") + untracked)

    tasks, structural = check_files(paths, allow_deleted=args.mode == "final")
    if args.mode == "final" and structural:
        tasks.add(":bootstrap:architectureTest")
    if tasks:
        run(str(ROOT / "gradlew"), *sorted(tasks))
    print("선택한 파일·구조 검사 통과. 동작 테스트와 diff 내용 검토는 별도로 확인하세요.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (subprocess.CalledProcessError, ValueError, SyntaxError) as error:
        print(f"검사 실패: {error}", file=sys.stderr)
        sys.exit(1)
