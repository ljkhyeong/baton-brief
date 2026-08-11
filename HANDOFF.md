# HANDOFF

진행 중 작업 없음.

## 다음 결정

1. BATON이 발행할 최소 domain-event envelope와 versioning 규칙을 채택한다.
2. 첫 `AttentionItem` 대상 event 3~5개와 안정적인 `reasonCode`를 정한다.
3. edition window, timezone, generation cursor와 rebuild 계약을 정한다.
4. 위 계약을 기준으로 기술 스택과 모듈 구조를 선택한다.

## 현재 제한

- production code, runtime과 배포 구성은 없다.
- BATON, WATCH, RELAY, GO와 채택된 실행 계약은 없다.
- GitHub Actions와 릴리스 정책은 아직 없다.
