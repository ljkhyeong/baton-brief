# PRD-0022: BRIEF HTTPS 이벤트 수신 경계

- 상태: 채택됨
- 결정일: 2026-08-27
- 범위: 스테이징 Caddy가 공개 HTTPS에서 BRIEF 이벤트 수신 한 경로만 전달하는 경계

## 목적

PRD-0020은 BATON 전용 Bearer를, PRD-0021은 내부 HTTP 애플리케이션과 PostgreSQL의
컨테이너 경계를 제공한다. BATON이 요구하는 loopback 밖 HTTPS origin을 실제 배포 형태로
준비하되, 아직 권한 모델이 없는 조회·에디션·재구축·상태 확인 API를 함께 공개하지 않는다.

## 외부 노출 계약

- Caddy가 외부에서 허용하는 요청은 정확히 `POST /api/v1/events` 하나다.
- 허용한 요청은 외부 송신 경로가 없는 내부 `proxy` 네트워크의 `brief:8080`으로 전달한다.
- 그 밖의 메서드와 경로는 Caddy에서 빈 본문의 `404`로 끝내며 BRIEF에 전달하지 않는다.
- `/actuator/health`는 BRIEF 컨테이너 healthcheck에만 사용하고 외부 HTTPS에서 노출하지
  않는다.
- BRIEF HTTP 포트의 기본 `127.0.0.1` 호스트 게시는 유지한다. 다른 호스트는 Caddy HTTPS를
  거치며 PostgreSQL은 계속 호스트 포트를 열지 않는다.

이 계약은 이벤트 수신 외 API의 인증·인가를 결정한 것이 아니다. 운영자·사용자 조회를
외부에 제공하려면 별도 권한과 노출 계약을 먼저 채택해야 한다.

## TLS와 호스트

- `compose.staging.yml`의 `https` profile은 digest로 고정한 Caddy 2.11.4 Alpine 이미지를
  사용한다.
- `BRIEF_STAGING_HOST`는 스테이징 DNS에 등록된 호스트 이름으로 설정한다. 실제 호스트를
  넣지 않으면 기본 `brief.invalid`가 사용되어 공인 인증서 발급에 성공하지 않는다.
- Caddy 자동 HTTPS가 공인 호스트의 ACME 인증서 발급·갱신과 HTTP→HTTPS 전환을 담당한다.
- 인증서와 Caddy 설정 상태는 별도 이름 있는 볼륨에 보존한다.
- BRIEF 애플리케이션 이미지에는 인증서, private key, TLS 서버와 자체 신뢰 저장소 코드를
  추가하지 않는다.

## 네트워크와 실행 권한

- PostgreSQL과 BRIEF는 외부 송신 경로가 없는 내부 `data` 네트워크를 공유한다.
- BRIEF와 Caddy는 외부 송신 경로가 없는 내부 `proxy` 네트워크만 공유한다.
- Caddy만 별도 `egress` 네트워크에 연결해 ACME와 외부 HTTPS 통신을 담당한다. BRIEF와
  PostgreSQL은 `egress`에 연결하지 않는다.
- Caddy는 Linux capability를 모두 제거한 뒤 80·443 포트 바인딩에 필요한
  `NET_BIND_SERVICE`만 추가한다. 읽기 전용 루트 파일시스템과 `no-new-privileges`도
  유지한다.
- 현재 공식 Caddy 이미지는 root 사용자로 실행한다. 이름 있는 인증서 볼륨의 소유권
  초기화 계약 없이 임의의 init 컨테이너나 별도 권한 변경 스크립트를 추가하지 않는다.

## 인증과 헤더

- Caddy는 `Authorization`을 해석하거나 Bearer를 다시 구현하지 않고 원문 헤더를 BRIEF에
  전달한다.
- 인증 성공·실패와 현재·직전 token 수동 교체는 PRD-0020의 Spring Security가 계속
  소유한다.
- 외부에서 받은 `Forwarded`, `X-Forwarded-*`와 `Proxy-Authorization`은 upstream 전달 전에
  제거하고 Caddy가 `X-Forwarded-Host`, `X-Forwarded-Port=443`,
  `X-Forwarded-Proto=https`를 설정한다.
- 응답의 `Server` 헤더는 제거하고 이벤트 응답에는 `Cache-Control: no-store`를 적용한다.
- Caddy 접근 로그에서 `Authorization`과 `Proxy-Authorization` 헤더를 삭제한다. token,
  이벤트 원문이나 다른 비밀을 로그 필드로 추가하지 않는다.

## 실행 방법

`.env.staging`에 실제 호스트와 필요하면 공개 포트 매핑을 설정한 뒤 profile을 명시적으로
활성화한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml --profile https config --quiet
docker compose --env-file .env.staging -f compose.staging.yml --profile https up --build -d --wait
```

기본 profile은 기존 loopback 호스트 게시 스테이징 조립을 유지한다. 공개 호스트를 준비하지
않은 개발 환경에서 `https` profile을 자동 활성화하지 않는다.

## 수용 기준

- Compose와 Caddy 설정이 유효하고 PostgreSQL, BRIEF와 Caddy가 함께 정상 상태로 기동한다.
- 신뢰한 HTTPS 연결에서 Bearer 없는 이벤트 요청은 `401`이고 정상 현재 Bearer는 기존
  `202 APPLIED` 결과를 유지한다.
- 외부 `/actuator/health`를 포함한 이벤트 수신 외 경로는 `404`이며 BRIEF에 전달되지 않는다.
- Caddy 접근 로그에는 Authorization 헤더와 token 원문이 없다.
- BRIEF는 내부 `data`·`proxy`에만, Caddy는 내부 `proxy`와 외부 송신용 `egress`에만
  연결된다. `data`와 `proxy`는 Docker 내부 네트워크다.
- Caddy 컨테이너는 읽기 전용 루트 파일시스템과 `no-new-privileges`를 사용하고, 모든
  capability를 제거한 뒤 `NET_BIND_SERVICE`만 보유한다.
- 검증용 컨테이너·네트워크·볼륨, 내부 CA와 임시 비밀 파일은 검증 뒤 제거한다.

## 비목표

- 실제 공인 DNS 연결, 방화벽 변경과 ACME 인증서 발급 성공
- 실제 BATON 스테이징 호스트에서 Caddy HTTPS origin으로 보내는 종단 간 전달
- 이벤트 수신 외 API의 외부 공개·인증·인가
- WAF, CDN, rate limiting, mTLS와 OAuth2/JWT
- Caddy 데이터 볼륨의 백업·복구와 다중 인스턴스 고가용성
- token 자동 발급·회전과 비밀 관리 제품 선택

## 관련 문서

- [BATON 이벤트 수신 인증 계약](../0020_baton-event-authentication/spec.md)
- [스테이징 실행 계약](../0021_staging-runtime-boundary/spec.md)
- [Caddy 이벤트 수신 앞단 결정](../../ADR/0005_caddy-event-ingress/adr.md)
- [서비스 간 이벤트 인증 결정](../../ADR/0003_baton-event-authentication/adr.md)
