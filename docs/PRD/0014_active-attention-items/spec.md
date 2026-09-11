# PRD-0014: 활성 점검 항목 키셋 조회

- 상태: 채택됨
- 결정일: 2026-08-22
- 범위: 작업공간·시즌별 현재 `ACTIVE` 점검 항목의 복합 식별자 키셋 조회

> PRD-0015는 이 경로의 기본 `ACTIVE` 의미를 유지하면서 선택적인 `status=RESOLVED` 조회를
> 추가한다. 키셋과 응답 의미는 이 문서를 그대로 따른다.

> [PRD-0027](../0027_attention-item-summary/spec.md)은 별도 경로로 활성 항목 개수를 요약한다.
> 이 목록 응답에는 전체 개수를 추가하지 않는다.

> [PRD-0028](../0028_attention-item-filters/spec.md)은 기존 정렬·커서를 유지하면서 선택적인
> 심각도·리비전 공백 필터를 추가한다. 이 문서의 제외 범위 중 해당 필터만 후속 계약으로 확장한다.

## 목적

PRD-0013은 복합 식별자를 아는 호출자가 점검 항목 한 건을 확인하게 한다. BATON UI의
요청을 중계하는 BATON 백엔드가 현재 활성 항목을 발견하려면 모든 이벤트 종류와 원본 참조를
미리 알고 있어야 한다.

작업공간·시즌별 현재 `ACTIVE` 항목을 커서 기반 페이지로 조회한다.
이 API는 요청 시점의 점검 항목을 반환하며, 브리프·브리프 미리보기·과거 상태 이력은 제공하지 않는다.

## HTTP API

