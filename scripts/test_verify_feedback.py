import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class FeedbackTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        self.git("init", "-q")
        self.git("config", "user.name", "검증")
        self.git("config", "user.email", "test@example.invalid")
        self.write("scripts/verify-feedback.py", Path(__file__).with_name("verify-feedback.py").read_text())
        self.write("gradlew", '#!/bin/sh\nprintf "%s\\n" "$@" >> gradle-tasks.log\n')
        (self.root / "gradlew").chmod(0o755)
        self.write(".gitignore", "*.log\n")
        self.write("tracked.md", "기준 내용\n")
        self.git("add", ".")
        self.git("commit", "-qm", "기준")
        self.base = self.git("rev-parse", "HEAD").strip()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, env=self.env, text=True)

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def verify(self, *args):
        return subprocess.run([sys.executable, "scripts/verify-feedback.py", *args],
                              cwd=self.root, env=self.env, capture_output=True, text=True)

    def test_files_compile_each_changed_module_once(self):
        paths = ["domain/src/main/kotlin/첫 파일.kt", "domain/src/main/kotlin/Second.kt",
                 "bootstrap/src/test/kotlin/Example.kt"]
        for path in paths:
            self.write(path, "class Example\n")
        result = self.verify("files", *paths)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.root / "gradle-tasks.log").read_text().splitlines(),
                         [":bootstrap:testClasses", ":domain:classes"])

    def test_final_includes_committed_staged_unstaged_untracked_and_deleted_files(self):
        self.write("domain/src/main/kotlin/Committed.kt", "class Committed\n")
        self.git("add", ".")
        self.git("commit", "-qm", "중간 커밋")
        self.write("staged.md", "staged\n")
        self.git("add", "staged.md")
        self.write("staged.md", "unstaged\n")
        self.write("새 파일.md", "untracked\n")
        (self.root / ".gitignore").unlink()
        result = self.verify("final", self.base)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for expected in ["Committed.kt", "staged.md", "unstaged", "새 파일.md", "untracked", ".gitignore"]:
            self.assertIn(expected, result.stdout)
        self.assertIn(":bootstrap:architectureTest", (self.root / "gradle-tasks.log").read_text())

    def test_document_only_final_does_not_run_gradle(self):
        self.write("new.md", "문서 변경\n")
        self.assertEqual(self.verify("final", self.base).returncode, 0)
        self.assertFalse((self.root / "gradle-tasks.log").exists())

    def test_final_keeps_staged_diff_when_worktree_matches_base_and_allows_committed_deletion(self):
        self.git("rm", "-q", ".gitignore")
        self.git("commit", "-qm", "파일 삭제")
        self.write("tracked.md", "스테이징 내용\n")
        self.git("add", "tracked.md")
        self.write("tracked.md", "기준 내용\n")
        result = self.verify("final", self.base)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("스테이징 내용", result.stdout)
        self.assertIn(".gitignore", result.stdout)

    def test_invalid_file_or_base_stops_before_gradle(self):
        self.write("broken.sh", "if true; then\n")
        self.write("spaces.md", "문장  \n")
        for args in [("files", "broken.sh"), ("files", "spaces.md"),
                     ("files", "missing.kt"), ("final", "missing-ref")]:
            with self.subTest(args=args):
                self.assertNotEqual(self.verify(*args).returncode, 0)
                self.assertFalse((self.root / "gradle-tasks.log").exists())


if __name__ == "__main__":
    unittest.main()
