# 인수인계

내부 HTTP 이벤트 수신, 투영/재구축, 불변 주간 에디션, 에디션 이력 조회와 표준 요청 오류
응답 로컬 MVP 구현을 완료했다.
PostgreSQL 통합 테스트, 저장소 전체 빌드와 Java 21 패키지 실행 점검까지 통과했다.

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

## 현재 검증

- `./gradlew --no-daemon :bootstrap:test --tests com.personal.baton.brief.BriefMvpIntegrationTest`:
  성공, PostgreSQL 18.4 Testcontainers에서 Flyway V1·V2를 적용하고 통합 시나리오 4개 통과
  - 수신의 중복/충돌/미지원/오래된 리비전/리비전 공백과 이벤트별 충돌 증거 상한
  - 에디션 멱등성·불변성·`generation`/최신 조회, `A → B → A` 상태 회귀, 이력 키셋
    페이지·범위 격리·항목 수, 재구축 재현성과 마이크로초 구간 경계
  - 엄격한 HTTP 표현 검증과 대표 `400`·`404`의 표준 `ProblemDetail`
  - 동시 중복 수신과 동시 에디션 생성
- `docker compose config`: 성공. `postgres:18.4-alpine`, 상태 확인과 이름 있는 볼륨의
  `/var/lib/postgresql` 마운트 구문 확인
- `./gradlew --no-daemon clean test :bootstrap:bootJar`: 성공. 다섯 모듈의 전체 테스트와
  `bootstrap-0.1.0-SNAPSHOT.jar` 생성 확인
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
- 특정 JDK 공급자, 실행 컨테이너 이미지와 운영 배포 구성은 없다.
- GitHub Actions와 릴리스 정책은 아직 없다.
