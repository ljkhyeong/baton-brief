# BATON BRIEF

BATON BRIEF는 BATON 생태계의 운영 사실을 설명 가능한 관심 항목으로 투영하고, 일정 시점의
불변 운영 브리프로 고정하는 독립 읽기 모델 서비스다.

## 현재 상태

Kotlin/JDK 21, Spring Boot 4.1과 PostgreSQL 18.4 기반의 로컬 MVP를 구현했다.

- 버전이 있는 BATON 이벤트 v1·v2의 멱등 수신, 충돌 증거와 집계 리비전 처리
- 현재 관심 항목 투영, 상태별 키셋 조회, 상태 전이 증거와 원자적 전체 재구축
- 월요일 시작 IANA 시간대 주간의 불변 에디션 생성·조회·이력·비교·조건부 조회
- RFC 9457 `ProblemDetail`, Spring Boot Actuator aggregate health
- BATON 전용 Bearer와 파일 기반 비밀을 사용하는 스테이징 컨테이너
- 이벤트 수신 한 경로만 허용하는 선택적 Caddy HTTPS 앞단

현재 검증 근거와 남은 작업은 [HANDOFF.md](HANDOFF.md), 제품·구조 문서의 전체 지도는
[문서 색인](docs/README.md)을 기준으로 확인한다.

## 동작 구조

```text
BATON 도메인 이벤트 ──> BRIEF 수신 기록 ──> AttentionItem 현재 투영
                                                │
                                                └─> 불변 BriefEdition

설계한 다음 연결(미구현):
BATON UI ──> BATON 백엔드 권한 판정 ──> BRIEF 내부 조회
BATON 백엔드 대상·시점 결정 ──────────> BRIEF 에디션 생성 명령
```

BRIEF는 WATCH·RELAY·GO의 데이터베이스를 직접 읽지 않는다. 운영 사실과 최종 판정은 원본
서비스가 소유하고 BRIEF는 커밋 후 전달된 이벤트만 소비한다. 사용자 조회와 에디션 생성
실행의 다음 경계는 [PRD-0023](docs/PRD/0023_baton-mediated-brief-query/spec.md)과
[PRD-0024](docs/PRD/0024_baton-driven-edition-generation/spec.md)를 따른다.

## 기능 지도

### 이벤트와 수신 증거

- `POST /api/v1/events`는 이벤트 식별자·버전·본문 지문으로 동일 재전달과 충돌을 구분한다.
- 집계 리비전으로 오래된 전달과 공백을 판정하며 수신과 현재 투영을 한 트랜잭션에서 처리한다.
- 최초 수신 결과 단건과 작업공간·시즌별 이상 수신 증거를 읽기 전용으로 조회한다.
- 대체 보존 계약 전에는 `UNSUPPORTED`를 포함한 수신 기록과 이벤트별 최초 충돌 한 건을
  삭제·압축하지 않는다.

### 현재 관심 항목

- `(workspaceId, seasonId, eventType, sourceReference)`를 복합 정체성으로 사용한다.
- 현재 단건과 `ACTIVE`·`RESOLVED` 상태별 키셋 목록을 조회한다.
- 실제 적용된 상태 전이를 집계 리비전 역순으로 조회하고 전이 시점의 공백 탐지와 현재의
  누적 공백을 구분한다.
- 단건 응답은 현재 규칙 버전과 마지막 적용 리비전에 결합한 `ETag`를 제공한다.

### 불변 에디션

- 월요일 시작 IANA 시간대 주간과 로컬 수신 `sourceCursor`를 기준으로 결정적으로 생성한다.
- 같은 범위의 직전 상태는 멱등하게 재사용하고 `A → B → A`처럼 과거 상태로 돌아오면 새
  `generation`으로 기록한다.
- 전역 최신·주간 범위 최신·단건·이력·비교 조회를 제공한다.
- 생성 당시 항목과 집계 리비전·리비전 공백을 함께 고정하며 기존 에디션을 수정하지 않는다.
- 전체 에디션 응답은 선택된 불변 에디션을 나타내는 `ETag`를 제공한다.

### 실행 경계

- `/actuator/health`는 Spring Boot 표준 aggregate 상태와 자동 구성된 DB contributor만
  사용한다.
- 로컬 실행의 이벤트 수신 인증은 기본 비활성이며, 스테이징은 BATON 전용 Bearer를 파일로
  주입한다.
- Caddy는 외부에서 정확한 `POST /api/v1/events`만 전달하고 다른 경로는 `404`로 종료한다.
- BRIEF는 내부 `data`·`proxy` 네트워크에만 참여하고 외부 송신용 `egress`에는 Caddy만
  연결된다.

정확한 경로, 필드, 상태와 비목표는 [문서 색인](docs/README.md)의 해당 PRD를 따른다.

## 서비스 경계

BRIEF가 소유한다.

- 멱등 수신 기록과 제한된 최초 충돌 증거
- 작업공간·시즌별 현재 관심 항목 투영
- 결정적 생성 커서와 불변 에디션
- 재구축과 조회에 필요한 로컬 운영 증거

BRIEF가 소유하지 않는다.

