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

## 구현한 후속 비교 조회

- `GET /api/v1/editions/{targetEditionId}/changes?fromEditionId={baseEditionId}`가 저장된 두
  불변 스냅샷의 `added`, `removed`, `changed.before`·`changed.after`를 반환한다.
- 비교 키는 `(reasonCode, sourceReference)`이며 `added`·`changed`는 대상 순서,
  `removed`는 기준 순서를 유지한다.
- 비교는 저장된 스냅샷만 읽으므로 이후 투영 변경이나 재구축에도 기존 비교 결과가
  달라지지 않는다.

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

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.edition is idempotent immutable generated and reproducible after rebuild' --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.serializes concurrent duplicate ingestion edition generation and rebuild'`:
  성공. PostgreSQL 18.4 Testcontainers에서 재구축 중 데이터베이스 제약 위반 뒤
  이전 관심 항목 수가 복원되고 기존 에디션이 재사용되는 원자적 롤백, 재구축의 전역
  배타 잠금 동안 지원 이벤트 수신의 투영 단계가 대기하고 종료 뒤 두 작업이 순서대로
  성공하는 동작을 확인

- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.ingestion distinguishes duplicate conflict unsupported stale and gap'`:
  성공, PostgreSQL 18.4 Testcontainers에서 최초 수신 결과, 동일 재전달과 충돌 뒤 불변성,
  fingerprint 비노출, 재구축 뒤 동일 응답, `UNSUPPORTED`와 미존재 `404 ProblemDetail`을
  확인
- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.health exposes aggregate status without deployment probes'`:
  성공, PostgreSQL 18.4 Testcontainers에서 `/actuator/health`의 `200`·`UP`, 상세 비노출,
  탐색 페이지와 대표 probe 경로의 `404`를 확인
- `./gradlew --no-daemon clean test :bootstrap:bootJar`: 성공. 다섯 모듈의 전체 테스트,
  PostgreSQL 통합 시나리오와 `bootstrap-0.1.0-SNAPSHOT.jar` 생성 확인
  - 최소 aggregate health의 `UP`·상세 비노출과 탐색 페이지·대표 probe 경로 미노출
  - 수신의 중복/충돌/미지원/오래된 리비전/리비전 공백, 이벤트별 충돌 증거 상한과
    최초 수신 증거의 읽기 전용 조회·재구축 뒤 불변성·fingerprint 비노출
  - 에디션 멱등성·불변성·`generation`/최신 조회, `A → B → A` 상태 회귀, 이력 키셋
    페이지·범위 격리·항목 수, 재구축 재현성과 마이크로초 구간 경계
  - 엄격한 HTTP 표현 검증과 대표 `400`·`404`의 표준 `ProblemDetail`
  - 동시 중복 수신과 동시 에디션 생성
  - 재구축 강제 실패의 원자적 롤백과 재구축·지원 이벤트 수신 잠금 직렬화
  - 불변 에디션의 추가·제거·변경, 기준·대상 순서, 역방향 비교와 재구축 뒤 동일 결과
- `./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefMvpIntegrationTest.edition changes compare immutable snapshots and reject invalid scopes'`:
  성공, PostgreSQL 18.4 Testcontainers에서 Flyway V1·V2를 적용하고 비교 분류·순서·역방향,
  재구축 뒤 불변성, 범위 불일치 `400`과 미존재 `404`를 확인
- `docker compose config`: 성공. `postgres:18.4-alpine`, 상태 확인과 이름 있는 볼륨의
  `/var/lib/postgresql` 마운트 구문 확인
- `java -jar bootstrap/build/libs/bootstrap-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none`:
  Java 21.0.10에서 Compose PostgreSQL 18.4에 연결해 성공. Flyway가 V1 마이그레이션을
  검증·적용했고 Spring 컨텍스트가 시작됨

위 비웹 실행 JAR 점검은 V2 추가 전 V1 기준이다. V2는 PostgreSQL 18.4 통합 테스트에서
실제 적용했고 현재 실행 JAR 생성까지 확인했으며, 같은 실행 환경 기동은 중복 수행하지
않았다.

실행 점검은 비웹 모드이므로 실제 수신 포트를 통한 HTTP 네트워크 동작은 입증하지 않는다.
HTTP 경로와 영속성 동작은 위 PostgreSQL Testcontainers·MockMvc 통합 시나리오가 입증한다.
BATON 생산자와의 외부 종단 간 연동, 인증·인가, 운영 배포는
검증 범위가 아니다.

검증 뒤 Compose 테스트 컨테이너, 네트워크와 이름 있는 볼륨
`baton-brief_brief-postgres-data`를 모두 제거했다.

## 현재 제한

- 로컬 MVP만 구현했다. 운영 준비와 배포 검증은 완료하지 않았다.
- BATON, WATCH, RELAY, GO 생산자와의 종단 간 연동은 없다.
- 브로커, 스케줄러, 외부 시스템 연동 어댑터와 운영 인증·인가 계약은 없다.
- 수신 기록·충돌 증거·에디션의 삭제·압축·외부 보관은 구현하지 않았다. 숫자 보존 기간,
  용량 상한, 재구축 SLO·잠금 제한 시간, 체크포인트와 운영 복구 목표도 미결정이다.
- 특정 JDK 공급자, 실행 컨테이너 이미지와 운영 배포 구성은 없다.
- GitHub Actions와 릴리스 정책은 아직 없다.