다음 인증 없는 내부 로컬 MVP 경로를 추가한다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items` | 활성 점검 항목 키셋 조회 |

경로의 `workspaceId`와 `seasonId`는 UUID다. 해당 범위에 활성 항목이 없어도 `404`가 아니라
`200 OK`의 빈 페이지를 반환한다. BRIEF는 작업공간이나 시즌의 원본 존재 여부와 호출
권한을 자체 판정하지 않는다.

### 질의 매개변수

| 이름 | 필수 | 형식과 제약 | 의미 |
|---|---|---|---|
| `afterEventType` | 아니요 | PRD-0002의 지원 이벤트 종류 | 이 이벤트 종류 뒤에서 조회 재개 |
| `afterSourceReference` | 아니요 | PRD-0002의 문자·공백 규칙, Unicode code point 기준 최대 128자 | 같은 커서의 원본 참조 |
| `limit` | 아니요 | `1..100`, 기본값 `20` | 한 응답에 포함할 최대 항목 수 |

두 `after` 필드는 함께 제공하거나 함께 생략한다. 한 필드만 전달하거나 참조가 비어 있는
경우, 참조가 PRD-0002의 문자·공백 규칙을 어기거나 Unicode code point 기준 128자를 넘는
경우, 잘못된 UUID·열거형과 `limit` 범위 오류는 `400 Bad Request`와 PRD-0004의 표준
`ProblemDetail`로 거부한다.

문자열은 수신과 같은 공용 입력 규칙으로 검증한다. 커서 조합 검증에서 다른 공백 기준을
적용하지 않으며, 수신한 NBSP 참조가 `nextCursor`에 포함되면 그대로 다음 페이지에 사용할 수
있다.

## 선정과 정렬

- 작업공간과 시즌이 모두 정확히 일치하고 현재 `status=ACTIVE`인 항목만 반환한다.
- `eventType` 오름차순, 그 안에서 `sourceReference` 오름차순으로 안정 정렬한다.
- 커서는 마지막으로 반환한 `(eventType, sourceReference)`다. 다음 페이지는 이 항목 다음부터 조회한다.
- 다음 페이지 존재 여부는 `limit`보다 한 건 더 읽어 판단하며 전체 개수 조회는 하지
  않는다.
- 기본 `status=ACTIVE`에서는 `RESOLVED` 항목을 제외한다. PRD-0015의
  `status=RESOLVED`를 사용하면 같은 목록 계약으로 현재 해소 상태를 탐색할 수 있다.

이 정렬은 현재 항목을 빠짐없이 탐색하기 위한 식별자 순서다. 심각도 우선의 브리프
선정 순서를 복제하거나 현재 목록을 브리프 미리보기로 해석하지 않는다.

## 성공 응답

성공 응답은 다음 필드를 가진다.

- `items`: 기존 `AttentionItemResponse` 형식의 현재 활성 항목 배열
- `nextCursor`: 다음 페이지가 있으면 마지막 반환 항목의 `eventType`과
  `sourceReference`, 없으면 `null`

호출자는 `nextCursor.eventType`과 `nextCursor.sourceReference`를 다음 요청의
`afterEventType`과 `afterSourceReference`로 전달한다.

각 항목은 `reasonCode`, `severity`, `sourceReference`, `status`, `observedAt`,
`aggregateRevision`, `ruleVersion`, `revisionGap`만 반환한다. 작업공간·시즌은 경로 범위이며
원본 payload, fingerprint, 수신 시각, 충돌 기록과 SQL·예외 상세는 노출하지 않는다.

## 변경 중 페이지 의미

현재 투영은 이벤트 수신과 재구축으로 바뀔 수 있으며 여러 HTTP 요청을 하나의 데이터베이스
스냅샷으로 묶지 않는다.

- 페이지 사이에 새 항목이 활성화되거나 기존 항목이 해소되면 진행 중인 탐색에서 새
  항목이 빠지거나 이미 본 위치가 사라질 수 있다.
- 기존 항목의 리비전·표시 값이 바뀌면 그 항목을 읽는 요청 시점의 현재 값을 반환한다.
- `nextCursor`는 정렬 위치일 뿐 스냅샷 토큰이나 변경 워터마크가 아니다.
- 최신 현재 상태를 다시 확인할 때는 커서를 버리고 첫 페이지부터 조회한다.

## 영속성과 호환성

- Flyway V4의 `(workspace_id, season_id, event_type, source_reference)` 복합 기본 키와
  PostgreSQL 행 값 비교를 사용해 배타 키셋을 조회한다.
- 기존 `attention_item`과 `AttentionItemResponse`를 재사용하며 새 테이블, 열, 인덱스와
  Flyway 마이그레이션을 추가하지 않는다.
- 조회는 현재 투영, 수신 기록과 브리프를 생성·수정·삭제하지 않는다.
- 현재 목록은 변경 가능하므로 PRD-0012의 브리프 `ETag` 계약을 적용하지 않는다.

## 수용 기준

- 정확한 작업공간·시즌의 `ACTIVE` 항목만 복합 식별자 순서로 반환한다.
- `nextCursor`로 조회하면 커서 항목 다음부터 최대 `limit`건을 중복 없이 반환한다.
- `RESOLVED` 항목은 후속 `ACTIVE` 첫 페이지에서 제외되고 PRD-0015의 `RESOLVED` 목록과
  PRD-0013 단건 조회에는 남는다.
- 다른 작업공간·시즌의 항목이 섞이지 않으며 빈 범위는 `200 OK`의 빈 배열이다.
- 커서 필드 하나만 제공하거나 참조가 비어 있거나 `limit`이 잘못되면 `400 Bad Request`다.
- 재구축 뒤 같은 현재 투영을 페이지로 조회할 수 있다.

## 제외 범위

- 심각도·관측 시각·이벤트 종류별 필터와 정렬 선택, 자유 검색과 전체 개수
- offset 페이지네이션과 여러 요청을 묶는 스냅샷 토큰
- 과거 투영 상태와 변경 이력
- 브리프 미리보기와 현재 투영에서 브리프를 즉석 조합하는 조회
- 새 인덱스·마이그레이션, 캐시 검증자와 변경 알림
- 인증·인가, 외부 공개 API와 운영 배포
