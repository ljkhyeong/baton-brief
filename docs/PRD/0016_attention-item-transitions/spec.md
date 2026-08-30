# PRD-0016: 현재 관심 항목 상태 전이 증거 이력

- 상태: 채택됨
- 결정일: 2026-08-22
- 범위: 복합 정체성별 실제 적용 상태 전이의 집계 리비전 키셋 조회

## 목적

PRD-0013~0015는 관심 항목의 마지막 현재 상태를 단건 또는 상태별 목록으로 제공한다. 현재
항목만으로는 어떤 적용 리비전에서 활성·해소 상태가 바뀌었고 어느 전이에서 리비전 공백이
처음 발견됐는지 확인할 수 없다.

`source_event_receipt`에 보존된 최초 수신 증거 중 실제로 투영에 적용된 리비전만 읽어 상태
전이 증거를 제공한다. 현재 규칙으로 과거 투영을 다시 계산하거나 별도 상태 이력 테이블에
같은 사실을 복제하지 않는다.

## HTTP API

다음 인증 없는 내부 로컬 MVP 경로를 추가한다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/transitions` | 현재 관심 항목의 적용 상태 전이 이력 |

### 질의 매개변수

| 이름 | 필수 | 형식과 제약 | 의미 |
|---|---|---|---|
| `eventType` | 예 | PRD-0002의 지원 이벤트 종류 | 복합 정체성의 이벤트 종류 |
| `sourceReference` | 예 | 공백만 있는 값과 `U+0000` 금지, Unicode code point 기준 최대 128자 | 복합 정체성의 원본 참조 |
| `beforeAggregateRevision` | 아니요 | 양의 정수 | 이 리비전보다 작은 과거 전이부터 조회 |
| `limit` | 아니요 | `1..100`, 기본값 `20` | 한 응답의 최대 전이 수 |

잘못된 UUID·열거형, 공백만 있는 원본 참조, `U+0000`을 포함하거나 Unicode code point 기준
128자를 넘는 참조, 양수가 아닌 커서와 `limit` 범위 오류는 `400 Bad Request`와 PRD-0004의
표준 `ProblemDetail`로 거부한다.

작업공간·시즌이나 복합 정체성에 적용 전이가 없어도 원본 존재 여부를 판정하지 않고
`200 OK`의 빈 이력을 반환한다.

## 선정과 순서

- 작업공간·시즌·`eventType`·`sourceReference`가 모두 정확히 일치해야 한다.
- 최초 수신 결과가 `APPLIED` 또는 `APPLIED_WITH_GAP`인 기록만 전이로 반환한다.
- 투영을 바꾸지 않은 `STALE`·`UNSUPPORTED`, 동일 재전달과 충돌 요청은 포함하지 않는다.
- 원본 상태 순서인 `aggregateRevision` 내림차순으로 정렬한다.
- `beforeAggregateRevision`은 배타 커서다. `limit + 1`로 다음 페이지를 판단하고 전체 개수는
  조회하지 않는다.

같은 복합 정체성에서 실제 적용된 리비전은 단조 증가하므로 새 전이는 첫 페이지 앞에
추가된다. 현재 `retain-all` 경계에서는 이미 반환한 전이가 수정·삭제되지 않는다. 최신
전이를 확인하려면 첫 페이지부터 다시 조회한다.

## 성공 응답

성공 응답은 다음 필드를 가진다.

- `transitions`: 적용 상태 전이 배열
- `nextBeforeAggregateRevision`: 다음 페이지가 있으면 마지막 반환 전이의
  `aggregateRevision`, 없으면 `null`

각 전이는 다음 필드만 반환한다.

- `eventId`: 전이를 만든 최초 수신 이벤트 식별자
- `aggregateRevision`: 원본 복합 정체성의 적용 리비전
- `state`: 해당 전이의 `ACTIVE` 또는 `RESOLVED`
- `observedAt`: 원본 이벤트의 정규화된 `occurredAt`
- `detectedRevisionGap`: 해당 전이에서 직전 적용 리비전과의 공백을 새로 발견했는지 여부

`detectedRevisionGap`은 전이별 사실이다. 이후 정상 리비전의 값은 `false`일 수 있지만 현재
`AttentionItem.revisionGap`은 과거 공백을 누적해 `true`를 유지할 수 있다. 두 의미를 서로
대체하지 않는다.

## 영속성과 재구축

- 기존 `source_event_receipt`의 복합 정체성, `aggregate_revision`, `event_state`,
  `occurred_at`, `processing_outcome`을 읽는다.
- `processingOutcome` 전체나 수신 시각, fingerprint, 원문 payload와 충돌 증거는 응답에
  노출하지 않는다.
- 재구축은 수신 기록을 바꾸지 않으므로 성공·실패 재구축 전후 전이 이력도 바뀌지 않는다.
- 새 테이블, 열, 인덱스와 Flyway 마이그레이션을 추가하지 않는다. 실제 운영 규모와 조회
  계획 근거 없이 인덱스를 미리 만들지 않는다.

## 수용 기준

- 실제 적용된 상태 전이만 집계 리비전 내림차순으로 반환한다.
- `APPLIED_WITH_GAP` 전이는 `detectedRevisionGap=true`, 정상 적용 전이는 `false`다.
- 동일 리비전의 `STALE`, 미지원, 중복과 충돌은 전이를 추가하지 않는다.
- 배타 커서로 다음 과거 전이를 중복 없이 조회하고 끝에서는 `null`을 반환한다.
- 다른 작업공간·시즌·이벤트 종류·원본 참조의 기록이 섞이지 않는다.
- 조회는 수신 기록, 현재 투영과 불변 에디션을 변경하지 않는다.

## 명시적 비목표

- 규칙 버전별 과거 `AttentionItem` 전체 스냅샷과 당시 심각도 재계산
- 현재 항목의 누적 `revisionGap`을 각 과거 전이에 역산
- `STALE`·`UNSUPPORTED`·충돌·중복을 포함한 전체 수신 감사 이력
- 상태 전이 변경·삭제, 재처리와 격리 해제
- offset, 전체 개수, 기간 검색과 새 인덱스·마이그레이션
- 인증·인가, 외부 공개 API와 운영 배포
