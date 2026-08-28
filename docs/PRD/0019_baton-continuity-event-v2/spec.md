# PRD-0019: BATON 연속성 신호 이벤트 v2 소비 계약

- 상태: 채택됨
- 결정일: 2026-08-22
- 범위: 기존 내부 이벤트 수신 경로에서 BATON의 권위 있는 다섯 연속성 신호를 v1과 함께 수용하는 계약

## 목적

BATON PRD-0006은 현재 다섯 연속성 신호, `CRITICAL`·`WARNING` 심각도, 영속 신호
정체성, 신호별 연속 리비전과 시간 재조정 의미를 생산자 계약으로 채택했다. 이 의미는
BRIEF 이벤트 v1의 세 타입과 일치하지 않으므로 이름 변환으로 v1에 끼워 맞추지 않는다.

기존 수신 경로·멱등성·충돌·리비전·재구축 경계를 유지하면서 이벤트 v2를 명시적으로
수용하고, v1 수신 기록과 불변 에디션을 그대로 읽고 재생할 수 있게 한다.

## 이벤트 버전별 계약

`POST /api/v1/events` 경로와 공통 봉투는 유지한다. 선택적인 `sourceSeverity`만 추가한다.

| 버전 | 허용 `eventType` | `sourceSeverity` |
|---|---|---|
| `1` | `HANDOFF_BLOCKED`, `ROUTINE_MISSED`, `DECISION_FOLLOW_UP_OVERDUE` | 반드시 생략 또는 `null` |
| `2` | `ROLE_UNASSIGNED`, `ROLE_SUCCESSOR_MISSING`, `ROLE_PREPARATION_INCOMPLETE`, `ROUTINE_REPEATEDLY_OVERDUE`, `HANDOFF_INCOMPLETE` | `CRITICAL` 또는 `WARNING` 필수 |
| 그 밖의 32비트 양수 | 알려진 열거형만 JSON으로 해석 | `UNSUPPORTED`로 최초 수신 증거를 보존하고 투영하지 않음 |

v2 지원 전 이미 `UNSUPPORTED`로 보존할 수 있었던 `eventVersion=2`·v1 타입·심각도 없음
조합은 동일 재전달 호환성을 위해 계속 `422 UNSUPPORTED`로 기록하되 투영하지 않는다.
그 밖의 지원 버전·타입·심각도 불일치는 `400 Bad Request`다. 알 수 없는 열거형, 32비트
양의 정수 범위를 벗어난 버전과 기존 봉투 형식 오류도 수신 기록을 만들지 않는다.

`sourceSeverity`는 BATON의 원본 판정을 뜻하며 payload fingerprint, 최초 수신 증거와
재구축 입력에 포함한다. v1 기록과 지원하지 않는 기존 기록은 `null`을 유지한다.

## 투영 규칙 v1 확장

`ruleVersion`은 계속 `1`이다. v1 세 타입의 기존 매핑은 바꾸지 않는다. v2는 BATON의
심각도를 재판정하지 않고 다음과 같이 BRIEF 표시 심각도로 대응한다.

| v2 `sourceSeverity` | `AttentionItem.severity` |
|---|---|
| `CRITICAL` | `HIGH` |
| `WARNING` | `MEDIUM` |

v2의 `reasonCode`는 수신한 다섯 `eventType` 중 하나이며 v1과 동일하게 `eventType`에서
파생한다. `ACTIVE`·`RESOLVED`, 복합 정체성, 리비전 공백, 오래된 이벤트, 현재 단건·목록·
전이 조회와 에디션 고정 규칙은 기존 계약을 그대로 사용한다.

이 대응은 두 값 사이의 일대일 표시 변환이며 BATON 신호의 종류·상태·심각도를 BRIEF가
다시 판정하는 규칙이 아니다. 생산자 원본 심각도는 수신 기록에 보존한다.

## 저장과 마이그레이션

- `source_event_receipt`에 nullable `source_severity`를 추가한다.
- 수신 기록, 현재 관심 항목과 불변 에디션 항목의 이벤트 종류 제약은 v1·v2 여덟 타입을
  허용한다.
- 기존 행의 `source_severity`는 `null`로 유지하며 값을 추정하거나 채우지 않는다.
- 현재 투영과 에디션의 `severity` 저장 값은 계속 `HIGH`·`MEDIUM`만 사용한다.
- 새 테이블, 인덱스, API 경로, 브로커와 생산자 전용 adapter를 추가하지 않는다.

## 수신 증거와 재생

PRD-0007 단건과 PRD-0011 이상 이력 응답은 nullable `sourceSeverity`를 같은 안전한 최초
수신 필드로 반환한다. fingerprint와 원문은 계속 노출하지 않는다.

