# PRD-0007: 이벤트 수신 증거 조회

- 상태: 채택됨
- 결정일: 2026-08-19
- 구현 상태: 로컬 구현 및 검증 완료
- 범위: 이벤트 식별자별 최초 수신 기록의 읽기 전용 단건 조회

## 목적

PRD-0002는 이벤트를 수신한 요청에서 처리 결과를 반환하지만, 호출자가 응답을 받지 못했거나
나중에 저장된 결과를 확인하려면 같은 이벤트를 다시 전송해야 한다. BRIEF가 이미 보존하는
`SourceEventReceipt`를 이벤트 식별자로 조회해 최초 수신 결과와 제한된 충돌 증거를 확인할
수 있게 한다.

이 조회는 전달 시도 이력, 원본 사실의 최신 상태나 생산자 전달 완전성을 판정하지 않는다.
최초 수신 기록을 읽을 뿐이며 기존 멱등 수신, 투영과 에디션 의미를 바꾸지 않는다.
작업공간·시즌 범위에서 `eventId`를 모르는 과거 이상 증거를 발견하는 이력 조회는
PRD-0011이 별도로 소유한다.

## HTTP API

다음 인증 없는 내부 로컬 경로를 추가한다.

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/api/v1/events/{eventId}/receipt` | 이벤트 식별자의 최초 수신 증거 조회 |

- `eventId`는 UUID다. 잘못된 UUID는 `400 Bad Request`와 PRD-0004의 `ProblemDetail`로
  응답한다.
- 저장된 수신 기록이 없으면 `404 Not Found`와 PRD-0004의 `ProblemDetail`로 응답한다.
- 이 로컬 계약은 운영 외부 공개나 호출 권한 판정이 완료되었다는 뜻이 아니다.

## 성공 응답

성공 응답은 `200 OK`이며 다음 필드를 반환한다.

| 필드 | 의미 |
|---|---|
| `eventId` | 최초 수신 기록의 이벤트 식별자 |
| `ingestionSequence` | BRIEF가 부여한 로컬 수신 순서이자 에디션 `sourceCursor`의 기준 |
| `eventType` | 최초 저장한 이벤트 종류 |
| `eventVersion` | 최초 저장한 이벤트 버전 |
| `sourceSeverity` | v2가 저장한 BATON 원본 심각도, v1·기존 미지원 기록은 `null` |
| `workspaceId` | 불투명 작업공간 참조 |
| `seasonId` | 불투명 시즌 참조 |
| `sourceReference` | 최초 저장한 이벤트의 길이가 제한된 불투명 원본 참조 |
| `aggregateRevision` | 최초 저장한 이벤트의 집계 리비전 |
| `occurredAt` | 원본이 사실을 관측한 정규화된 시각 |
| `state` | 최초 저장한 이벤트의 `ACTIVE` 또는 `RESOLVED` 상태 |
| `processingOutcome` | 최초 기록에 저장된 처리 결과 |
| `receivedAt` | BRIEF가 최초 수신 기록을 저장한 시각 |
| `conflictDetectedAt` | 다른 지문의 충돌을 처음 탐지한 시각, 없으면 `null` |

`ingestionSequence`는 BRIEF 로컬 기록 경계이며 생산자 이벤트의 누락이 없음을 보장하는
워터마크가 아니다.

## 최초 저장 결과와 재전달 의미

`processingOutcome`은 최초 수신 기록에 저장된 다음 결과 중 하나다.

- `APPLIED`
- `APPLIED_WITH_GAP`
- `STALE`
- `UNSUPPORTED`

완전히 같은 이벤트의 재전달은 `POST /api/v1/events`에서 `DUPLICATE` 응답을 만들 수 있지만
새 수신 기록을 만들거나 조회 응답의 `processingOutcome`을 `DUPLICATE`로 덮어쓰지 않는다.

같은 이벤트 식별자의 다른 지문은 `POST /api/v1/events`에서 `CONFLICT` 응답을 만들지만
최초 수신 기록과 `processingOutcome`을 바꾸지 않는다. 이벤트별 최초 충돌
탐지 시각만 선택적인 `conflictDetectedAt`으로 반환한다.

## 민감정보 제한

응답에 다음 값을 포함하지 않는다.

- `payloadFingerprint`와 `conflictingFingerprint`
- 원문 요청 payload와 정규화 전 입력
- SQL, PostgreSQL 진단, 예외 클래스·메시지와 스택 트레이스
- 자격 증명, 제공자 주소, 원문 URL과 불필요한 PII

`sourceReference`는 PRD-0002가 제한한 최대 128자의 불투명 참조만 사용하며 원본 객체를
확장 조회하거나 사람이 읽을 수 있는 이름으로 해석하지 않는다.

## 읽기 전용성과 호환성

- 조회는 수신 기록, 충돌 증거, 현재 투영과 불변 에디션을 생성·수정·삭제하지 않는다.
- 동일 재전달, 충돌 탐지와 투영 재구축 뒤에도 최초 저장 필드와 처리 결과가
  달라지지 않는다.
- 기존 `POST /api/v1/events`의 HTTP 상태와 `IngestResponse`를 바꾸지 않는 추가 계약이다.
- PRD-0019의 v2 수신 뒤에도 같은 최초 기록 표현을 사용하며 `sourceSeverity`만 nullable
  안전 필드로 포함한다.
- PRD-0011의 이상 이력 조회는 이 단건 응답과 같은 안전한 필드를 반환하지만,
  `APPLIED_WITH_GAP`, `STALE`, `UNSUPPORTED` 또는 최초 충돌이 있는 기록만 선정한다.
- 기존 `source_event_receipt`와 `source_event_conflict`를 읽으며 새 테이블, 열, 인덱스와
  Flyway 마이그레이션을 추가하지 않는다.
- PRD-0008의 현재 `retain-all` 경계에서는 모든 최초 수신 기록과 이벤트별 최초 충돌
  한 건을 자동 만료·삭제·압축하지 않는다. 후속 삭제 계약은 만료된 조회 대상의 상태와
  HTTP 응답 의미를 먼저 정의해야 한다.

## 비목표

- 정상 `APPLIED`를 포함한 전체 수신 기록 목록, 임의 검색·필터와 정렬 선택
- 모든 전달 시도와 중복·충돌 횟수의 이력 보존
- 숫자 보존 기간·용량, 삭제·압축 구현, 격리 해제와 재처리 정책
- 원본 누락 여부, 생산자 워터마크와 종단 간 전달 완전성 판정
- BATON 멤버십, 운영 인증·인가, 외부 공개와 배포

## 수용 기준

- 최초 수신 기록을 `eventId`로 조회해 저장된 메타데이터, `ingestionSequence`,
  `processingOutcome`과 `receivedAt`을 반환한다.
- 동일 이벤트 재전달 뒤에도 최초 `processingOutcome`이 유지된다.
- 다른 지문의 충돌 뒤에도 최초 저장 필드는 유지되고 `conflictDetectedAt`만 나타난다.
- 투영 재구축 전후의 조회 응답이 같다.
- 없는 이벤트는 `404 ProblemDetail`로 응답한다.
- 응답에 두 fingerprint, 원문 payload, SQL과 예외 상세가 없다.
- 조회 전후에 수신 기록, 충돌 증거, 투영과 에디션 저장 상태가 바뀌지 않는다.
