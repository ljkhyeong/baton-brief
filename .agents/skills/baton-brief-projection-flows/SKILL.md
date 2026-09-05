---
name: baton-brief-projection-flows
description: BATON BRIEF의 현재 관심 항목·리비전 판정·상태 전이·주간 해소 조회와 전체 재구축을 변경할 때 사용한다.
---

# BATON BRIEF 현재 관심 항목과 재구축

## 관련 계약

변경 대상의 문서만 읽는다.

- 투영 규칙: [MVP 계약](../../../docs/PRD/0002_mvp-contract/spec.md),
  [이벤트 v2](../../../docs/PRD/0019_baton-continuity-event-v2/spec.md)
- 단건·조건부 조회: [현재 단건](../../../docs/PRD/0013_current-attention-item/spec.md),
  [ETag](../../../docs/PRD/0017_current-attention-item-etag/spec.md)
- 목록·요약: [상태 필터](../../../docs/PRD/0015_attention-item-status-filter/spec.md),
  [활성 요약](../../../docs/PRD/0027_attention-item-summary/spec.md),
  [심각도·공백 필터](../../../docs/PRD/0028_attention-item-filters/spec.md)
- 전이·해소 근거: [상태 전이](../../../docs/PRD/0016_attention-item-transitions/spec.md),
  [주간 해소](../../../docs/PRD/0030_weekly-resolution-summary/spec.md)
- [보존·재구축](../../../docs/PRD/0008_retention-rebuild-boundary/spec.md)

## 유지할 규칙

- 현재 항목의 키는 `(workspaceId, seasonId, eventType, sourceReference)`다. 최신 판정은
  원본 `aggregateRevision`을 사용하며 도착 순서나 로컬 `sourceCursor`로 대신하지 않는다.
- 실시간 수신과 재구축은 같은 규칙을 사용한다. v1의 타입별 심각도를 유지하고 v2는
  `CRITICAL` → `HIGH`, `WARNING` → `MEDIUM`으로 표시한다.
- 현재 단건은 `ACTIVE`·`RESOLVED`를 모두 반환한다. 목록은 `ACTIVE`가 기본이며
  `(eventType, sourceReference)` 오름차순 배타 키셋을 쓴다. 커서를 스냅샷으로 해석하지 않는다.
- 전이 이력은 `APPLIED`·`APPLIED_WITH_GAP` 수신 기록을 원본 리비전 역순으로 읽는다.
  전이별 `detectedRevisionGap`과 누적 `revisionGap`을 구분하고 v1 `sourceSeverity=null`을 추정하지 않는다.
- 주간 해소는 연속된 활성·해소 증거와 현재 해소 상태를 함께 확인한다. 재활성화했거나
  해소 시점의 기록이 누락된 항목을 해소 건수로 추정하지 않는다.
- 재구축은 `UNSUPPORTED`를 제외한 기록을 `ingestion_sequence` 순서로 재생하고 현재 투영만
  한 트랜잭션에서 교체한다. 실패하면 이전 투영으로 롤백하며 불변 에디션은 바꾸지 않는다.
- 재구축·에디션 생성은 같은 PostgreSQL advisory lock의 배타 모드, 지원 이벤트 수신은 공유
  모드를 사용한다. 잠금이나 트랜잭션 경계를 나눠 부분 결과를 노출하지 않는다.
- 단건 ETag는 표현 버전·규칙 버전·마지막 적용 리비전에 결합한다. 조건부 요청 처리는
  Spring MVC에 맡기고 직접 헤더 파서·본문 해시·캐시 저장소를 만들지 않는다.

## 변경에 맞는 검증

- 판정 규칙 변경은 중복·역순 전달·오래된 리비전·공백과 재구축 결과를 기존 통합 시나리오에서 확인한다.
- 조회 변경은 해당 범위 격리·정렬·페이지 경계·상태 갱신을 확인한다. 주간 해소는 고정 시각에서
  주간 경계·재활성화·해소 증거 공백을, ETag는 동일 표현 `304`와 후속 리비전 `200`을 확인한다.
- 재구축 실패·잠금 변경은 중간 강제 실패의 롤백과 지원 이벤트 수신의 직렬화를 PostgreSQL에서
  확인한다. 같은 잠금 코드만으로 에디션 생성·다른 재구축의 동시성까지 검증했다고 보고하지 않는다.
