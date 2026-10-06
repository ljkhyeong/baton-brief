---
name: baton-brief-event-flows
description: BATON BRIEF의 이벤트 계약·멱등 수신·충돌 판정·수신 증거·보존 정책과 생산자 호환성을 변경할 때 사용한다.
---

# BATON BRIEF 이벤트 수신

## 관련 계약

변경 대상의 문서만 읽는다.

- 봉투·수신 결과: [MVP 계약](../../../docs/PRD/0002_mvp-contract/spec.md)
- 버전·심각도 계약: [이벤트 v2](../../../docs/PRD/0019_baton-continuity-event-v2/spec.md),
  [계약 팩](../../../contracts/README.md)과 `contracts/VERSION`
- 수신 증거 조회: [단건](../../../docs/PRD/0007_event-receipt-query/spec.md),
  [이상 이력](../../../docs/PRD/0011_event-receipt-anomalies/spec.md)
- 기록 보존: [보존·재구축](../../../docs/PRD/0008_retention-rebuild-boundary/spec.md)
- BATON 연동: [생산자 선행조건](../../../docs/PRD/0018_baton-producer-compatibility/spec.md)
- Bearer 인증·Caddy 변경은 [공통 설정 스킬](../baton-brief-flows/SKILL.md), 수신 기록 열·제약 변경은
  [저장소 스킬](../baton-brief-persistence-flows/SKILL.md), 수신 결과 지표는 [운영 스킬](../baton-brief-operations-flows/SKILL.md)을
  함께 사용한다.

## 수신 시 유지할 규칙

- 같은 `eventId`로 저장된 최초 수신 필드를 `SourceEvent` 동등 비교로 새 요청과 대조한다. 모두 같으면
  투영을 다시 적용하지 않고, 하나라도 다르면 충돌이며 최초 수신 기록과 투영을 덮어쓰지 않는다.
  미지원 이벤트의 재전달은 `UNSUPPORTED`를 유지한다. `event:<eventId>` 잠금 아래 저장 행을 읽어
  판정하므로 수신 순번을 소비하지 않는다.
- 수신 기록과 투영 효과는 원자적으로 저장한다. 시각은 PostgreSQL 마이크로초 정밀도로
  정규화해 저장·비교하며 임의 JSON 재직렬화 결과를 비교 입력으로 쓰지 않는다.
- 지원 버전은 `2`뿐이며 필수 `sourceSeverity`를 수신 증거와 비교 필드로 보존한다. `3` 이상은
  `UNSUPPORTED`로 보존하고 심각도가 없으면 `null`로 저장·비교한다.
- 수신 증거는 최초 `processingOutcome`을 반환한다. `DUPLICATE`·`CONFLICT`로 덮어쓰지 않으며
  충돌은 최초 탐지 시각만 저장·표시한다. 원문 payload는 저장하거나 응답에 노출하지 않는다.
- `UNSUPPORTED`를 포함한 모든 수신 기록과 이벤트별 최초 충돌 한 건은 대체 보존 계약의
  채택·마이그레이션·검증 전까지 삭제·압축하지 않는다. 기존 `sourceCursor` 근거도 유지한다.
- 이상 이력의 커서는 요청 간 스냅샷이 아니다. 수신 후 충돌이 추가될 수 있으므로 최신 상태는
  첫 페이지부터 확인한다.
- 생산자 변경은 승인된 저장소 범위에서 수행한다. 조회 신호의 이름 변경이나 JPA `@Version`을
  외부 이벤트 계약으로 대체하지 않는다. RC 승격은 HANDOFF의 생산자·실제 스테이징 검증 조건을 따른다.

## 변경에 맞는 검증

- 수신·비교 필드·저장 변경은 최초 수신, 중복·동시 중복, 충돌, 미지원과 실패 후 재시도를 확인한다.
- 수신 판정은 저장 열 왕복 동등 비교에 의존한다. 열 타입·정밀도·정렬(예: timestamp 정밀도, trim,
  citext)을 바꾸면 동일 재전달이 `DUPLICATE`로 남는지 경계 연도 재전달 테스트로 확인한다.
- 조회·보존 변경은 최초 결과 유지, 범위 격리, 늦은 충돌과 페이지 경계를 확인한다.
- 이벤트 계약 변경은 JSON Schema·예시 검증과 `./gradlew contractsZip`을 확인한다. 기존 v2
  통합 시나리오가 계약 예시를 직접 수신하게 하고 별도 고정 요청을 복제하지 않는다.
- 생산자 연동 변경은 실제 serializer와 원본 변경 → outbox → BRIEF 수신을 확인한다.
  계약 팩 검증만으로 전체 전달 성공을 보고하지 않는다.
