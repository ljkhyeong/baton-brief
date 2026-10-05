@AGENTS.md

## Claude Code

- 작업별 스킬은 `.claude/skills/<이름>` 링크로 읽는다. 원본은 `.agents/skills/<이름>`이므로 스킬을
  고칠 때는 원본 경로를 편집한다. 링크를 새로 만든 세션에서는 `/reload-skills` 후 사용한다.
- 오래 걸리는 Gradle 검증은 백그라운드로 한 번만 실행하고 완료를 기다린다. 하위 에이전트에 검증을
  맡길 때도 같은 작업 폴더에서 Gradle 명령이 겹치지 않게 한다.
