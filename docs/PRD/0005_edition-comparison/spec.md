# PRD-0005: 불변 에디션 비교

- 상태: 채택됨
- 결정일: 2026-08-18
- 범위: 저장된 두 불변 에디션의 결정적 항목 차이 조회

## 목적

PRD-0002와 PRD-0003은 불변 에디션의 단건·이력 조회를 제공하지만, BATON UI가 두 시점
사이에서 어떤 항목이 추가·제거·변경되었는지는 직접 계산해야 한다.

저장된 두 에디션 스냅샷을 안정적인 항목 키로 비교하는 읽기 전용 API를 추가한다. 현재
투영이나 원본 상태를 다시 조회하지 않으며, 에디션과 투영의 불변성을 바꾸지 않는다.

## HTTP API

다음 인증 없는 내부 로컬 MVP 경로를 추가한다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/api/v1/editions/{targetEditionId}/changes?fromEditionId={baseEditionId}` | 기준 에디션에서 대상 에디션으로의 항목 차이 조회 |

- `targetEditionId`는 비교 결과의 대상인 `to` 에디션 식별자다.
- 필수 질의 매개변수 `fromEditionId`는 비교 기준인 `from` 에디션 식별자다.
- 두 식별자는 UUID다. 잘못된 UUID나 누락된 `fromEditionId`는 `400 Bad Request`다.

## 비교 범위

- 두 에디션은 같은 `workspaceId`와 `seasonId`에 속해야 한다. 둘 중 하나라도 다르면
  `400 Bad Request`와 PRD-0004의 `ProblemDetail`을 반환한다.
- 두 식별자 중 어느 하나라도 저장되어 있지 않으면 `404 Not Found`와 PRD-0004의
  `ProblemDetail`을 반환한다.
- 같은 작업공간·시즌이면 서로 다른 `weekStart`, `zoneId`, `ruleVersion`의 에디션도
  비교할 수 있다.
- `generation`의 선후 관계를 검증하지 않는다. 과거에서 미래 방향과 미래에서 과거
  방향 비교를 모두 허용하며, URL과 질의 매개변수가 각각 `to`와 `from` 의미를 정한다.

이 범위 검증은 BATON 멤버십이나 조회 권한 판정이 아니다. 운영 인증·인가는 별도 계약이
필요하다.

## 성공 응답

성공 응답은 `200 OK`이며 다음 필드를 가진다.

| 필드 | 의미 |
|---|---|
| `from` | 기준 에디션 요약 |
| `to` | 대상 에디션 요약 |
| `added` | 대상에만 존재하는 고정 항목 배열 |
| `removed` | 기준에만 존재하는 고정 항목 배열 |
| `changed` | 같은 비교 키에서 표시 필드가 달라진 `before`·`after` 쌍 배열 |

`from`과 `to`는 PRD-0003의 `EditionSummary` 필드를 그대로 재사용한다. 즉
`editionId`, `generation`, `weekStart`, `zoneId`, `generatedAt`, `sourceCursor`,
`ruleVersion`, `itemCount`를 포함한다.

`added`, `removed`와 `changed.before`·`changed.after`는 PRD-0002 단건 에디션 항목의 고정
표시 필드를 그대로 재사용한다. 즉 `reasonCode`, `severity`, `sourceReference`, `status`,
`observedAt`, `ruleVersion`, `null`을 허용하는 `aggregateRevision`과 `revisionGap`을 포함한다.
Flyway V3 이전 항목은 두 리비전 근거가 모두 `null`이며, 새 항목은 둘 다 저장된 값이다.
변경되지 않은 항목은 응답에 반복하지 않는다.

## 비교 규칙

항목 비교 키는 다음 두 필드의 조합이다.

```text
(reasonCode, sourceReference)
```

- 기준에는 없고 대상에만 키가 있으면 `added`다.
- 기준에만 있고 대상에는 키가 없으면 `removed`다.
- 양쪽에 같은 키가 있고 고정 표시 필드가 하나라도 다르면 `changed`다. 표시 필드는 같고
  `aggregateRevision` 또는 `revisionGap`만 달라도 `changed`로 분류한다. `before`에는 기준
  항목을, `after`에는 대상 항목을 넣는다.
- 양쪽에 같은 키와 같은 고정 표시 필드가 있으면 변경 없음이며 응답 배열에 넣지 않는다.

`added`와 `changed`는 대상 에디션에 저장된 항목 순서를 유지한다. `removed`는 기준
에디션에 저장된 항목 순서를 유지한다. 따라서 별도 정렬 선택지 없이도 PRD-0002의
`severity` 내림차순, `reasonCode`, `sourceReference` 오름차순 결과가 재현된다.

`removed`는 대상 스냅샷에 해당 비교 키가 없다는 뜻일 뿐이다. 현재 투영이나 원본의
`RESOLVED` 상태를 판정하거나 추론하지 않는다.

## 불변성과 호환성

- 비교는 저장된 두 불변 스냅샷만 읽으며 현재 `AttentionItem` 투영을 조회하거나 다시
  조합하지 않는다.
- 비교 요청은 에디션, 항목, 수신 기록, 투영과 원본 커서를 생성·수정·삭제하지 않는다.
- 기존 생성·최신·단건·이력 응답을 바꾸지 않는 추가 계약이다.
- 별도 비교 테이블, 변경 이력 이벤트와 상태 전이 기록을 만들지 않는다.
- 서로 다른 규칙 버전의 의미를 번역하거나 동일시하지 않고 저장된 표시 필드 차이만
  반환한다.
- 이전 항목의 리비전 근거가 `null`이어도 현재 투영이나 수신 기록에서 값을 추정하지
  않는다. `null`과 새 항목의 저장된 근거는 서로 다른 고정 값이므로 같은 비교 키에서는
  `changed`가 될 수 있다.

## 비목표

- `removed`를 도메인 해소, 삭제 또는 정정으로 판정
- 현재 투영과 과거 에디션 비교
- 세 개 이상 에디션의 추세·집계 비교
- 비교 결과 페이지네이션, 검색과 정렬 선택지
- BATON 멤버십, 인증·인가, 외부 공개와 운영 배포

## 수용 기준

- 같은 작업공간·시즌의 두 에디션을 비교해 안정적인 키 기준의 `added`, `removed`,
  `changed.before`·`changed.after`를 반환한다.
- `added`와 `changed`는 대상 순서, `removed`는 기준 순서를 유지한다.
- 같은 비교 키에서 `aggregateRevision` 또는 `revisionGap`만 달라도 `changed`에
  `before`·`after`를 반환한다.
- 주간, 시간대, 규칙 버전과 세대 방향이 다른 같은 범위의 에디션을 비교할 수 있다.
- 어느 식별자든 없으면 `404`, 작업공간이나 시즌이 다르면 `400`이며 모두
  `application/problem+json`으로 응답한다.
- 비교 전후에 두 에디션과 현재 투영의 저장 상태가 바뀌지 않는다.
