# PRD-0019: BATON 연속성 신호 이벤트 v2 수신 계약

- 상태: 채택됨
- 결정일: 2026-08-22
- 범위: 기존 내부 이벤트 수신 경로에서 BATON이 정의한 다섯 연속성 신호를 수용하는 계약

## 목적

BATON PRD-0006은 현재 다섯 연속성 신호, `CRITICAL`·`WARNING` 심각도, 영속 신호
신호 식별자, 신호별 연속 리비전과 날짜 변경 시 재계산 규칙을 생산자 계약으로 채택했다. 이 의미를
이름 변환으로 다른 이벤트 종류에 끼워 맞추지 않고 이벤트 v2로 정의한다.

기존 수신 경로·멱등성·충돌·리비전·재구축 경계를 유지하면서 이벤트 v2를 명시적으로
수용한다. BRIEF가 투영하는 이벤트 버전은 v2뿐이다.

## 이벤트 버전별 계약

`POST /api/v1/events` 경로와 PRD-0002의 공통 봉투를 사용하며 `sourceSeverity`를 함께 받는다.

| 버전 | 허용 `eventType` | `sourceSeverity` | 처리 |
|---|---|---|---|
| `1` 이하 | - | - | 수신 기록 없이 `400 Bad Request` |
| `2` | `ROLE_UNASSIGNED`, `ROLE_SUCCESSOR_MISSING`, `ROLE_PREPARATION_INCOMPLETE`, `ROUTINE_REPEATEDLY_OVERDUE`, `HANDOFF_INCOMPLETE` | `CRITICAL` 또는 `WARNING` 필수 | 수신·투영 |
| `3` 이상의 32비트 정수 | 위 다섯 열거형 | 생략 가능 | `UNSUPPORTED`로 최초 수신 기록을 보존하고 투영하지 않음 |

`eventVersion=2`에 `sourceSeverity`가 없으면 `400 Bad Request`다. 알 수 없는 열거형, 32비트
정수 범위를 벗어난 버전과 봉투 형식 오류도 수신 기록을 만들지 않는다.

`sourceSeverity`는 BATON의 원본 판정을 뜻하며 payload fingerprint, 최초 수신 기록과
재구축 입력에 포함한다. 심각도 없이 받은 미지원 기록은 `null`로 저장한다.

## 투영 규칙 v1

`ruleVersion`은 `1`이다. BATON의 심각도를 재판정하지 않고 다음과 같이 BRIEF 표시
심각도로 대응한다.

| v2 `sourceSeverity` | `AttentionItem.severity` |
|---|---|
| `CRITICAL` | `HIGH` |
| `WARNING` | `MEDIUM` |

`reasonCode`는 수신한 `eventType`과 같다. `ACTIVE`·`RESOLVED`, 복합 식별자, 리비전 공백,
오래된 이벤트, 현재 단건·목록·전이 조회와 브리프 고정 규칙은 기존 계약을 그대로 사용한다.

이 대응은 두 값 사이의 일대일 표시 변환이며 BATON 신호의 종류·상태·심각도를 BRIEF가
다시 판정하는 규칙이 아니다. 생산자 원본 심각도는 수신 기록에 보존한다.

## 저장

- `source_event_receipt.source_severity`는 nullable이며 `CRITICAL`·`WARNING`만 허용한다.
- 수신 기록, 점검 항목과 브리프 항목의 이벤트 종류 제약은 v2 다섯 타입만 허용하고,
  수신 기록의 `event_version`은 `2` 이상이다.
- `source_event_receipt_supported_contract`는 `UNSUPPORTED`가 아닌 기록에 `event_version = 2`와
  `source_severity IS NOT NULL`을 요구한다.
- 현재 투영과 브리프의 `severity` 저장 값은 `HIGH`·`MEDIUM`만 사용한다.
- 새 테이블, 인덱스, API 경로, 브로커와 생산자 전용 adapter를 추가하지 않는다.

## 수신 기록과 재생

PRD-0007 단건과 PRD-0011 이상 수신 기록 응답은 nullable `sourceSeverity`를 같은 안전한 최초
수신 필드로 반환한다. fingerprint와 원문은 계속 노출하지 않는다.

재구축은 `UNSUPPORTED`를 제외한 수신 기록을 `ingestion_sequence` 순서로 읽으며
저장한 `sourceSeverity`를 사용해 실시간 처리와 같은 현재 투영을 만든다.

## 언어 중립 계약 팩

- `contracts/schemas/source-event.v2.schema.json`을 이벤트 v2 요청의 기계 판독 기준으로
  제공한다.
- Draft 2020-12 검증기는 UUID와 시점 `format-assertion`을 활성화한다. JSON Schema가 숫자
  token의 소수점·지수 표기를 구분하지 못하는 한계는 PRD-0002의 정수 표현 계약과 실제
  소비자 수신 검증으로 보완한다.
