# PRD-0021: BRIEF 스테이징 실행 계약

- 상태: 채택됨
- 결정일: 2026-08-27
- 구현 상태: 로컬 스테이징 컨테이너 조립 및 실행 검증 완료
- 범위: BRIEF 애플리케이션·PostgreSQL과 선택적인 HTTPS 앞단의 최소 스테이징 실행 경계

## 목적

실제 BATON→BRIEF 공개 HTTPS 전달을 검증하기 전에 BRIEF 실행 이미지, PostgreSQL 연결과
파일 기반 비밀 주입을 운영과 유사한 컨테이너 경계에서 재현한다. 기본 조립은 loopback
HTTP origin까지만 제공하고 PRD-0022의 명시적인 profile로 이벤트 수신 전용 HTTPS 앞단을
추가한다.

## 실행 조립

- `Dockerfile`은 저장소의 Gradle wrapper로 `:bootstrap:bootJar`를 빌드한다.
- 실행 이미지는 Java 21 JRE, UID/GID `10001`, 읽기 전용 루트 파일시스템과 `/tmp`
  tmpfs를 사용한다.
- `compose.staging.yml`의 기본 조립은 `brief`와 `postgres` 두 서비스를 제공한다.
- PostgreSQL 18.4는 내부 `data` 네트워크와 이름 있는 볼륨만 사용하며 호스트 포트를
  열지 않는다.
- BRIEF 프로세스는 컨테이너 내부의 `0.0.0.0:8080`에서 요청을 받고 `data`와 `proxy`
  내부 네트워크에만 참여한다. 호스트에는 기본 `127.0.0.1:8080`으로만 게시한다.
- 기존 Spring Boot aggregate health인 `/actuator/health`가 애플리케이션과 필수
  데이터베이스 상태를 확인한다.
- 선택적인 `https` profile은 Caddy만 `proxy`와 외부 송신용 `egress` 네트워크에 연결하며
  외부에는 PRD-0022의 이벤트 수신 한 경로만 제공한다.

## 설정과 비밀

`.env.staging.example`은 다음 비밀 파일의 절대 경로와 공개 설정만 예시로 제공한다.

- `BRIEF_STAGING_DATABASE_PASSWORD_FILE`
- `BRIEF_STAGING_EVENT_RECEIVER_BEARER_TOKEN_FILE`
- `BRIEF_STAGING_EVENT_RECEIVER_PREVIOUS_BEARER_TOKEN_FILE`
- 데이터베이스 이름·사용자, 이미지 이름과 loopback 바인딩 포트
- `https` profile에서 사용할 공개 호스트 이름과 HTTP/HTTPS 포트 매핑

실제 비밀 파일은 저장소 밖에 두고 Git에 추가하지 않는다. Compose는 세 파일을
`/run/secrets`에 마운트하며 Spring Boot `configtree:`가 데이터베이스 비밀번호와 현재·
직전 Bearer 속성을 읽는다. 평상시 직전 token 파일은 비워 두고, PRD-0020의 수동 교체
구간에만 기존 값을 넣는다.

스테이징 조립은 `BRIEF_EVENT_RECEIVER_AUTHENTICATION_REQUIRED=true`를 고정한다. Bearer
원문을 Compose 일반 환경 변수, 이미지, 로그와 문서에 넣지 않는다.

## 실행 방법

운영자는 예시 파일을 추적되지 않는 `.env.staging`으로 복사한 뒤 실제 절대 경로와 포트를
설정한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml config --quiet
docker compose --env-file .env.staging -f compose.staging.yml up --build -d --wait
```

공개 호스트가 준비된 환경은 실제 `BRIEF_STAGING_HOST`를 설정하고 PRD-0022의 profile을
명시적으로 활성화한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml --profile https config --quiet
docker compose --env-file .env.staging -f compose.staging.yml --profile https up --build -d --wait
```

`BRIEF_STAGING_HTTP_BIND`를 외부 인터페이스로 바꾸는 것은 Caddy 우회 경로를 만들 수
있으므로 기본 loopback 값을 유지한다.

## 수용 기준

- 고정한 Java 21 이미지에서 실행 JAR을 빌드하고 컨테이너가 health 상태가 된다.
- PostgreSQL은 호스트 포트 없이 기동하고 Flyway V1~V7을 적용한다.
- BRIEF 실행 컨테이너는 UID/GID `10001`과 읽기 전용 루트 파일시스템을 사용한다.
- Bearer 없는 `POST /api/v1/events`는 `401`이고 파일에서 주입한 현재 Bearer로 기존 v2
  계약 예시를 수신할 수 있다.
- 데이터베이스 비밀번호와 Bearer 원문은 일반 Compose 환경 변수에 포함되지 않는다.
- 검증용 컨테이너·네트워크·볼륨과 임시 비밀은 실행 뒤 제거한다.

## 비목표

- 실제 공인 DNS, 방화벽과 ACME 인증서 발급 성공
- 실제 BATON 호스트에서 공개 HTTPS BRIEF origin으로 보내는 종단 간 검증
- 사용자·운영자 API 인증·인가와 외부 공개 정책
- PostgreSQL 백업·복구, 고가용성, 보존 기간과 용량 SLO
- Kubernetes, 관리형 데이터베이스, 이미지 registry와 CI 배포
- token 자동 발급·회전과 비밀 관리 제품 선택

## 관련 문서

- [BATON 이벤트 수신 인증 계약](../0020_baton-event-authentication/spec.md)
- [스테이징 컨테이너 실행 결정](../../ADR/0004_staging-container-runtime/adr.md)
- [HTTPS 이벤트 수신 계약](../0022_https-event-ingress/spec.md)
- [Caddy 이벤트 수신 앞단 결정](../../ADR/0005_caddy-event-ingress/adr.md)
- [기술 스택과 모듈 경계](../../ADR/0002_technology-stack/adr.md)
