# 인수인계

내부 HTTP 이벤트 수신, 투영/재구축, 불변 주간 에디션, 에디션 이력 조회와 표준 요청 오류
응답 로컬 MVP에 이어 두 불변 에디션의 읽기 전용 비교 API 구현을 완료했다.
PostgreSQL 통합 테스트, 저장소 전체 빌드와 Java 21 패키지 실행 점검까지 통과했다.
Spring Boot Actuator 표준 aggregate health를 사용하는 최소 상태 확인도 구현했고,
대상 PostgreSQL 통합 테스트와 전체 테스트·실행 JAR 생성이 성공했다.
최초 이벤트 수신 증거의 읽기 전용 단건 조회도 구현했고, 대상 PostgreSQL 통합 테스트와
전체 테스트·실행 JAR 생성이 성공했다.

PRD-0008에서 모든 수신 기록과 이벤트별 최초 충돌 한 건을 보존하는 `retain-all`, 불변
에디션 `sourceCursor` 근거와 동기 전역 원자적 투영 재구축 경계를 채택하고 구현했다.
강제 실패 롤백과 재구축·지원 이벤트 수신 잠금 동시성 대상 테스트, 변경 뒤 전체
테스트와 실행 JAR 생성이 성공했다.

PRD-0009에서 기존 작업공간·시즌 전역 `latest`를 유지하면서 정확한 주간·시간대 범위의
최신 불변 에디션을 조회하는 별도 경로를 채택하고 구현했다. PostgreSQL 통합 시나리오와
저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0010에서 새 에디션 항목에 `aggregateRevision`과 `revisionGap`을 함께 고정하고,
이전 항목의 미기록 값은 `null`로 유지하는 계약을 채택하고 구현했다. PostgreSQL
Testcontainers 빈 데이터베이스의 V1~V6 적용과 대표 V2 데이터의 V3~V6 업그레이드,
대상 통합 시나리오와 저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0011에서 작업공간·시즌별 과거 이상 수신 증거를 `ingestionSequence` 배타 키셋으로
조회하는 계약을 채택하고 구현했다. 기존 PostgreSQL 수신 통합 시나리오와 저장소 전체
테스트·실행 JAR 생성이 성공했다.

PRD-0012에서 생성·전역 최신·주간 최신·단건 에디션에 `ETag`를 제공하고 Spring MVC의
표준 `If-None-Match` 조건부 조회를 사용하는 계약을 채택하고 구현했다. 대상 PostgreSQL
통합 시나리오와 저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0013에서 기존 복합 정체성으로 현재 관심 항목 한 건을 조회하는 계약을 채택하고
구현했다. 대상 PostgreSQL 수신 통합 시나리오와 저장소 전체 테스트·실행 JAR 생성이
성공했다.

PRD-0014에서 작업공간·시즌의 현재 `ACTIVE` 관심 항목을 복합 정체성 배타 키셋으로
조회하는 계약을 채택하고 구현했다. 대상 PostgreSQL 수신 통합 시나리오와 저장소 전체
테스트·실행 JAR 생성이 성공했다.

PRD-0015에서 같은 목록 경로의 기본 `ACTIVE` 동작을 유지하면서 `RESOLVED` 현재 항목을
선택하는 상태 필터 계약을 채택하고 구현했다. 대상 PostgreSQL 수신 통합 시나리오와
저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0016에서 보존 수신 기록 중 실제 적용된 상태 전이 증거를 복합 정체성별 집계 리비전
키셋으로 조회하는 계약을 채택하고 구현했다. 대상 PostgreSQL 수신 통합 시나리오와
저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0017에서 현재 관심 항목 단건 응답에 규칙 버전과 마지막 적용 집계 리비전에 결합한
`ETag`를 제공하고 Spring MVC의 표준 `If-None-Match` 조건부 조회를 채택하고 구현했다.
대상 PostgreSQL 수신 통합 시나리오와 저장소 전체 테스트·실행 JAR 생성이 성공했다.

PRD-0018에서 BATON `ccfbc46`의 현재 연속성 신호와 BRIEF 이벤트 v1을 읽기 전용으로
대조했다. 이후 BATON `dc1a7f8`의 PRD-0006에서 현재 다섯 신호와 `CRITICAL`·`WARNING`,
영속 신호 정체성, 신호별 연속 리비전과 시간 재조정 의미를 채택했다. BRIEF 이벤트 v2와
BATON 전용 outbox·송신기·실제 직렬화 아티팩트는 당시 구현하지 않았다.

