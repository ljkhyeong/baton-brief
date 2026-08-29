# ADR-0007: 별도 Bearer와 내부 Docker 네트워크로 BATON 서비스 API를 보호

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
- 두 저장소는 연결 전용 Compose override에서 운영자가 `--internal`로 만든 외부 Docker
  네트워크를 공유한다.
- BATON `app`과 BRIEF 애플리케이션만 이 네트워크에 연결하고 Caddy와 데이터베이스는 제외한다.
- 같은 호스트의 내부 네트워크에서는 컨테이너 내부 HTTP를 사용한다. 네트워크 범위가 호스트
  밖으로 넓어지면 암호화된 전송 계약을 먼저 추가한다.
- 이벤트 수신 token, 경로와 공개 Caddy 허용 목록은 바꾸지 않는다.

## 결과

### 장점

- 이벤트 쓰기 권한과 사용자 중계 조회·생성 권한의 비밀이 분리된다.
- BATON 사용자 인증을 BRIEF에 복제하지 않고도 내부 API 우회 경로를 막는다.
- 공개 DNS·인증서를 추가하지 않고 같은 호스트 Compose 배포에서 최소 연결을 만들 수 있다.

### 비용과 한계

- 두 저장소의 override와 외부 네트워크 생성·검증 절차를 함께 운영해야 한다.
- token 파일 두 종류와 순차 교체 절차가 필요하다.
- 여러 호스트 배포로 바꾸면 현재 내부 HTTP 결정을 그대로 사용할 수 없다.

## 대안

- 이벤트 Bearer 재사용: 한 비밀의 권한 범위가 넓어져 채택하지 않았다.
- 공개 Caddy에 조회·생성 추가: 브라우저가 내부 경로에 도달할 수 있어 채택하지 않았다.
- BRIEF 사용자 세션 도입: BATON의 권위 있는 계정·멤버십을 복제하므로 채택하지 않았다.
- 처음부터 mTLS 도입: 현재 같은 호스트 Compose 범위에는 인증서 발급·교체 운영 비용이 더
  크므로 보류했다.

## 관련 문서

- [서비스 API 인증과 비공개 연결](../../PRD/0025_baton-service-api-security/spec.md)
- [BATON·BRIEF 애플리케이션 경계](../0006_baton-brief-application-boundary/adr.md)
- [Caddy 이벤트 수신 앞단](../0005_caddy-event-ingress/adr.md)

