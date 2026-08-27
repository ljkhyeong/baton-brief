# ADR-0005: Caddy를 BRIEF 이벤트 수신 HTTPS 앞단으로 사용

- 상태: 채택됨
- 결정일: 2026-08-27

## 배경

ADR-0004의 BRIEF 컨테이너는 loopback HTTP만 제공하고 BATON 생산자는 loopback 밖 주소에
HTTPS origin을 요구한다. 애플리케이션에 TLS와 인증서 생명주기를 넣지 않으면서 실제
스테이징 형태를 구성해야 한다. 동시에 BRIEF 조회·에디션·재구축 API의 외부 권한은 아직
결정되지 않았으므로 reverse proxy가 모든 경로를 그대로 공개해서는 안 된다.

## 결정

- `compose.staging.yml`의 선택적인 `https` profile에서 Caddy 2.11.4 Alpine 이미지를
  digest로 고정한다.
- Caddy 자동 HTTPS를 사용하고 호스트는 `BRIEF_STAGING_HOST`로 주입한다. 공인 환경은
  ACME를 사용하고 로컬 `localhost` 검증은 Caddy 내부 CA를 사용한다.
- 외부에서는 정확한 `POST /api/v1/events`만 `brief:8080`으로 전달하고 다른 모든 요청은
  Caddy에서 `404`로 종료한다.
- Bearer 검증은 Spring Security가 계속 소유한다. Caddy는 Authorization 값을 해석하거나
  복제하지 않고 접근 로그에서 해당 헤더만 제거한다.
- 외부 전달 헤더를 신뢰하지 않고 Caddy가 HTTPS 기준 `X-Forwarded-*`를 다시 설정한다.
- BRIEF 애플리케이션의 loopback 바인딩, PostgreSQL 내부 네트워크와 파일 기반 Compose
  secrets를 유지한다.
- PostgreSQL·BRIEF의 `data`와 BRIEF·Caddy의 `proxy`를 외부 송신 경로가 없는 내부
  네트워크로 분리한다. ACME와 외부 HTTPS에 필요한 `egress`에는 Caddy만 연결한다.
- Caddy 루트 파일시스템은 읽기 전용으로 두고 인증서·설정 상태만 이름 있는 볼륨에 쓴다.
- Caddy의 Linux capability는 모두 제거하고 80·443 바인딩에 필요한
  `NET_BIND_SERVICE`만 추가한다. `no-new-privileges`도 유지한다.

## 결과

### 장점

- 애플리케이션 이미지와 Kotlin 코드에 TLS 서버, 인증서 갱신과 신뢰 우회 코드를 추가하지
  않는다.
- 공개 경로 허용 목록이 명시되어 권한 계약이 없는 내부 API의 우발적 노출을 막는다.
- BRIEF 컨테이너가 외부 송신 네트워크에 참여하지 않아 애플리케이션 침해 시 외부 연결
  경로를 줄인다.
- Caddy 자동 HTTPS와 저장 볼륨으로 공인 인증서 발급·갱신 책임을 배포 경계에 둔다.
- 기존 loopback 스테이징 실행은 profile을 활성화하지 않으면 그대로 유지된다.

### 비용과 한계

- DNS가 올바르게 연결되고 80/443 접근이 가능해야 공인 인증서 발급이 성공한다.
- Caddy 데이터 볼륨의 백업·복구와 다중 인스턴스 인증서 조정은 아직 결정하지 않았다.
- 로컬 내부 CA 검증만으로 공인 ACME 발급과 실제 BATON 원격 전달을 입증할 수 없다.
- 이벤트 수신 외 API를 공개하려면 별도 권한·경로 계약과 Caddy 설정 변경이 필요하다.
- 공식 Caddy 이미지는 현재 root 사용자로 실행한다. capability는 최소화했지만 비루트
  전환에는 이름 있는 인증서 볼륨의 소유권 초기화·복구 계약이 먼저 필요하다.

## 대안

- Spring Boot에서 HTTPS 직접 종단: 인증서와 private key 생명주기를 애플리케이션 실행
  경계에 결합하므로 채택하지 않았다.
- 모든 BRIEF 경로를 reverse proxy: 권한 모델이 없는 조회·재구축 API를 함께 공개하므로
  채택하지 않았다.
- Nginx와 수동 인증서 배포: 현재 필요한 자동 HTTPS와 단일 경로 정책에 비해 인증서 조립
  절차가 늘어나므로 채택하지 않았다.
- 관리형 load balancer·Kubernetes ingress: 운영 플랫폼과 고가용성 요구가 정해지지 않아
  이번 스테이징 경계에는 채택하지 않았다.

## 관련 문서

- [BATON 이벤트 전용 Bearer 인증](../0003_baton-event-authentication/adr.md)
- [스테이징 컨테이너 실행 경계](../0004_staging-container-runtime/adr.md)
- [BRIEF HTTPS 이벤트 수신 계약](../../PRD/0022_https-event-ingress/spec.md)