PRD-0019에서 기존 v1을 보존하면서 BATON 다섯 신호와 필수 `sourceSeverity`를 수용하는
이벤트 v2를 구현했다. `CRITICAL → HIGH`, `WARNING → MEDIUM` 일대일 표시, nullable 최초
수신 증거, 심각도를 포함한 v2 지문, V7 저장 제약과 v1·v2 재구축을 검증했다.

이후 BATON `codex/brief-producer-contract-20260822` 작업 브랜치에서 `2.0.0-rc.1` 계약 팩을
고정한 실제 Java/Jackson 직렬화, 신호별 연속 리비전·불변 outbox와 설정형 시간 재조정을
구현하고 전체 빌드를 통과했다. 원본 변경 경로의 같은 트랜잭션 자동 재조정, outbox 송신기와
실제 BRIEF HTTP 종단 간 전달은 아직 없다.

이후 `contracts/VERSION`의 `2.0.0-rc.1`, Draft 2020-12 JSON Schema와 생산 의미가 반영된
예시를 추가했다. 기존 v2 통합 시나리오가 이 예시를 직접 요청 본문으로 사용하며 Gradle
표준 `contractsZip`은 `contracts/**`와 PRD-0019를 재현 가능한 ZIP으로 묶는다. 이는 BRIEF
소비자 계약 팩이며 BATON 실제 serializer 출력 검증이나 계약 팩 게시를 뜻하지 않는다.

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest' --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.consumes BATON continuity event v2 and reproduces it after rebuild' contractsZip`:
  성공. 모든 계약 예시의 JSON Schema 일치, 같은 예시의 PostgreSQL 수신·재구축과
  `baton-brief-contracts-2.0.0-rc.1.zip` 생성을 확인했다.
- `./gradlew --no-daemon clean test :bootstrap:bootJar`: 성공. 빈 데이터베이스 V1~V7 적용,
  전체 테스트와 `bootstrap-0.1.0-SNAPSHOT.jar` 생성을 확인했다.

관심 항목에서 실제 소비되지 않던 `item_id` 대리키는 Flyway V4에서 제거했다. 기존
`(workspace_id, season_id, event_type, source_reference)` 정체성을 복합 기본 키로 사용하며,
제품 API와 투영 의미는 바꾸지 않았다.

조회·선정·지문·비교·재구축·응답에서 소비되지 않던 `attention_item.projected_at`은
Flyway V5에서 제거했다. 최초 수신 증거의 `source_event_receipt.received_at`은 수신 시각
계약이므로 그대로 유지했다.

규칙 v1의 현재 투영 `reasonCode`는 `eventType`과 항상 같으므로 도메인 계산값으로
유지하고, 중복된 `attention_item.reason_code`는 Flyway V6에서 제거했다. 생성 당시 값을
보존해야 하는 불변 `brief_edition_item.reason_code`는 그대로 유지했다.

## 채택한 기반

- ADR-0001에서 BRIEF의 독립 읽기 모델 서비스 경계를 채택했다.
- ADR-0002에서 Kotlin/JVM 2.3.21, Java 21 도구 체인·JVM 21 바이트코드 대상·JDK 21 실행 환경,
  Spring Boot/BOM 4.1.0, Gradle wrapper 9.2.1과 Kotlin DSL, PostgreSQL 18.4, Spring JDBC
  `JdbcClient`와 Flyway를 채택했다. JPA는 사용하지 않는다.
- 불변 에디션, 버전이 있는 이벤트와 제한된 상태를 Kotlin 타입으로 표현하는 BRIEF 자체
  적합성을 기준으로 언어를 선택했다. Java 25는 호환성 확인만 했으며 MVP 기능상 필요와
  고정 CI/실행 이미지가 없어 보류했다. Kotlin 2.3.21의 공식 Gradle 호환 범위 안에
  있도록 wrapper는 9.2.1을 유지한다.
- 모듈은 `domain`, `application`, `adapter-in-web`, `adapter-out-persistence`, `bootstrap`
  다섯 개이며 의존은 어댑터에서 `application`, 다시 `domain` 쪽으로만 향한다.
- PRD-0002에서 인증 없는 내부 HTTP API, 이벤트 v1 봉투와 결과, 규칙 v1 투영,
  리비전 공백/오래된 리비전 처리, 재구축과 불변 주간 에디션 계약을 채택했다.
- PRD-0003에서 작업공간·시즌별 에디션 요약을 `generation` 배타 키셋으로 조회하는 내부
  HTTP 계약을 채택했다.
- PRD-0004에서 요청 검증 실패와 명시적 `404`를 RFC 9457 `ProblemDetail`로 반환하는
  계약을 채택했다.
- PRD-0005에서 같은 작업공간·시즌의 두 불변 에디션을 안정적인 항목 키로 비교하는
  내부 HTTP 계약을 채택했다.
- PRD-0006에서 프로세스와 필수 데이터베이스의 Spring Boot 표준 aggregate 상태만
  `/actuator/health`로 노출하는 로컬 관리 계약을 채택했다.
- PRD-0007에서 이벤트 식별자별 최초 수신 메타데이터와 제한된 충돌 탐지 시각을
  조회하는 인증 없는 내부 로컬 계약을 채택했다.
- PRD-0008에서 숫자 TTL이나 SLO를 만들지 않는 현재 `retain-all` 기준, 이벤트별 최초
  충돌 한 건, 불변 에디션과 `sourceCursor` 근거, `UNSUPPORTED`를 제외한 동기 전역
  원자적 투영 재구축 경계를 채택했다.
- PRD-0009에서 작업공간·시즌·`weekStart`·`zoneId`가 정확히 같은 범위의 최대
  `generation` 에디션을 저장된 불변 스냅샷으로 반환하는 별도 조회 계약을 채택했다.
  `ruleVersion`은 조회 필터가 아니며 저장된 값을 응답에 포함한다.
- PRD-0010에서 새 에디션 항목의 집계 리비전·리비전 공백 근거를 응답,
  `stateFingerprint`와 비교 `changed` 판정에 포함하는 계약을 채택했다. Flyway V3 이전
  항목은 정확한 근거가 없으므로 두 값을 `null`로 유지하고 `0`·`false`로 기존 데이터를
  채우지 않는다. 이 의미 추가로 투영·에디션 `ruleVersion`을 올리지 않는다.
- PRD-0011에서 `APPLIED_WITH_GAP`, `STALE`, `UNSUPPORTED` 또는 최초 충돌이 있는 수신
  기록을 작업공간·시즌 범위에서 탐색하는 읽기 전용 계약을 채택했다. 충돌이 있는
  `APPLIED`도 포함하며, 페이지 커서는 스냅샷 토큰으로 해석하지 않는다.
- PRD-0012에서 전체 불변 에디션 응답의 불투명 `ETag`와 적용 대상 `GET`의 표준
  `If-None-Match` 조건부 조회를 채택했다. 최신 조회의 검증자는 요청 시점에 선택된
  에디션을 나타내며 캐시 TTL과 외부 공개 정책은 포함하지 않는다.
- PRD-0013에서 `(workspaceId, seasonId, eventType, sourceReference)`가 정확히 같은 현재
  관심 항목 단건 조회를 채택했다. `ACTIVE`와 `RESOLVED`를 모두 반환하며 목록·검색·과거
  상태 이력은 포함하지 않는다.
- PRD-0014에서 작업공간·시즌의 현재 `ACTIVE` 항목을 `eventType`·`sourceReference`
  순서의 배타 키셋으로 탐색하는 계약을 채택했다. 커서는 요청 간 스냅샷이 아니며 최신
  상태는 첫 페이지부터 다시 조회한다.
- PRD-0015에서 같은 키셋 목록에 선택적인 `status`를 추가했다. 생략 시 `ACTIVE`이고
  `RESOLVED`를 선택할 수 있으며, 어느 쪽도 과거 상태 이력으로 해석하지 않는다.
- PRD-0016에서 실제 적용된 `APPLIED`·`APPLIED_WITH_GAP` 상태 전이만 원본 집계 리비전
  내림차순 키셋으로 조회하는 계약을 채택했다. 현재 규칙의 과거 전체 투영 재계산은 아니다.
- PRD-0017에서 현재 관심 항목 단건 응답의 규칙 버전·마지막 적용 집계 리비전에 결합한
  불투명 `ETag`와 Spring MVC 표준 `If-None-Match` 조건부 조회를 채택했다. 목록 검증자와
  캐시 TTL·저장소는 포함하지 않는다.
- PRD-0018에서 BATON 생산자 연동 전에 권위 있는 신호 의미, 안정적 정체성·상태 생명주기,
  JPA 버전과 분리된 외부 집계 리비전, 시간 경계 재조정, 전용 transactional outbox와 실제
  직렬화·종단 간 검증을 모두 갖추도록 선행 게이트를 채택했다. BATON PRD-0006에서 생산
  의미를 결정했고 PRD-0019에서 BRIEF 이벤트 v2 소비를 구현했다.
- PRD-0019에서 v1 세 타입과 fingerprint를 보존하고 v2 다섯 타입·원본 심각도·V7 저장과
  재구축을 추가했다. `2.0.0-rc.1` 계약 팩의 BATON 실제 직렬화와 생산자 신호 스트림·
  불변 outbox·설정형 시간 재조정까지 검증했다. 다음 교차 저장소 진입점은 영향받는 BATON
  원본 변경을 같은 트랜잭션 재조정 경계에 연결한 뒤 outbox 송신기를 구현하는 작업이다.
- 로컬 PostgreSQL 18.4 `compose.yml`을 추가했다. 애플리케이션 기본값은 이 데이터베이스에
  연결하고 Flyway를 활성화하며, 다른 환경은 Spring Boot 표준 데이터 원본/Flyway
  환경변수로 덮어쓴다.

## 구현한 로컬 MVP

1. `POST /api/v1/events`가 수신 기록과 투영을 같은 트랜잭션에서 처리한다.
2. 동일 이벤트 중복, 제한된 충돌 증거, 일관된 미지원 거부, 오래된 리비전과 리비전 공백을
   구분한다.
3. 재구축이 수락된 수신 기록으로 현재 투영만 원자적으로 재생성하고 기존 에디션을
   보존한다.
4. 월요일 시작 IANA 시간대 구간, 로컬 수신 기록 `sourceCursor`, 상태 지문과
   작업공간/시즌 `generation`으로 불변 에디션을 생성한다. 같은 요청 범위의 직전 상태는
   멱등하게 재사용하고, `A → B → A`처럼 상태가 되돌아오면 과거 에디션을 재사용하지 않고
   새 세대를 만든다.
5. 최신·단건 에디션 조회는 저장한 고정 스냅샷을 반환하고, 에디션 이력은
   `generation` 내림차순 요약과 배타적 다음 커서를 반환한다.
6. 이벤트·생성 시각을 PostgreSQL `TIMESTAMPTZ` 의미와 맞게 마이크로초로 정규화하고,
   `occurredAt`, `weekStart`, `zoneId`의 HTTP 표현을 엄격하게 검증한다.
7. Spring Boot 표준 Problem Details 자동 구성으로 대표 `400`·`404` 요청 오류를
   `application/problem+json`으로 반환한다.

동시 중복 수신은 수신 기록 하나와 `202/200`, 동시 동일 상태 에디션 생성은 에디션 하나와
`201/200`으로 직렬화한다.

## 구현한 현재 관심 항목 단건 조회

- `GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/current`에
  `eventType`과 `sourceReference`를 전달해 현재 투영 한 건을 조회한다.
- 이벤트 적용이 사용하던 복합 기본 키 조회를 포트로 승격해 재사용했고 기존
  `AttentionItemResponse`를 반환한다. 새 DTO, SQL 도우미, 스키마와 인덱스는 없다.
- 현재 항목은 후속 지원 이벤트와 재구축 결과에 따라 바뀔 수 있다. 불변 에디션이나 과거
  투영 이력으로 해석하지 않는다.
- 현재 응답은 `ruleVersion`과 마지막 적용 리비전에 결합한 `ETag`를 제공한다. 같은 현재
  표현은 `304`, 더 큰 적용 리비전으로 바뀐 표현은 이전 검증자에 `200`과 새 `ETag`를
  반환하며 직접 헤더 파서·본문 해시·캐시 저장소는 없다.

## 구현한 현재 관심 항목 상태별 키셋 조회

- `GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items`가 현재
  항목을 `status`와 복합 정체성 오름차순으로 반환한다. `status`를 생략하면 기존처럼
  `ACTIVE`이고 `RESOLVED`도 선택할 수 있다.
- 두 값이 함께 있는 `AttentionItemCursor`와 PostgreSQL 행 값 비교를 사용하고,
  `limit + 1`로 다음 커서를 판단한다. offset, 전체 개수와 새 인덱스는 없다.
- 기존 `AttentionItemResponse`와 한 SQL 경로를 재사용하며 검색·정렬 선택, 과거 상태
  이력과 불변 에디션 미리보기는 포함하지 않는다.

## 구현한 현재 관심 항목 상태 전이 증거 이력

- `GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/transitions`가
  복합 정체성의 실제 적용 상태 전이를 `aggregateRevision` 내림차순으로 반환한다.
- `APPLIED`·`APPLIED_WITH_GAP`만 포함하고 `STALE`·`UNSUPPORTED`, 중복과 충돌은 제외한다.
- `detectedRevisionGap`은 해당 전이에서 새 공백을 발견했는지 나타내며 현재 항목의 누적
  `revisionGap`과 구분한다.
- 기존 수신 기록과 `limit + 1`을 사용하며 새 테이블, 열, 인덱스와 Flyway는 없다.

## 구현한 주간 범위 최신 조회

- `GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/weekly/latest`에
  `weekStart`와 `zoneId`를 전달하는 읽기 전용 조회를 추가했다.
- 기존 `GET .../editions/latest`는 계속 작업공간·시즌 전체의 최대 `generation`을
  반환한다. 새 경로는 작업공간·시즌·주간·시간대가 모두 같은 범위만 조회한다.
- 현재 투영을 다시 조합하지 않고 저장된 전체 불변 에디션을 반환하며, 새 스키마나
  Flyway 마이그레이션을 추가하지 않는다.
- PostgreSQL 통합 시나리오에서 전역 최신과 주간 최신의 분리, 작업공간·시즌·주간·시간대
  정확 범위, 월요일·IANA 시간대 입력 오류와 미존재 범위 `404`를 확인했다.

## 구현한 에디션 리비전 근거 고정

- `brief_edition_item`에서 `null`을 허용하는 `aggregate_revision`·`revision_gap`은 V3 이전
  행의 알 수 없는 근거를 보존하고, 신규 저장은 두 값을 함께 고정한다.
- 두 열은 함께 `null`이거나 함께 값이 있어야 하며, 값이 있는 `aggregate_revision`은
  양수여야 한다. 기존 행에는 값을 추정해 채우지 않는다.
- 두 근거를 에디션 응답과 `stateFingerprint`에 포함하고, 근거만 달라진 같은 키의 항목도
  비교 응답의 `changed.before`·`changed.after`에 포함한다.
- 처음 리비전 근거를 포함한 생성은 이전 지문과 달라 새 `generation`을 만들고, 이후 같은
  근거는 멱등하게 재사용한다. 기존 에디션은 수정하지 않는다.
- 새 경로, 인증, 생산자·브로커 변경, 인덱스와 규칙 버전 변경은 포함하지 않는다.
- PostgreSQL Testcontainers 빈 데이터베이스에 Flyway V1~V6를 적용한 대상 시나리오에서
  신규 근거 고정, 근거만 다른 새 세대와 즉시 멱등 재사용, 정방향·역방향 비교의
  `1`·`false` ↔ `3`·`true`, 재구축 뒤 비교 불변성과 실제 동일 전체 스냅샷
  `A → B → A`의 새 역사적 세대를 확인했다.
- 별도 PostgreSQL 스키마에 V1·V2 SQL과 대표 투영·에디션 행을 준비한 뒤 V3~V6 SQL을
  적용해 기존 행과 V3 이전 리비전 근거의 두 `null`, V4 복합 기본 키, V5·V6 열 제거,
  리비전 근거의 쌍·양수 CHECK 제약 거부를 확인했다.

## 구현한 후속 비교 조회

- `GET /api/v1/editions/{targetEditionId}/changes?fromEditionId={baseEditionId}`가 저장된 두
  불변 스냅샷의 `added`, `removed`, `changed.before`·`changed.after`를 반환한다.
- 비교 키는 `(reasonCode, sourceReference)`이며 `added`·`changed`는 대상 순서,
  `removed`는 기준 순서를 유지한다.
- 비교는 저장된 스냅샷만 읽으므로 이후 투영 변경이나 재구축에도 기존 비교 결과가
  달라지지 않는다.

## 구현한 불변 에디션 조건부 조회

- 생성·전역 최신·주간 최신·단건 에디션 응답에 API v1 표현과 `editionId`에 결합한 강한
  `ETag`를 제공한다. 호출자는 값을 해석하지 않고 그대로 재사용한다.
- 적용 대상 `GET`의 `If-None-Match` 처리는 Spring MVC가 `ResponseEntity` 헤더로 자동
  수행한다. 별도 헤더 파서, 직접 `304` 분기, 본문 해시와 캐시 저장소를 추가하지 않았다.
- 특정 에디션은 투영 변경·재구축 뒤에도 같은 검증자를 유지한다. 전역·주간 최신은 선택된
  에디션이 바뀔 때 검증자도 바뀐다.

## 구현한 최소 상태 확인

- `bootstrap`의 기본 starter를 `spring-boot-starter-actuator`로 교체해 중복 의존성 없이
  `GET /actuator/health`를 제공한다.
- Spring Boot가 `DataSource`를 감지해 자동 구성하는 DB health contributor를 사용하며,
  정상 응답에는 aggregate `status`만 노출한다.
- Actuator 탐색 페이지와 health probes는 표준 속성으로 비활성화했다. 커스텀 controller,
  DTO, `HealthIndicator`, 상태 매퍼와 DB 확인 SQL은 추가하지 않았다.
- 대상 PostgreSQL 통합 테스트와 전체 `clean test`, `bootJar` 생성이 성공했다.

## 구현한 이벤트 수신 증거 조회

- `GET /api/v1/events/{eventId}/receipt`가 최초 수신 메타데이터,
  `ingestionSequence`, 저장된 `processingOutcome`, `receivedAt`과 선택적인
  `conflictDetectedAt`을 반환한다.
- `DUPLICATE`와 `CONFLICT`는 최초 `processingOutcome`을 덮어쓰지 않는다. 충돌은 최초
  탐지 시각만 별도 증거로 드러낸다.
- fingerprint, 원문 payload, SQL과 예외 상세는 응답에 포함하지 않는다.
- 기존 기본 키 기반 읽기만 추가하며 새 스키마, 보존 기간, 삭제와 격리 해제 정책은
  포함하지 않는다.
- 대상 PostgreSQL 통합 테스트와 전체 `clean test`, `bootJar` 생성이 성공했다.

## 구현한 이상 수신 증거 이력 조회

- `GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/event-receipts/anomalies`가
  `APPLIED_WITH_GAP`, `STALE`, `UNSUPPORTED` 또는 최초 충돌이 있는 보존 수신 기록을
  발견하게 하는 계약을 채택했다.
- 충돌이 있는 `APPLIED`는 포함하고 충돌이 없는 `APPLIED`는 제외한다. 동일 재전달의
  `DUPLICATE`는 최초 처리 결과나 별도 수신 기록으로 저장되지 않는다.
- `beforeIngestionSequence` 배타 커서와 `limit`을 사용해 `ingestionSequence` 내림차순으로
  반환한다. 최초 충돌은 나중에 추가될 수 있으므로 여러 페이지를 하나의 스냅샷으로
  보장하지 않으며 최신 이상 증거는 첫 페이지부터 새로 조회한다.
- PRD-0007의 안전한 수신 증거 필드만 반환하고 새 필터·전체 개수·스키마·인덱스·인증·
  재처리 동작을 추가하지 않는다.
- 기존 PostgreSQL 수신 시나리오를 새 테스트 메서드 없이 확장했다. 최초 `APPLIED`와
  `DUPLICATE` 직후 빈 목록, 후속 충돌 뒤 `APPLIED` 포함, 수신 순서 `4`·`3`에서 배타
  커서 `3`으로 이어지는 `2`·`1`, `UNSUPPORTED`·`APPLIED_WITH_GAP`·`STALE`·충돌이 있는
  `APPLIED`, 다른 시즌·작업공간의 빈 범위를 확인했다.
- 기존 수신 증거 항목 표현을 재사용했고 별도 수신 증거 항목 DTO, Flyway 마이그레이션과
  인덱스를 추가하지 않았다.
- `./gradlew --no-daemon clean test :bootstrap:bootJar`가 성공해 전체 테스트와 실행 JAR
  생성을 확인했다.
- 첫 페이지와 다음 페이지 요청 사이에 충돌이 추가되는 별도 중간 페이지 시나리오는 직접
  실행하지 않았다. 요청 간 비스냅샷과 첫 페이지 새로고침은 계약의 명시적 한계다.

## 구현한 보존·재구축 경계

- 현재 스키마에는 수신 기록, 최초 충돌 증거와 불변 에디션을 삭제·압축하는 경로가 없고,
  대체 계약 전까지 모든 저장 결과를 보존한다.
- 재구축은 PostgreSQL 트랜잭션 advisory lock을 투영 전역에서 배타적으로 사용한다.
  지원 이벤트 수신은 같은 잠금을 공유 모드로, 에디션 생성과 다른 재구축은 배타 모드로
  사용해 직렬화한다.
- `UNSUPPORTED` 수신 기록은 보존하지만 재생 입력에서는 제외한다. 나머지 기록을
  `ingestion_sequence` 순서와 실시간 규칙으로 재생하고 현재 `attention_item`만 같은
  트랜잭션에서 전체 교체한다.
- 기존 통합 시나리오는 실시간·재구축 결과의 재현성과 재구축 뒤 수신 증거·에디션
  불변성을 확인했다. 확장한 대상 시나리오는 재구축 도중 강제 예외의 원자적 롤백과
  재구축·지원 이벤트 수신 사이의 잠금 직렬화를 직접 확인했다.
- 에디션 생성과 다른 재구축도 구현에서 같은 전역 배타 잠금 경로를 사용하지만, 대상
  테스트가 두 별도 동시 실행 시나리오까지 직접 증명한 것으로 확대하지 않는다.

## 현재 검증

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.ingestion distinguishes duplicate conflict unsupported stale and gap'`:
  성공. PostgreSQL 18.4 Testcontainers에서 수신 증거와 이상 증거 키셋, 재구축 뒤 현재
  단건의 리비전·공백 근거, 현재 `ACTIVE` 세 종류의 복합 키셋 페이지, 후속 `RESOLVED`
  갱신과 활성 목록 제외·해소 목록 포함, 적용 전이 `4 → 3 → 1`의 리비전 키셋·공백 탐지와
  `STALE` 제외, 현재 단건의 동일 검증자 `304`와 후속 리비전 뒤 이전 검증자 `200`·새
  `ETag`, 범위 격리와 대표 입력 오류를 확인

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.edition is idempotent immutable generated and reproducible after rebuild'`:
  성공. PostgreSQL 18.4 Testcontainers에서 재구축 뒤 불변 단건 에디션의 `304`, 전역
  최신 세대 변경 뒤 이전 검증자에 대한 `200`과 새 본문, 같은 주간 최신의 `304`를 확인

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.edition is idempotent immutable generated and reproducible after rebuild' --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.serializes concurrent duplicate ingestion edition generation and rebuild'`:
  성공. PostgreSQL 18.4 Testcontainers에서 재구축 중 데이터베이스 제약 위반 뒤
  이전 관심 항목 수가 복원되고 기존 에디션이 재사용되는 원자적 롤백, 재구축의 전역
  배타 잠금 동안 지원 이벤트 수신의 투영 단계가 대기하고 종료 뒤 두 작업이 순서대로
  성공하는 동작을 확인

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.health exposes aggregate status without deployment probes'`:
  성공, PostgreSQL 18.4 Testcontainers에서 `/actuator/health`의 `200`·`UP`, 상세 비노출,
  탐색 페이지와 대표 probe 경로의 `404`를 확인
