# PRD-0002: MVP 이벤트·투영·에디션 계약

- 상태: 채택됨
- 결정일: 2026-08-13
- 구현 상태: 로컬 MVP 구현 및 검증 완료
- 범위: 로컬 MVP의 내부 HTTP 계약과 결정적 투영·에디션 규칙

## 목적

첫 MVP가 구현할 최소 경계를 고정한다. BRIEF는 BATON의 운영 사실을 내부 HTTP로 받아
멱등하게 기록하고, 설명 가능한 관심 항목으로 투영한 뒤 작업공간·시즌 단위의 주간
에디션을 생성·조회한다.

이 계약의 내부 HTTP 엔드포인트, PostgreSQL 영속성과 로컬 MVP 동작은 구현했다. 이는
BATON 생산자 연동, 운영 인증·인가, 외부 종단 간 동작이나 배포가 완료되었다는
뜻은 아니다.

PostgreSQL 18.4 통합 시나리오 4개, 저장소 전체 정리 후 테스트와 실행 JAR 생성, Compose
PostgreSQL에 대한 Java 21 비웹 실행 환경/Flyway 기동 점검으로 로컬 MVP를 검증했다. MockMvc 기반
HTTP 계약 검증과 비웹 실행 점검을 실제 배포 환경의 네트워크 종단 간 검증으로
확대 해석하지 않는다.

## MVP 경계

- 전송 방식은 내부 HTTP만 사용한다.
- 로컬 MVP에서는 인증·인가를 구현하지 않는다. 외부 노출이나 운영 사용을 허용하는
  결정이 아니며, 운영 경계에서는 별도 인증·인가 계약이 선행되어야 한다.
- 메시지 브로커, 스케줄러, 원본 생산자 변경과 운영 배포는 포함하지 않는다.
- BATON, WATCH, RELAY 또는 GO의 데이터베이스와 엔티티를 공유하지 않는다.
- BRIEF는 원본 사실을 재판정하지 않고 전달받은 사실만 투영한다.

## HTTP API

모든 경로는 `/api/v1` 아래에 둔다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `POST` | `/api/v1/events` | 이벤트 v1 수신과 멱등 투영 |
| `POST` | `/api/v1/projections/rebuild` | 보존한 수신 기록으로 현재 투영 전체 재구축 |
| `POST` | `/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions` | 주간 에디션 생성 |
| `GET` | `/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/latest` | 해당 범위의 최신 완료 에디션 조회 |
| `GET` | `/api/v1/editions/{id}` | 불변 에디션 단건 조회 |

로컬 MVP의 엔드포인트는 인증 없는 내부 API다. 호출자가 전달한 작업공간·시즌에
대한 권한을 BRIEF가 자체 판정하지 않는다.

## 이벤트 v1

### 요청 봉투

`POST /api/v1/events`는 다음 필드를 받는다.

| 필드 | 형식 | 제약 |
|---|---|---|
| `eventId` | UUID | 이벤트 식별자 |
| `eventType` | 열거형 | 아래 세 종류 중 하나 |
| `eventVersion` | 정수 | 양수이며 소비자가 지원하는 값은 정확히 `1` |
| `workspaceId` | UUID | 불투명 원본 참조 범위 |
| `seasonId` | UUID | 불투명 원본 참조 범위 |
| `sourceReference` | 문자열 | 비어 있지 않은 불투명 식별자, 최대 128자 |
| `aggregateRevision` | 정수 | 양수 |
| `occurredAt` | JSON 문자열의 ISO-8601 시점 | 원본이 사실을 관측한 시각 |
| `state` | 열거형 | `ACTIVE` 또는 `RESOLVED` |

지원하는 `eventType`은 다음과 같다.

- `HANDOFF_BLOCKED`
- `ROUTINE_MISSED`
- `DECISION_FOLLOW_UP_OVERDUE`

### 수신 결과

BRIEF는 정규 이벤트 페이로드의 지문을 이벤트 식별자와 함께 보존하고 다음 결과를
구분한다.

