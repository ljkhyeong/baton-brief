# PRD-0011: 이상 이벤트 수신 증거 이력 조회

- 상태: 채택됨
- 결정일: 2026-08-20
- 구현 상태: 로컬 구현 및 검증 완료
- 범위: 작업공간·시즌별 과거 이상 수신 증거의 읽기 전용 키셋 조회

## 목적

PRD-0007의 단건 조회는 `eventId`를 이미 아는 호출자가 최초 수신 결과를 확인하게 한다.
하지만 리비전 공백, 오래된 리비전, 미지원 이벤트나 식별자 충돌이 과거에 발생했는지
발견하려면 모든 이벤트 식별자를 미리 알고 있어야 한다.

BRIEF가 PRD-0008에 따라 보존하는 최초 수신 기록과 이벤트별 최초 충돌 한 건을
작업공간·시즌 범위에서 탐색할 수 있게 한다. 이 조회는 저장된 이상 증거를 드러낼 뿐이며
원본 상태를 재판정하거나 수신 기록을 재처리·해제·정정하지 않는다.

## HTTP API

다음 인증 없는 내부 로컬 경로를 추가한다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/event-receipts/anomalies` | 과거 이상 수신 증거 조회 |

- `workspaceId`와 `seasonId`는 UUID다. 잘못된 UUID는 `400 Bad Request`와 PRD-0004의
  `ProblemDetail`로 응답한다.
- 해당 범위에 이상 수신 증거가 없어도 `404`가 아니라 빈 페이지의 `200 OK`로 응답한다.
- 이 로컬 계약은 운영 외부 공개나 호출 권한 판정이 완료되었다는 뜻이 아니다.

### 질의 매개변수

| 이름 | 필수 | 제약 | 의미 |
|---|---|---|---|
| `beforeIngestionSequence` | 아니요 | 양수 | 이 수신 순서보다 작은 증거만 조회하는 배타적 키셋 |
| `limit` | 아니요 | `1..100`, 기본값 `20` | 한 응답에 포함할 최대 증거 수 |

숫자로 변환할 수 없는 값, 양수가 아닌 `beforeIngestionSequence`와 `1..100` 밖의 `limit`은
`400 Bad Request`와 PRD-0004의 `ProblemDetail`로 거부한다.

## 이상 수신 증거 선정

작업공간·시즌이 모두 일치하는 최초 수신 기록 중 다음 조건을 하나 이상 만족하면
반환한다.

- 저장된 `processingOutcome`이 `APPLIED_WITH_GAP`, `STALE`, `UNSUPPORTED` 중 하나다.
- 해당 `eventId`에 최초 충돌 증거가 있어 `conflictDetectedAt`이 존재한다.

따라서 최초 결과가 `APPLIED`여도 나중에 다른 지문의 충돌을 처음 탐지했다면 포함한다.
충돌이 없는 `APPLIED`는 포함하지 않는다.

완전히 같은 이벤트의 재전달 결과인 `DUPLICATE`와 다른 지문의 요청 결과인 `CONFLICT`는
최초 수신 기록의 `processingOutcome`으로 저장되지 않는다. 두 요청 결과를 이상 유형이나
필터 값으로 새로 만들지 않으며, 충돌은 기존의 선택적인 `conflictDetectedAt`으로만
표현한다.

## 성공 응답

성공 응답은 `200 OK`이며 다음 필드를 가진다.

- `receipts`: `ingestionSequence` 내림차순으로 정렬된 이상 수신 증거 배열
- `nextBeforeIngestionSequence`: 다음 페이지가 있을 때 마지막으로 반환한
  `ingestionSequence`, 없으면 `null`

각 배열 항목은 PRD-0007의 안전한 수신 증거 필드를 그대로 반환한다.

| 필드 | 의미 |
|---|---|
| `eventId` | 최초 수신 기록의 이벤트 식별자 |
| `ingestionSequence` | BRIEF가 부여한 불변 로컬 수신 순서 |
| `eventType` | 최초 저장한 이벤트 종류 |
| `eventVersion` | 최초 저장한 이벤트 버전 |
| `workspaceId` | 불투명 작업공간 참조 |
| `seasonId` | 불투명 시즌 참조 |
| `sourceReference` | 길이가 제한된 불투명 원본 참조 |
| `aggregateRevision` | 최초 저장한 이벤트의 집계 리비전 |
| `occurredAt` | 원본이 사실을 관측한 정규화된 시각 |
| `state` | 최초 저장한 `ACTIVE` 또는 `RESOLVED` 상태 |
| `processingOutcome` | 최초 기록에 저장된 처리 결과 |
| `receivedAt` | 최초 수신 기록을 저장한 시각 |
| `conflictDetectedAt` | 다른 지문의 충돌을 처음 탐지한 시각, 없으면 `null` |

fingerprint, 원문 payload, 정규화 전 입력, SQL·예외 상세, 자격 증명, 원문 URL과
불필요한 PII는 반환하지 않는다.

## 페이지와 시점 의미

- 첫 요청에서 `beforeIngestionSequence`를 생략하면 요청 시점에 자격을 갖춘 가장 큰
  `ingestionSequence`부터 조회한다.
- 다음 페이지는 직전 응답의 `nextBeforeIngestionSequence`를 그대로 전달한다.
- 커서는 배타적이며 다음 페이지 존재 여부는 `limit`보다 한 건 더 읽어 판단한다. 전체
  개수 조회는 수행하지 않는다.
- 빈 범위는 `receipts` 빈 배열과 `nextBeforeIngestionSequence: null`을 반환한다.

이 페이지는 여러 요청을 하나의 데이터베이스 스냅샷으로 묶지 않는다. 최초 수신 기록의
`ingestionSequence`는 바뀌지 않지만 충돌 증거는 최초 수신 뒤에 추가될 수 있다. 그 결과
이전에 제외됐던 `APPLIED` 기록이 후속 요청에서 조회 대상으로 바뀔 수 있고, 새 이상 수신
기록도 더 큰 순서로 추가될 수 있다.

`nextBeforeIngestionSequence`는 정렬 위치만 나타내며 스냅샷 토큰이나 변경 워터마크가
아니다. 진행 중인 과거 방향 탐색에서 커서보다 큰 순서의 기록이 새로 대상이 되면 그
탐색에는 나타나지 않을 수 있다. 호출자는 최신 이상 증거를 다시 확인할 때 커서를 버리고
첫 페이지부터 새로 조회한다.

## 읽기 전용성과 호환성

- 기존 `POST /api/v1/events`, `GET /api/v1/events/{eventId}/receipt`, 투영과 에디션 계약을
  바꾸지 않는 추가 조회다.
- 기존 `source_event_receipt`와 `source_event_conflict`만 읽으며 새 테이블, 열, 인덱스와
  Flyway 마이그레이션을 추가하지 않는다.
- 조회는 수신 기록, 충돌 증거, 현재 투영과 불변 에디션을 생성·수정·삭제하지 않는다.
- PRD-0008의 `retain-all` 경계에서 보존된 최초 수신 결과와 최초 충돌 한 건만 사용한다.
  후속 삭제 계약은 단건 조회와 이력 페이지 모두의 만료 의미를 먼저 정의해야 한다.

## 비목표

- 정상 `APPLIED`를 포함한 전체 수신 기록 목록
- 결과·이벤트 종류·날짜·원본 참조 검색이나 필터, 정렬 선택과 전체 개수
- offset 페이지네이션이나 여러 요청을 묶는 스냅샷 토큰
- 중복·충돌 횟수와 모든 전달 시도 이력
- 재처리, 격리 해제, 정정, 삭제와 운영 조치 API
- 새 스키마·마이그레이션·인덱스, 숫자 보존 기간과 용량 상한
- BATON 멤버십, 운영 인증·인가, 외부 공개와 배포

## 수용 기준

- 작업공간·시즌 범위에서 `APPLIED_WITH_GAP`, `STALE`, `UNSUPPORTED`와 최초 충돌이 있는
  기록만 반환한다.
- 충돌이 있는 `APPLIED`는 포함하고 충돌이 없는 `APPLIED`는 제외한다.
- 동일 재전달은 별도 기록이나 `DUPLICATE` 처리 결과 항목을 만들지 않는다.
- 결과를 `ingestionSequence` 내림차순으로 반환하고 배타적 커서로 다음 페이지를 조회한다.
- `limit`을 넘지 않으며 빈 범위는 `200 OK`와 빈 배열을 반환한다.
- 충돌이 나중에 발견된 기록은 첫 페이지부터 다시 시작한 탐색에서 현재 자격에 따라
  나타나며, 커서를 스냅샷 토큰으로 해석하지 않는다.
- 응답은 PRD-0007의 안전한 필드만 포함하고 fingerprint와 원문 payload를 노출하지 않는다.
- 조회 전후에 수신 기록, 충돌 증거, 투영과 에디션 저장 상태가 바뀌지 않는다.

## 현재 구현과 검증

기존 `SourceEventReceipt` 항목 표현과 `source_event_receipt`·`source_event_conflict`를
재사용해 로컬 조회를 구현했다. 별도 수신 증거 항목 DTO, Flyway 마이그레이션과 인덱스를
추가하지 않았다.

기존 `ingestion distinguishes duplicate conflict unsupported stale and gap` PostgreSQL 통합
시나리오를 확장해 다음을 확인했다. 새 테스트 메서드는 추가하지 않았다.

- `APPLIED` 최초 수신과 같은 이벤트의 `DUPLICATE` 직후에는 목록이 비어 있다.
- 후속 충돌 뒤에는 최초 `processingOutcome`이 `APPLIED`인 기록도
  `conflictDetectedAt`과 함께 포함된다.
- 첫 페이지가 수신 순서 `4`, `3`의 `UNSUPPORTED`, `APPLIED_WITH_GAP`을 반환하고,
  배타 커서 `beforeIngestionSequence=3`인 다음 페이지가 순서 `2`, `1`의 `STALE`,
  충돌이 있는 `APPLIED`를 반환한다.
- 다른 시즌과 다른 작업공간 범위는 `200 OK`의 빈 목록을 반환한다.

`./gradlew --no-daemon clean test :bootstrap:bootJar`가 성공해 전체 테스트와 실행 JAR
생성을 확인했다.

첫 페이지와 다음 페이지 요청 사이에 새 충돌이 추가되는 별도 동시·중간 페이지 시나리오는
직접 실행하지 않았다. 요청 간 스냅샷을 보장하지 않고 최신 상태를 첫 페이지부터 다시
조회하는 의미는 이 계약의 명시적 한계다.