- `./gradlew --no-daemon clean test :bootstrap:bootJar`: 성공. 다섯 모듈의 전체 테스트,
  PostgreSQL Testcontainers 빈 데이터베이스의 Flyway V1~V7 적용, 대표 V2 데이터의
  V3~V7 업그레이드와
  `bootstrap-0.1.0-SNAPSHOT.jar` 생성 확인
  - 최소 aggregate health의 `UP`·상세 비노출과 탐색 페이지·대표 probe 경로 미노출
  - 수신의 중복/충돌/미지원/오래된 리비전/리비전 공백, 이벤트별 충돌 증거 상한과
    최초 수신 증거의 읽기 전용 조회·재구축 뒤 불변성·fingerprint 비노출
  - 복합 정체성의 현재 관심 항목 단건 조회, 재구축 뒤 리비전·공백 근거와 후속
    `RESOLVED` 갱신, 동일 현재 표현의 `304`와 이전 검증자에 대한 새 `200` 응답, 범위
    격리와 입력 검증
  - 현재 `ACTIVE` 항목의 복합 정체성 순서, 배타 키셋 다음 페이지, 다른 작업공간의 빈
    범위, 반쪽 커서 거부와 후속 `RESOLVED` 항목 제외
  - 작업공간·시즌별 이상 수신 증거의 선정, 충돌 뒤 `APPLIED` 포함, 수신 순서 내림차순
    배타 페이지와 다른 시즌·작업공간의 빈 범위
  - 에디션 멱등성·불변성·`generation`/최신 조회, 실제 동일 전체 스냅샷
    `A → B → A`의 새 역사적 세대, 이력 키셋 페이지·범위 격리·항목 수, 재구축 재현성과
    마이크로초 구간 경계
  - 신규 에디션의 집계 리비전·리비전 공백 근거 고정, 근거만 다른 새 세대와 즉시 멱등
    재사용, 정방향·역방향 비교의 `1`·`false` ↔ `3`·`true`, 재구축 뒤 비교 불변성
  - 작업공간·시즌 전역 최신과 주간 범위 최신의 분리, 작업공간·시즌·주간·시간대 격리,
    월요일·IANA 시간대 입력 오류와 미존재 범위 `404`
  - 불변 단건 에디션의 일치 검증자 `304`, 전역 최신 세대 변경 뒤 이전 검증자에 대한
    `200`과 새 본문, 같은 주간 최신의 일치 검증자 `304`
  - 엄격한 HTTP 표현 검증과 대표 `400`·`404`의 표준 `ProblemDetail`
  - 동시 중복 수신과 동시 에디션 생성
  - 재구축 강제 실패의 원자적 롤백과 재구축·지원 이벤트 수신 잠금 직렬화
  - 불변 에디션의 추가·제거·변경, 기준·대상 순서, 역방향 비교와 재구축 뒤 동일 결과
