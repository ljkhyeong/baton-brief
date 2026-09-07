---
name: baton-brief-edition-flows
description: BATON BRIEF의 불변 에디션 생성·선정·시간 구간·스냅샷 필드와 최신·이력·비교·조건부 조회를 변경할 때 사용한다.
---

# BATON BRIEF 불변 에디션

## 관련 계약

변경 대상의 문서만 읽는다.

- 생성·멱등성: [MVP 계약](../../../docs/PRD/0002_mvp-contract/spec.md),
  [BATON 주도 생성](../../../docs/PRD/0024_baton-driven-edition-generation/spec.md)
- 선정·저장 필드: [주간 분류](../../../docs/PRD/0029_edition-carry-over/spec.md),
  [리비전 근거](../../../docs/PRD/0010_edition-revision-evidence/spec.md)
- 조회: [이력](../../../docs/PRD/0003_edition-history/spec.md),
  [주간 최신](../../../docs/PRD/0009_weekly-latest-edition/spec.md),
  [비교](../../../docs/PRD/0005_edition-comparison/spec.md),
  [ETag](../../../docs/PRD/0012_edition-etag/spec.md)

## 유지할 규칙

- 생성 대상·시간대·시점은 BATON이 정하고 기존 BRIEF 명령을 호출한다. BRIEF에 스케줄러나
  대상 registry를 추가하지 않는다.
- 주간은 IANA 시간대의 월요일 시작 `[windowStart, windowEnd)`다. DST 경계를 보존하고
  생성 시각과 지문 입력은 PostgreSQL 마이크로초 정밀도를 맞춘다.
- 생성 시 현재 투영과 로컬 `sourceCursor`를 일관되게 고정하고 에디션을 원자적으로 저장한다.
  같은 범위의 직전 상태만 재사용한다. `A → B → A`는 새 세대이며 커서 이동만으로 생성하지 않는다.
- 선정 규칙 `2`는 `ACTIVE`이고 `observedAt < windowEnd`인 항목을 `CURRENT_WEEK`·`CARRY_OVER`로
  구분한다. 현재 투영·항목 규칙은 `1`, V9 이전 `section`은 `null`을 유지한다.
- 새 항목의 `aggregateRevision`·`revisionGap`은 함께 고정하고 V3 이전 두 값은 `null`로 둔다.
  `0`·`false`로 채우거나 현재 투영에서 추정하지 않는다. 분류와 근거는 응답·지문·비교에 포함한다.
- 생성한 항목·지문은 투영 변경이나 재구축으로 다시 계산하지 않는다. 전역 최신과 정확한
  작업공간·시즌·주차·시간대의 주간 최신은 각각 저장된 최대 세대를 선택한다.
- 비교는 `(reasonCode, sourceReference)`를 키로 저장된 두 에디션을 읽는다. 분류·리비전 근거만
  달라도 `changed`이며 `removed`를 현재 `RESOLVED`로 해석하지 않는다.
- 전체 에디션 ETag는 선택된 불변 에디션을 나타낸다. 최신 선택이 바뀌면 검증자도 바뀌며
  조건부 요청은 Spring MVC에 맡긴다. 표현 변경 시 ETag 버전도 확인한다.

## 변경에 맞는 검증

- 생성·선정 변경은 고정 시각의 주간·DST 경계, 결정적 정렬, 즉시 재시도와 `A → B → A`,
  동시 생성과 생성 후 불변성을 확인한다. 생성 잠금을 바꾸면 재구축과의 직렬화도 검증한다.
- 스냅샷 필드 변경은 이전 데이터의 `null`·지문 보존과 신규 생성·비교를 PostgreSQL에서 확인한다.
- 조회 변경은 해당 범위·정렬·페이지 경계를 확인한다. 주간 최신은 전역 최신과 다른 데이터로,
  비교는 추가·제거·변경과 기준·대상 순서로, ETag는 같은 선택 `304`·새 선택 `200`으로 확인한다.