PostgreSQL `TIMESTAMPTZ`와 저장·응답 스냅샷의 정밀도를 맞추기 위해 모든 이벤트와 생성
시각은 애플리케이션 경계에서 마이크로초 단위로 절삭한 값을 정규 의미로 사용한다.
따라서 마이크로초 아래의 차이만 있는 `occurredAt`은 의미상 같은 시점으로 취급한다.

- `APPLIED`: 지원하는 새 이벤트를 수신 기록에 남기고 리비전 규칙에 따라 투영에
  반영했다.
- `APPLIED_WITH_GAP`: 새 이벤트를 적용했지만 직전 적용 리비전과 사이에 간격이 있다.
- `DUPLICATE`: 지원하는 이벤트에서 같은 `eventId`와 동일한 의미 지문이
  재전송되었다. 성공으로 취급하며 투영 효과를 반복하지 않는다.
- `CONFLICT`: 같은 `eventId`에 다른 지문이 들어왔다. 먼저 수락한 수신 기록과
  투영을 덮어쓰지 않고, 원문 페이로드나 비밀 값 없이 크기가 제한되고 정제된 격리
  증거를 남긴다.
- `UNSUPPORTED`: 양수인 `eventVersion`이 `1`이 아니다. 수신 기록을 `UNSUPPORTED` 결과로
  보존하고 투영을 부분 적용하지 않는다.
- `STALE`: 이미 적용한 현재 리비전 이하의 이벤트다. 수신 기록에는 결과를 남기지만
  투영을 변경하지 않는다.

문법적으로 잘못된 UUID·시점·열거형, 빈 `sourceReference`, 128자를 넘는
`sourceReference`, 양수가 아닌 이벤트 버전 또는 리비전은 유효한 이벤트 수신 기록이나
투영 효과를 만들지 않는다.

HTTP 상태와 응답 본문은 다음과 같다.

- `APPLIED`, `APPLIED_WITH_GAP`: `202 Accepted`
- `DUPLICATE`, `STALE`: `200 OK`
- `CONFLICT`: `409 Conflict`
- `UNSUPPORTED`: `422 Unprocessable Content`
- 요청 검증 실패: `400 Bad Request` (`eventVersion <= 0` 포함)

본문은 `eventId`, `status`와 적용된 경우의 `item`을 반환한다. `item`은 `reasonCode`,
`severity`, `sourceReference`, `status`, `observedAt`, `aggregateRevision`, `ruleVersion`과
`revisionGap`을 포함한다.

지원하지 않는 이벤트의 완전히 같은 재생은 `DUPLICATE`로 바꾸지 않고 저장된 `UNSUPPORTED` 결과를
그대로 반환한다. 같은 `eventId`로 지문이 다른 이벤트가 오면 최초 수신 기록의 지원
여부와 관계없이 `CONFLICT`다. 격리 증거는 이벤트별 최초 충돌의 지문과
탐지 시각 한 건만 보존해 크기를 제한한다.

### 리비전과 간격

투영의 현재 리비전은 `(workspaceId, seasonId, eventType, sourceReference)` 범위로
비교한다.

- 현재 리비전 이하의 이벤트는 `STALE`로 기록하되 투영을 변경하지 않는다.
- 현재 리비전보다 1 큰 이벤트는 정상 적용한다.
- 리비전이 하나 이상 건너뛴 이벤트는 도착 순서를 원본 사실의 판단 기준으로 삼지 않는다. 이벤트
  자체는 `APPLIED_WITH_GAP`으로 적용하고 투영에 명시적인 리비전 간격 증거를
  남긴다.
- 재구축도 실시간 수신과 같은 리비전 및 간격 규칙을 사용한다.

## `AttentionItem` 투영 규칙 v1

규칙 버전은 `1`이며 이벤트 종류를 다음과 같이 결정적으로 매핑한다.

| 이벤트 종류 | `reasonCode` | `severity` |
|---|---|---|
| `HANDOFF_BLOCKED` | `HANDOFF_BLOCKED` | `HIGH` |
| `ROUTINE_MISSED` | `ROUTINE_MISSED` | `MEDIUM` |
| `DECISION_FOLLOW_UP_OVERDUE` | `DECISION_FOLLOW_UP_OVERDUE` | `MEDIUM` |

