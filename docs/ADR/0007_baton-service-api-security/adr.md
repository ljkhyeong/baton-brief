# ADR-0007: 별도 Bearer와 비공개 HTTPS 앞단으로 BATON 서비스 API를 보호

- 상태: 채택됨
- 결정일: 2026-08-29

## 배경

BATON은 사용자 권한을 확인한 뒤 BRIEF의 읽기 모델을 조회하고 에디션 생성을 지시해야 한다.
기존 공개 Caddy와 Bearer는 이벤트 수신 한 경로만을 위해 설계됐다. 이를 재사용하면 이벤트
전달 비밀이 조회와 생성 권한까지 얻게 되고, 내부 API를 공개 origin에 추가해야 한다.

## 결정

- 조회·생성 허용 목록은 이벤트 수신과 다른 Spring Security stateless filter chain과
  전용 opaque Bearer를 사용한다.
- BRIEF는 현재·직전 token 한 건의 순차 교체만 지원하고 BATON은 현재 token 한 건만 보낸다.
- 이벤트 수신과 서비스 API 인증을 함께 켜면 두 경계의 현재·직전 token 집합은 서로 달라야
  하며, 겹치는 설정은 기동 시 거부한다.
- 두 저장소는 연결 전용 Compose override에서 운영자가 `--internal`로 만든 외부 Docker
  네트워크를 공유한다.
- BATON `app`과 BRIEF 서비스 전용 Caddy만 이 네트워크에 연결한다. BRIEF 애플리케이션·
  데이터베이스와 공개 Caddy는 제외한다.
- 서비스 Caddy는 호스트 포트를 게시하지 않고 운영자 제공 인증서로 `8443` HTTPS를 열며,
  정확한 조회·생성 허용 목록만 기존 내부 `proxy` 네트워크의 BRIEF로 전달한다.
- digest로 고정한 Caddy 기반 이미지에서 `cap_net_bind_service` file capability를 빌드 시
  제거하고 UID/GID `10001`, 읽기 전용 루트와 런타임 `cap_drop=ALL`로 실행한다.
- BATON은 서비스 Caddy 인증서를 truststore로 검증한다. 사설망에서도 평문 HTTP나 인증서
  검증 비활성화를 허용하지 않는다.
- 이벤트 수신 token, 경로와 공개 Caddy 허용 목록은 바꾸지 않는다.

## 결과

### 장점

- 이벤트 쓰기 권한과 사용자 중계 조회·생성 권한의 비밀이 분리된다.
- BATON 사용자 인증을 BRIEF에 복제하지 않고도 내부 API 우회 경로를 막는다.
- 공개 DNS·호스트 포트를 추가하지 않고 전송 기밀성과 서버 신원을 검증할 수 있다.

### 비용과 한계

- 두 저장소의 override와 외부 네트워크 생성·검증 절차를 함께 운영해야 한다.
- token 파일 두 종류와 순차 교체 절차가 필요하다.
- 서비스 인증서·private key와 BATON truststore의 발급·교체 절차가 필요하다.
- 고정 Caddy 버전이 바뀌면 file capability 제거와 비루트 기동을 다시 검증해야 한다.
- 이번 결정은 상호 TLS나 여러 호스트 라우팅·인증서 자동 발급을 해결하지 않는다.

## 대안

- 이벤트 Bearer 재사용: 한 비밀의 권한 범위가 넓어져 채택하지 않았다.
- 공개 Caddy에 조회·생성 추가: 브라우저가 내부 경로에 도달할 수 있어 채택하지 않았다.
- BRIEF 사용자 세션 도입: BATON의 권위 있는 계정·멤버십을 복제하므로 채택하지 않았다.
- 내부 Docker HTTP 사용: 같은 호스트에서도 다른 컨테이너와 호스트 권한에서 Bearer가
  노출될 수 있어 채택하지 않았다.
- 처음부터 mTLS 도입: 현재 서비스 Bearer와 서버 TLS로 목적을 달성하며 클라이언트 인증서
  수명주기까지 추가할 근거가 없어 보류했다.

## 관련 문서

- [서비스 API 인증과 비공개 연결](../../PRD/0025_baton-service-api-security/spec.md)
- [BATON·BRIEF 애플리케이션 경계](../0006_baton-brief-application-boundary/adr.md)
- [Caddy 이벤트 수신 앞단](../0005_caddy-event-ingress/adr.md)