- `docker compose config`: 성공. `postgres:18.4-alpine`, 상태 확인과 이름 있는 볼륨의
  `/var/lib/postgresql` 마운트 구문 확인
- `BRIEF_DB_PORT=55432 docker compose -p baton-brief-smoke -f compose.yml up -d --wait`:
  격리된 PostgreSQL 18.4 컨테이너가 정상 상태로 기동됨
- `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/baton_brief java -jar bootstrap/build/libs/bootstrap-0.1.0-SNAPSHOT.jar --server.port=18080`:
  Java 21.0.10에서 실제 웹 모드로 기동. Tomcat 11.0.22가 18080 포트를 열고 Flyway가 빈
  스키마에 V1~V6 여섯 마이그레이션을 적용해 v6에 도달함
- `curl --fail-with-body --silent --show-error --include http://127.0.0.1:18080/actuator/health`:
  `200 OK`, `Content-Type: application/vnd.spring-boot.actuator.v3+json`, 본문
  `{"status":"UP"}` 확인

실제 로컬 소켓을 통한 패키지 JAR의 Tomcat·Flyway·DataSource·aggregate health 결합은
확인했다. 제품 HTTP 계약과 영속성 동작은 위 PostgreSQL Testcontainers·MockMvc 통합
시나리오가 담당하므로 같은 제품 시나리오를 네트워크로 반복하지 않았다. BATON 생산자와의
외부 종단 간 연동, 인증·인가와 운영 배포는 검증 범위가 아니다.