- 세 UUID는 36자 하이픈 표기, `occurredAt`은 오프셋이 있는 ISO-8601 문자열을 사용하고
  `sourceReference`는 PRD-0002의 문자·공백 규칙을 따른다. 소비자는 PRD-0002에 따라 UUID·시점의
  다른 Jackson 표준 표현도 받으므로 생산자 형식은 JSON Schema로 확인한다.
- 문자열 패턴은 JDK 21 공백 문자를 명시하고 정상 surrogate 쌍을 허용하는 ECMAScript
  표현을 사용한다. Java 전용 문자 클래스나 엔진마다 다른 기본 공백 분류에 의존하지 않는다.
  NBSP·이모지·줄바꿈 뒤의 정상 문자는 허용하고 `U+0000`·짝이 없는 surrogate는 거부한다.
- `contracts/examples/*.json`은 BATON 다섯 신호와 한 신호의 심각도 변경·해소 생명주기를
  설명한다. 예시는 새 의미를 만들지 않으며 이 PRD가 필드 간 의미와 HTTP 결과의 기준이다.
- 계약 팩 버전의 단일 기준은 `contracts/VERSION`이다. BATON 생산자는 그 값을 고정해 실제
  serializer와 송신 경계를 검증해야 한다.
- Gradle 표준 `contractsZip` 작업은 `contracts/**`와 이 PRD, 직접 참조하는 PRD-0002·0007·0018을
  `baton-brief-contracts-<VERSION>.zip`으로 묶는다. 문서의 원래 경로를 유지해 ZIP 안에서
  상대 링크를 열 수 있게 한다. JVM DTO JAR, 별도 계약 서비스와 배포 플러그인은 만들지 않는다.
- BRIEF의 기존 v2 PostgreSQL 통합 시나리오는 계약 예시를 직접 요청 본문으로 사용한다.
  예시를 위한 별도 제품 시나리오를 복제하지 않는다.

이 팩은 BRIEF 소비자와 언어 중립 요청 형식의 일치를 증명한다. BATON 저장소가 버전을
고정하고 실제 serializer 출력과 outbox·송신 경계를 검증하기 전에는 생산자 호환 완료나
안정 버전으로 표시하지 않는다.

## 호환성과 오류 경계

- 지원 v2와 미지원 이벤트의 저장 fingerprint를 고정값으로 검증한다.
- 지원하지 않는 버전은 `eventVersion`이 `3` 이상인 경우다.
- 이후 버전을 지원하게 되어도 이미 `UNSUPPORTED`로 저장한 기록은 재투영하지 않는다.
  저장된 `processingOutcome`은 불변이며 재구축도 계속 제외한다.
- 같은 `eventId`의 `UNSUPPORTED` 기록은 같은 지문이면 `UNSUPPORTED`, 다른 지문이면
  `CONFLICT`를 반환한다.
- 이 BRIEF 소비자 변경의 책임은 BATON 생산자 코드, 인증, 전송 작업자와 종단 간 전달을
  포함하지 않는다. 생산자 쪽 완료 상태는 PRD-0018의 교차 저장소 근거로 따로 갱신한다.

## 수용 기준

- `eventVersion` `1` 이하와 심각도 없는 v2는 수신 기록 없이 `400`이고, `3` 이상은
  `UNSUPPORTED`로 보존된다.
- v2 다섯 타입의 `ACTIVE`·`RESOLVED`가 BATON 심각도 대응과 함께 적용된다.
- v2의 동일 재전달, 충돌, 오래된 리비전과 리비전 공백이 기존 결과 계약을 따른다.
- `sourceSeverity`가 fingerprint와 최초 수신 기록에 보존되고 다른 심각도의 같은
  `eventId`가 충돌로 분류된다.
- 재구축 뒤 현재 투영이 실시간 처리 결과와 같다.
- 이벤트 v2 계약 예시가 JSON Schema와 일치하고 같은 예시가 실제 BRIEF 수신·재구축
  시나리오에서 처리된다.
- `contracts/VERSION`에서 이름을 정한 계약 팩 ZIP에 스키마·예시와 이 PRD 및 직접 참조하는
  PRD-0002·0007·0018이 포함되며, 포함 문서의 상대 링크 대상이 ZIP 안에 존재한다.
- 전체 테스트와 실행 JAR 생성이 성공한다.

## 제외 범위

- 이 BRIEF 저장소에서 BATON 원본 변경 경로와 outbox 송신기를 구현하는 작업
- 실제 생산자 serializer와 종단 간 전달 성공 주장
- 계약 팩 게시·릴리스와 BATON 저장소의 버전 고정
- 기존 브리프 재작성
- BRIEF가 BATON 신호 종류·심각도를 다시 판정하는 규칙
- 운영 인증·인가, 브로커, 재시도 시간과 배포 설정

## 관련 문서

- [MVP 이벤트·투영·브리프 계약](../0002_mvp-contract/spec.md)
- [이벤트 수신 기록 조회](../0007_event-receipt-query/spec.md)
- [BATON 이벤트 연동 조건](../0018_baton-producer-compatibility/spec.md)
- [이벤트 계약 팩](../../../contracts/README.md)
- BATON `PRD-0006: BATON–BRIEF 연속성 신호 생산 계약`