- 팀·시즌·역할·루틴·결정·인수인계와 최종 권한: BATON
- URL 점검과 상태 원본: BATON WATCH
- 구독·제공자 전달과 재시도 생명주기: BATON RELAY
- 링크 코드·만료·폐기·리디렉션: BATON GO
- AI가 생성한 권위 있는 운영 판정

## 이벤트 v2 계약 팩

[이벤트 계약 팩](contracts/README.md)은 BATON 생산자가 사용할 v2 요청 JSON Schema와
다섯 연속성 신호의 예시를 제공한다. 현재 버전 `2.0.0-rc.2`는 BRIEF 소비자 입력 계약을
엄격하게 한 사전 버전이다. 생산자 호환성의 현재 검증 범위는 [계약 팩 안내](contracts/README.md)와
[HANDOFF.md](HANDOFF.md)를 따른다.

```shell
./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest'
./gradlew --no-daemon contractsZip
```

생성 파일은 `build/distributions/baton-brief-contracts-2.0.0-rc.2.zip`이다.

## 기술 스택

[ADR-0002](docs/ADR/0002_technology-stack/adr.md)를 기술 기준으로 사용한다.

- Kotlin/JVM 2.3.21, Java 21 도구 체인과 JDK 21 실행 환경
- Spring Boot/BOM 4.1.0, Gradle wrapper 9.2.1과 Kotlin DSL
- PostgreSQL 18.4, Spring JDBC `JdbcClient`, Flyway, JPA 미사용
- `domain`, `application`, `adapter-in-web`, `adapter-out-persistence`, `bootstrap`의 다섯
  Gradle 모듈

의존은 `bootstrap`·어댑터에서 `application`, 다시 `domain`으로만 향한다.

## 로컬 실행

PostgreSQL을 기동한 뒤 애플리케이션을 실행한다.

```shell
docker compose up -d --wait postgres
./gradlew :bootstrap:bootRun
```

애플리케이션은 기본 `127.0.0.1:8080`에서 요청을 받고 `compose.yml`은 PostgreSQL 포트를
호스트의 `127.0.0.1`에만 게시한다. 기본 데이터 원본은
`jdbc:postgresql://localhost:5432/baton_brief`이고 사용자와 비밀번호는 `brief`다. 다른
환경에서는 Spring Boot 표준 환경변수
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`와
`SPRING_FLYWAY_ENABLED`로 덮어쓴다.

로컬 이벤트 수신 인증을 활성화하려면 다음 값을 함께 설정한다.

```text
BRIEF_EVENT_RECEIVER_AUTHENTICATION_REQUIRED=true
BRIEF_EVENT_RECEIVER_BEARER_TOKEN=<32~200자의 URL-safe ASCII 값>
```

보호 범위는 `POST /api/v1/events`뿐이다. 중단 없이 값을 바꿀 때는 새 값을 현재 token에,
기존 값을 `BRIEF_EVENT_RECEIVER_PREVIOUS_BEARER_TOKEN`에 한 번만 함께 배포하고 BATON 전환
뒤 직전 값을 제거한다.

BATON 백엔드 경유 조회·생성 연결은 이벤트 token을 재사용하지 않는다. 같은 호스트에서
연결할 때 운영자가 내부 네트워크를 한 번 만들고 서비스 API override를 함께 적용한다.

```shell
docker network create --internal baton-brief-private
docker network inspect --format '{{.Internal}}' baton-brief-private
docker compose --env-file .env.staging \
  -f compose.staging.yml -f compose.service-api.yml config --quiet
docker compose --env-file .env.staging \
  -f compose.staging.yml -f compose.service-api.yml up --build -d --wait
```

`docker network inspect` 결과는 `true`여야 한다. 연결된 BATON `app`은
`http://brief:8080`을 사용하고 BRIEF Caddy의 공개 이벤트 허용 목록은 바꾸지 않는다.

## 스테이징 실행

`.env.staging.example`을 추적되지 않는 `.env.staging`으로 복사하고 데이터베이스·Bearer
파일의 실제 절대 경로를 지정한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml config --quiet
docker compose --env-file .env.staging -f compose.staging.yml up --build -d --wait
```

기본 조립은 PostgreSQL의 호스트 포트를 열지 않고 BRIEF HTTP를 `127.0.0.1:8080`에만
바인딩한다. 호스트 주소는 Compose에서 고정되며 포트만 바꿀 수 있다. 공개 호스트가 준비된
환경에서만 Caddy profile을 명시적으로 활성화한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml --profile https config --quiet
docker compose --env-file .env.staging -f compose.staging.yml --profile https up --build -d --wait
```

이 조립은 실제 DNS·방화벽, 공인 인증서 발급, 백업·복구, 이미지 registry와 BATON 원격
전달을 대신하지 않는다.

## 문서

- [문서 색인](docs/README.md)
- [현재 검증과 다음 작업](HANDOFF.md)
- [이벤트 v2 계약 팩](contracts/README.md)
- [개발 작업 규칙](AGENTS.md)

## 라이선스

아직 라이선스를 채택하지 않았다. 라이선스가 추가되기 전에는 별도의 사용 허가가 부여되지
않는다.