검증 뒤 `baton-brief-smoke` 애플리케이션 프로세스를 정상 종료하고, 이번 검증에서 만든
Compose 컨테이너·네트워크·이름 있는 볼륨을 모두 제거했다.

## 현재 제한

- 로컬 MVP만 구현했다. 운영 준비와 배포 검증은 완료하지 않았다.
- BATON, WATCH, RELAY, GO 생산자와의 종단 간 연동은 없다. BRIEF 이벤트 v2 소비는
  구현했고 RC 계약 팩과 BATON 실제 직렬화, 생산자 신호 스트림·불변 outbox·설정형 시간
  재조정도 검증했지만 원본 변경 자동 재조정, outbox 송신기와 종단 간 전달은 아직 없다.
- 브로커, 스케줄러, 외부 시스템 연동 어댑터와 운영 인증·인가 계약은 없다.
- 수신 기록·충돌 증거·에디션의 삭제·압축·외부 보관은 구현하지 않았다. 숫자 보존 기간,
  용량 상한, 재구축 SLO·잠금 제한 시간, 체크포인트와 운영 복구 목표도 미결정이다.
- 특정 JDK 공급자, 실행 컨테이너 이미지와 운영 배포 구성은 없다.
- GitHub Actions와 릴리스 정책은 아직 없다.
