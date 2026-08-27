# PRD-0021: BRIEF 스테이징 실행 계약

- 상태: 채택됨
- 결정일: 2026-08-27
- 구현 상태: 로컬 스테이징 컨테이너 조립 및 실행 검증 완료
- 범위: 공개 HTTPS 앞단에 배치할 BRIEF 애플리케이션과 PostgreSQL의 최소 실행 경계

## 목적

실제 BATON→BRIEF HTTPS 전달을 검증하기 전에 BRIEF 실행 이미지, PostgreSQL 연결과
파일 기반 비밀 주입을 운영과 유사한 컨테이너 경계에서 재현한다. 공개 DNS·인증서와
reverse proxy를 임의로 선택하지 않고 그 앞단이 연결할 loopback HTTP origin까지만
소유한다.

## 실행 조립

- `Dockerfile`은 저장소의 Gradle wrapper로 `:bootstrap:bootJar`를 빌드한다.
- 실행 이미지는 Java 21 JRE, UID/GID `10001`, 읽기 전용 루트 파일시스템과 `/tmp`
  tmpfs를 사용한다.
- `compose.staging.yml`은 `brief`와 `postgres` 두 서비스만 제공한다.
- PostgreSQL 18.4는 내부 `data` 네트워크와 이름 있는 볼륨만 사용하며 호스트 포트를
  열지 않는다.
- BRIEF는 `data`와 `edge` 네트워크에 참여하되 HTTP 포트를 기본
  `127.0.0.1:8080`에만 게시한다.
- 기존 Spring Boot aggregate health인 `/actuator/health`가 애플리케이션과 필수
  데이터베이스 상태를 확인한다.

## 설정과 비밀

`.env.staging.example`은 다음 비밀 파일의 절대 경로와 공개 설정만 예시로 제공한다.

- `BRIEF_STAGING_DATABASE_PASSWORD_FILE`
- `BRIEF_STAGING_EVENT_RECEIVER_BEARER_TOKEN_FILE`
- `BRIEF_STAGING_EVENT_RECEIVER_PREVIOUS_BEARER_TOKEN_FILE`
- 데이터베이스 이름·사용자, 이미지 이름과 loopback 바인딩 포트

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

외부 reverse proxy는 loopback HTTP origin으로 전달하고 공개 요청에는 HTTPS를 제공해야
한다. `BRIEF_STAGING_HTTP_BIND`를 외부 인터페이스로 바꾸는 것은 이번 계약의 기본 경계를
벗어나며 방화벽·인증서·proxy 우회 경로 검토 없이 수행하지 않는다.

## 수용 기준

- 고정한 Java 21 이미지에서 실행 JAR을 빌드하고 컨테이너가 health 상태가 된다.
- PostgreSQL은 호스트 포트 없이 기동하고 Flyway V1~V7을 적용한다.
- BRIEF 실행 컨테이너는 UID/GID `10001`과 읽기 전용 루트 파일시스템을 사용한다.
- Bearer 없는 `POST /api/v1/events`는 `401`이고 파일에서 주입한 현재 Bearer로 기존 v2
  계약 예시를 수신할 수 있다.
- 데이터베이스 비밀번호와 Bearer 원문은 일반 Compose 환경 변수에 포함되지 않는다.
- 검증용 컨테이너·네트워크·볼륨과 임시 비밀은 실행 뒤 제거한다.

## 비목표

- 공개 DNS, TLS 인증서 발급·회전, reverse proxy 제품과 방화벽 구성
- 실제 BATON 호스트에서 공개 HTTPS BRIEF origin으로 보내는 종단 간 검증
- 사용자·운영자 API 인증·인가와 외부 공개 정책
- PostgreSQL 백업·복구, 고가용성, 보존 기간과 용량 SLO
- Kubernetes, 관리형 데이터베이스, 이미지 registry와 CI 배포
- token 자동 발급·회전과 비밀 관리 제품 선택

## 현재 검증

로컬 Docker에서 스테이징 이미지를 빌드하고 PostgreSQL과 함께 기동했다.
`/actuator/health`의 `UP`, 무인증 이벤트 요청의 `401`, 계약 팩의 기존 v2 예시를 파일 기반
현재 Bearer로 수신한 `APPLIED`를 확인했다. 실행 컨테이너의 사용자 `10001:10001`과 읽기
전용 루트 파일시스템도 확인했으며 검증용 컨테이너·네트워크·볼륨과 임시 비밀 파일을
제거했다.

이 결과는 loopback HTTP 컨테이너 조립 근거이며 공개 HTTPS, 실제 인증서·신뢰 저장소와
BATON 스테이징 전달 완료를 뜻하지 않는다.

## 관련 문서

- [BATON 이벤트 수신 인증 계약](../0020_baton-event-authentication/spec.md)
- [스테이징 컨테이너 실행 결정](../../ADR/0004_staging-container-runtime/adr.md)
- [기술 스택과 모듈 경계](../../ADR/0002_technology-stack/adr.md)