- `ACTIVE`는 해당 원본 참조의 관심 항목을 활성 상태로 투영한다.
- `RESOLVED`는 해당 관심 항목을 해소 상태로 투영한다.
- AI 생성 결과, 수신 기록 도착 시간 또는 BRIEF의 추론으로 이유·심각도·상태를 바꾸지
  않는다.
- 표시 가능한 항목은 작업공간·시즌, 이유 코드, 심각도, 불투명 원본 참조,
  원본의 `occurredAt`, 적용 리비전과 규칙 버전을 보존한다.

## 투영 재구축

`POST /api/v1/projections/rebuild`는 보존된 수락 수신 기록을 원본 리비전 규칙에 따라
재적용해 현재 투영을 다시 만든다.

- 완전히 같은 재생, 충돌과 지원하지 않는 수신 기록이 새로운 투영 효과를 만들지 않는다.
- 실시간 처리와 같은 규칙 v1을 사용한다.
- 간격과 오래된 이벤트의 증거를 잃지 않는다.
- 부분 재구축 결과를 현재 투영으로 노출하지 않는다.
- 재구축은 수락된 수신 기록과 기존 불변 에디션을 보존하고 현재 관심 항목
  투영만 원자적으로 다시 만든다.

첫 MVP는 재구축 시작 명령만 HTTP로 제공하며 스케줄러나 브로커 소비자를 추가하지 않는다.
성공 응답은 `200 OK`와 `receiptCount`, `itemCount`를 반환한다.

PRD-0008은 이 명령의 보존·동시성·실패 경계를 구체화한다. 모든 저장된 수신 결과와
이벤트별 최초 충돌 증거는 대체 계약 전까지 보존하되, 재구축은 `UNSUPPORTED`를 제외한
수신 기록만 `ingestion_sequence` 순서로 재생한다. 현재 투영 전체 교체는 수신과 에디션
생성과 공유하는 전역 잠금 경계 및 하나의 트랜잭션에서 수행하며 실패 시 이전 투영으로
롤백한다. 숫자 TTL, 잠금 제한 시간과 재구축 SLO는 아직 채택하지 않았다.

## 주간 `BriefEdition`

### 생성 요청

`POST /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions`는 다음 본문을 받는다.

| 필드 | 형식 | 제약 |
|---|---|---|
| `weekStart` | JSON 문자열의 ISO-8601 지역 날짜 | 반드시 월요일 |
| `zoneId` | JSON 문자열의 IANA 시간대 ID | JVM이 제공하는 이름 있는 시간대 ID |

숫자 타임스탬프, 배열형 지역 날짜와 `+09:00` 같은 고정 오프셋 시간대는 받지 않는다. 요청
표현을 문자열로 고정해 동일한 의미가 여러 JSON 형태로 들어오는 것을 막는다.

구간은 지정한 시간대에서 `weekStart` 00:00부터 다음 월요일 00:00까지의
`[windowStart, windowEnd)`로 계산한다. 두 경계를 각각 시점으로 변환하므로 일광 절약 시간
전환이 있는 주를 고정 168시간으로 가정하지 않는다.

### 생성 의미

- 생성 트랜잭션에서 해당 작업공간·시즌에 보존된 지원 수신 기록의 최대
  `ingestion_sequence`를 `sourceCursor`로 고정한다. 이 값은 BRIEF가 처리한 로컬 수신 기록
  경계이며 원본 시스템의 완전성 워터마크나 수신 기록 도착 시각이 아니다.
- 같은 트랜잭션의 현재 투영 중 `ACTIVE`이고 원본
  `occurredAt`(`observedAt`)이 `[windowStart, windowEnd)`에 속한 항목만 선택한다.
- 정렬·선택된 고정 항목 상태를 정규화해 `stateFingerprint`를 계산한다. 같은 작업공간,
  시즌, `weekStart`, `zoneId`와 규칙 버전의 가장 최근 에디션이 같은
  `stateFingerprint`를 가질 때만 반복 생성 요청으로 판단한다. 이 경우 하나의 논리적
  에디션만 유지하며 생성 시각이나 원본 커서만 달라졌다는 이유로 새 에디션을 만들지
  않는다.
- 항목은 `severity` 내림차순(`HIGH`가 `MEDIUM`보다 먼저), 그다음 `reasonCode`, 마지막으로
  `sourceReference` 오름차순으로 안정 정렬한다.