재구축은 `UNSUPPORTED`를 제외한 v1·v2 수신 기록을 `ingestion_sequence` 순서로 읽으며
저장한 `sourceSeverity`를 사용해 실시간 처리와 같은 현재 투영을 만든다. v1 기록은 기존
타입 기반 심각도 규칙으로 재생한다.

## 언어 중립 계약 팩

- `contracts/schemas/source-event.v2.schema.json`을 이벤트 v2 요청의 기계 판독 기준으로
  제공한다.
- `contracts/examples/*.json`은 BATON 다섯 신호와 한 신호의 심각도 변경·해소 생명주기를
  설명한다. 예시는 새 의미를 만들지 않으며 이 PRD가 필드 간 의미와 HTTP 결과의 기준이다.
- 계약 팩 버전의 단일 기준은 `contracts/VERSION`이다. 현재 `2.0.0-rc.2`는 BRIEF 소비자의
  입력 계약 엄격화까지 반영한다. BATON 생산자는 같은 버전을 고정해 실제 serializer와
  송신 경계를 검증해야 한다.
- Gradle 표준 `contractsZip` 작업은 `contracts/**`와 이 PRD를
  `baton-brief-contracts-2.0.0-rc.2.zip`으로 묶는다. JVM DTO JAR, 별도 계약 서비스와
  배포 플러그인은 만들지 않는다.
- BRIEF의 기존 v2 PostgreSQL 통합 시나리오는 계약 예시를 직접 요청 본문으로 사용한다.
  예시를 위한 별도 제품 시나리오를 복제하지 않는다.

이 팩은 BRIEF 소비자와 언어 중립 요청 형식의 일치를 증명한다. BATON 저장소가 버전을
고정하고 실제 serializer 출력과 outbox·송신 경계를 검증하기 전에는 생산자 호환 완료나
안정 버전으로 표시하지 않는다.

## 호환성과 오류 경계

- v1 요청·응답·fingerprint 의미와 기존 수신 기록은 바꾸지 않는다.
- v2 지원 뒤 지원하지 않는 버전 판단은 `eventVersion`이 `1`도 `2`도 아닌 경우다.
- 기존에 `eventVersion=2`를 `UNSUPPORTED`로 저장한 로컬 데이터가 있다면 새 배포가 그
  기록을 재투영하지 않는다. 저장된 `processingOutcome`은 불변이며 재구축도 계속 제외한다.
- 같은 `eventId`의 기존 `UNSUPPORTED` 기록은 새 코드에서도 같은 지문이면
  `UNSUPPORTED`, 다른 지문이면 `CONFLICT`를 반환한다.
- 이 BRIEF 소비자 변경의 책임은 BATON 생산자 코드, 인증, 전송 작업자와 종단 간 전달을
  포함하지 않는다. 생산자 쪽 완료 상태는 PRD-0018의 교차 저장소 근거로 따로 갱신한다.

## 수용 기준

- v1 세 타입의 처리·재생·응답이 이전과 같다.
- v2 다섯 타입의 `ACTIVE`·`RESOLVED`가 BATON 심각도 대응과 함께 적용된다.
- v2의 동일 재전달, 충돌, 오래된 리비전과 리비전 공백이 기존 결과 계약을 따른다.
- `sourceSeverity`가 fingerprint와 최초 수신 증거에 보존되고 다른 심각도의 같은
  `eventId`가 충돌로 분류된다.
- 재구축 뒤 v1·v2 현재 투영이 실시간 처리 결과와 같다.
- 대표 기존 행에 마이그레이션을 적용했을 때 새 열은 `null`이고 기존 현재 투영·에디션
  항목을 보존한다.
- 이벤트 v2 계약 예시가 JSON Schema와 일치하고 같은 예시가 실제 BRIEF 수신·재구축
  시나리오에서 처리된다.
- `contracts/VERSION`에서 이름을 정한 계약 팩 ZIP에 스키마·예시와 이 PRD가 포함된다.
- 전체 테스트와 실행 JAR 생성이 성공한다.

## 명시적 비목표

- 이 BRIEF 저장소에서 BATON 원본 변경 경로와 outbox 송신기를 구현하는 작업
- 실제 생산자 serializer와 종단 간 전달 성공 주장
- 계약 팩 게시·릴리스와 BATON 저장소의 버전 고정
- v1 타입 삭제·이름 변경 또는 기존 에디션 재작성
- BRIEF가 BATON 신호 종류·심각도를 다시 판정하는 규칙
- 운영 인증·인가, 브로커, 재시도 시간과 배포 설정

## 관련 문서

- [MVP 이벤트·투영·에디션 계약](../0002_mvp-contract/spec.md)
- [이벤트 수신 증거 조회](../0007_event-receipt-query/spec.md)
- [BATON 생산자 호환성 선행조건](../0018_baton-producer-compatibility/spec.md)
- [이벤트 계약 팩](../../../contracts/README.md)
- BATON `PRD-0006: BATON–BRIEF 연속성 신호 생산 계약`
