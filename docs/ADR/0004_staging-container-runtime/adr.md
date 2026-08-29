# ADR-0004: BRIEF 스테이징 컨테이너 실행 경계

- 상태: 채택됨
- 결정일: 2026-08-27
- 수정일: 2026-08-29

## 배경

BRIEF는 실행 JAR과 로컬 PostgreSQL 조립을 제공하지만 운영과 유사한 실행 컨테이너가
없었다. BATON 생산자는 loopback 밖에서 HTTPS origin만 허용하므로 실제 스테이징 전달을
검증하려면 먼저 BRIEF 애플리케이션과 데이터베이스를 비밀 값 노출 없이 기동할 수 있어야
한다. HTTPS 앞단 선택은 이 내부 실행 경계를 고정한 뒤 후속 결정으로 분리한다.

## 결정

- `Dockerfile`은 Gradle wrapper로 `:bootstrap:bootJar`를 빌드하고 JRE만 포함한 실행
  이미지에 결과를 복사한다.
- BuildKit이 해석하는 Dockerfile frontend도 `docker/dockerfile:1.7`의 다중 플랫폼 digest로
  고정한다.
- 빌드와 실행 이미지는 Eclipse Temurin 21.0.11+10 Alpine 이미지를 digest로 고정한다.
  이는 ADR-0002의 Java 21 기준을 스테이징 컨테이너에서 재현하기 위한 선택이며 다른
  배포 환경의 JDK 공급자를 강제하지 않는다.
- 실행 컨테이너는 UID/GID `10001`, 읽기 전용 루트 파일시스템, `/tmp` tmpfs와 모든 Linux
  capability 제거를 사용한다.
- `compose.staging.yml`의 기본 profile은 BRIEF와 PostgreSQL 18.6을 조립한다. PostgreSQL은 내부
  데이터 네트워크에서만 실행하고 호스트 포트를 공개하지 않는다.
- BRIEF 프로세스는 컨테이너 내부 네트워크 요청을 받도록 `0.0.0.0:8080`에서 실행하되,
  HTTP 포트는 호스트의 `127.0.0.1`에만 게시하고 주소를 설정으로 바꾸지 못하게 한다. 후속
  HTTPS 앞단과 인증서는 배포 환경이 소유하고 애플리케이션 이미지에 TLS 종단을 넣지
  않는다.
- 후속 ADR-0005의 선택적인 Caddy profile도 BRIEF의 loopback 호스트 게시 경계를 바꾸지
  않는다.
- 데이터베이스 비밀번호와 현재·직전 이벤트 수신 Bearer는 저장소 밖 파일을 Compose
  secrets로 마운트하고 Spring Boot `configtree:`로 읽는다. 별도 비밀 로더나 환경변수
  복사 스크립트를 만들지 않는다.
- 스테이징 조립에서는 이벤트 수신 Bearer 인증을 항상 활성화한다.
- 컨테이너 상태 확인은 기존 `/actuator/health`를 사용한다. 커스텀 상태 확인 경로나
  상세 응답을 추가하지 않는다.

## 결과

### 장점

- 로컬 실행 JAR과 다른 컨테이너 파일시스템·사용자·비밀 마운트 경계를 실제로 확인할 수
  있다.
- 데이터베이스와 Bearer 원문이 Compose 환경 변수나 저장소 파일에 들어가지 않는다.
- HTTPS 앞단과 독립적으로 애플리케이션 실행 경계를 검증할 수 있다.
- BATON의 HTTPS origin 정책과 BRIEF 내부 HTTP 실행 책임이 분리된다.

### 비용과 한계

- 이미지 digest와 JDK 패치 버전을 의도적으로 갱신해야 한다.
- 단일 PostgreSQL 볼륨만 제공하며 백업, 복구, 고가용성과 용량 정책은 포함하지 않는다.
- 기본 loopback 조립만으로는 다른 호스트의 BATON이 접근할 수 없다. 실제 전달에는
  ADR-0005의 Caddy profile과 공인 DNS·인증서 환경이 필요하다.
- 파일 기반 secret의 생성·배포·회수와 호스트 접근 통제는 운영 환경이 담당한다.

## 대안

- 애플리케이션에서 HTTPS 직접 종단: 인증서 생명주기와 신뢰 저장소를 제품 코드에
  결합하므로 채택하지 않았다.
- BRIEF와 PostgreSQL 포트를 모두 공개: 데이터베이스 노출 위험이 있고 reverse proxy
  우회 경로가 생기므로 채택하지 않았다.
- Bearer와 DB 비밀번호를 일반 환경 변수로 전달: Compose 조립과 프로세스 환경에 원문이
  남으므로 채택하지 않았다.
- Kubernetes·관리형 PostgreSQL 즉시 채택: 현재 스테이징 규모와 운영 환경이 정해지지 않아
  보류한다.

## 관련 문서

- [기술 스택과 모듈 경계](../0002_technology-stack/adr.md)
- [BATON 이벤트 전용 Bearer 인증](../0003_baton-event-authentication/adr.md)
- [스테이징 실행 계약](../../PRD/0021_staging-runtime-boundary/spec.md)
- [Caddy 이벤트 수신 앞단](../0005_caddy-event-ingress/adr.md)