- 생성이 완료되면 선택한 항목과 표시 필드, 구간, 시간대, 규칙 버전과 원본 커서를
  고정한다. 이후 투영 변경이나 재구축이 기존 에디션을 수정하지 않는다.
- 선택 상태가 달라지거나 정정이 필요하면 기존 에디션을 덮어쓰지 않고 해당
  작업공간·시즌에서 증가하는 새 세대를 만든다. 상태가 `A → B → A`로 되돌아와 과거와
  같은 지문이 다시 나타나더라도 직전 에디션과 다르므로 과거 `A` 에디션을 재사용하지
  않고 새 세대를 만든다.
- 최신 에디션은 해당 작업공간·시즌에서 완료된 에디션 중 세대가 가장 큰
  에디션이다.

`GET .../editions/latest`와 `GET /api/v1/editions/{id}`는 저장된 고정 스냅샷을 반환하며
실시간 투영을 다시 조합하지 않는다.

새 에디션 생성 응답은 `201 Created`와 단건 조회 `Location`을, 같은 논리 상태의 반복
요청은 기존 에디션과 `200 OK`를 반환한다. 에디션 본문은 최소한 `editionId`,
`workspaceId`, `seasonId`, `generation`, `weekStart`, `zoneId`, `windowStart`, `windowEnd`,
`sourceCursor`, `generatedAt`, `ruleVersion`과 고정된 `items`를 포함한다. 항목에는
`reasonCode`, `severity`, `sourceReference`, `status`, `observedAt`과 `ruleVersion`을 둔다.
조회 대상이 없으면 `404 Not Found`를 반환한다.

## 호환성과 오류 경계

- v1 소비자는 명시적으로 채택한 이벤트 종류와 `eventVersion=1`만 처리한다.
- 생산자가 기존 필드 의미, 식별자 범위, 리비전 의미 또는 열거형을 바꾸려면 새 버전과
  생산자·소비자 호환성 검증이 필요하다.
- 응답과 격리 증거는 크기가 제한되어야 하며 원문 페이로드, PII, 자격 증명, 원문 URL과
  비밀 값을 포함하지 않는다.
- BRIEF 실패는 원본 트랜잭션을 롤백시키지 않는다. 로컬 MVP에서 생산자 변경은
  범위 밖이므로 이 성질은 생산자 측 구현 완료를 뜻하지 않는다.

## 수용 기준

- 첫 전달, 완전히 같은 중복, 식별자 충돌과 지원하지 않는 버전 결과가 구분된다.
- 지원하지 않는 이벤트의 완전히 같은 재생은 안정적으로 `UNSUPPORTED`를 반환하고 같은
  식별자의 다른 지문은 `CONFLICT`가 된다. 충돌 증거는 이벤트별 한 건을 넘지 않는다.
- 오래된 리비전은 투영을 변경하지 않고, 리비전 간격은 명시적인 증거를 남긴다.
- 세 이벤트 종류의 `ACTIVE`/`RESOLVED` 투영이 규칙 v1에 맞다.
- 재구축 결과가 같은 수락 수신 기록의 실시간 투영과 같다.
- 월요일 검증, IANA 시간대와 DST 경계의 `[start, end)` 계산이 고정 시간 테스트로 확인된다.
- 같은 요청 범위의 직전 `stateFingerprint`와 동일한 반복 요청은 에디션을 중복 생성하지
  않고, 선택 상태가 달라지거나 `A → B → A`로 되돌아오면 세대가 증가한다.
- 정렬 순서가 항상 재현되고, 생성 뒤 투영을 바꿔도 에디션 항목이 변하지 않는다.
- 최신 조회가 최대 세대를 반환하고 단건 조회가 동일 고정 스냅샷을 반환한다.

## 명시적 비목표

- 인증·인가와 외부 공개 API
- 메시지 브로커, 스케줄러와 원본 생산자 변경
- BATON 멤버십 복제 또는 원본 데이터베이스 직접 조회
- RELAY 제공자 전송과 GO 링크 생성
- AI 요약 또는 개인화 추천
- 실행 컨테이너 이미지와 운영 배포
