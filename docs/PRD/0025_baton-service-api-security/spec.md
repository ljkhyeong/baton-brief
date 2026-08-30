# PRD-0025: BATON 서비스 API 인증과 비공개 연결

- 상태: 채택됨
- 결정일: 2026-08-29
- 범위: BATON 백엔드가 BRIEF 조회와 에디션 생성 명령을 호출하는 서비스 전용 신뢰 경계

## 목적

BATON 사용자 요청을 중계하는 BRIEF 조회와 에디션 생성 명령을 공개 이벤트 수신 경로와
분리한다. BATON 백엔드만 비공개 네트워크와 별도 Bearer로 이 경로에 접근하고, BRIEF는
BATON 사용자 계정이나 멤버십을 복제하지 않는다.

## 허용 경로

서비스 자격 증명은 다음 기존 경로와 메서드에만 권한을 준다.

- 현재 관심 항목 단건·상태별 목록·전이 이력 `GET`
- 에디션 전역 최신·주간 최신·단건·이력·비교 `GET`
- `POST /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions`

이벤트 수신·수신 증거·이상 이력·투영 재구축·Actuator는 서비스 API 권한에 포함하지
않는다. 에디션 생성은 새 경로를 만들지 않고 PRD-0024의 기존 명령을 사용한다.

## 인증

- `brief.service-api.authentication-required=true`인 환경에서 위 허용 경로는 전용
  `Authorization: Bearer`가 필요하다.
- token은 `[A-Za-z0-9._~-]`로 된 32자 이상 200자 이하의 불투명 값이다.
- BRIEF는 현재 token과 선택적인 직전 token 한 건만 받아 순차 배포 교체 구간을 지원한다.
- BATON은 현재 token 한 건만 보내며 이벤트 송신용 `baton.brief.bearer-token`을 재사용하지
  않는다.
- 이벤트 수신과 서비스 API 인증을 함께 켠 환경에서는 양쪽의 현재·직전 token 집합이 서로
  겹치면 BRIEF가 원문 비밀을 노출하지 않고 기동을 거부한다.
- 인증이 필요한 경로의 token 누락·중복·불일치는 본문을 업무 처리하기 전에 표준 Bearer
  `401`로 끝낸다.
- token 원문은 설정·로그·응답·메트릭·영속 데이터에 남기지 않는다.

로컬 기본값은 기존 개발 흐름을 위해 인증 비활성이다. 스테이징과 BATON 연결 조립은 인증을
반드시 활성화하고 config tree 파일로 비밀을 주입한다.

## 비공개 네트워크

BATON과 BRIEF의 기본 Compose 파일은 서로 독립적으로 유지한다. 연결이 필요한 배포는 각
저장소의 전용 Compose override가 같은 외부 Docker 네트워크에 BATON 애플리케이션과
BRIEF 서비스 전용 Caddy만 연결한다.

- 운영자는 네트워크를 `--internal`로 미리 만든다.
- BATON `app`은 `https://<BRIEF_SERVICE_HOST>:8443` origin만 사용하고 운영자가 제공한
  truststore로 서버 인증서를 검증한다.
- 서비스 Caddy는 `BRIEF_SERVICE_HOST`를 SAN으로 포함한 인증서와 private key를 파일 기반
  Compose secret으로 받는다. 호스트 포트는 게시하지 않는다.
- 서비스 Caddy 이미지는 digest로 고정한 Caddy에서 불필요한 실행 파일 file capability를
  제거하고 UID/GID `10001`로 실행한다. 런타임 capability는 모두 제거한다.
- 서비스 Caddy는 기존 내부 `proxy` 네트워크에서 BRIEF `8080`으로 전달하며 정확한 조회·
  생성 허용 목록 밖의 경로는 `404`로 끝낸다.
- BRIEF 애플리케이션과 데이터베이스, 공개 `https` Caddy는 외부 서비스 네트워크에 연결하지
  않는다.
- BRIEF의 호스트 포트 비게시와 공개 Caddy의 이벤트 한 경로 허용 목록은 바꾸지 않는다.
- 조립 전 검증은 외부 네트워크가 실제로 존재하고 `Internal=true`인지 확인한다.

`--internal` Docker 네트워크도 기밀 전송 경계로 간주하지 않는다. loopback 밖의 서비스
호출은 HTTPS와 정상적인 인증서 검증을 유지한다. 이번 범위는 서버 TLS와 서비스 Bearer이며
상호 TLS, 인증서 발급 자동화와 여러 호스트 라우팅은 별도 운영 결정으로 남긴다.

## token 교체

1. BRIEF에 새 token을 현재 값, 기존 token을 직전 값으로 배포한다.
2. BATON이 새 token을 보내도록 배포하고 조회·생성 정상 호출을 확인한다.
3. BRIEF에서 직전 token을 제거한다.

직전 token을 장기 보관하거나 둘보다 많은 token을 허용하지 않는다.

## 수용 기준

- 이벤트 token으로 서비스 API를 호출할 수 없고 서비스 token으로 이벤트를 수신할 수 없다.
- 이벤트 수신과 서비스 API의 현재·직전 token이 하나라도 같으면 애플리케이션이 기동하지
  않는다.
- 허용한 조회와 생성만 서비스 token으로 접근할 수 있다.
- 수신 증거·재구축·health는 같은 token으로도 서비스 API 경계를 통과하지 못한다.
- BATON 애플리케이션과 서비스 전용 Caddy만 같은 `Internal=true` 네트워크에 연결된다.
- BATON은 HTTPS 인증서 검증을 끄지 않고 서비스 Caddy를 호출한다.
- 서비스 Caddy는 허용한 조회·생성 외의 경로를 `404`로 끝낸다.
- 서비스 Caddy는 비루트·읽기 전용 루트와 capability 없는 상태로 기동한다.
- 공개 Caddy는 계속 정확한 `POST /api/v1/events`만 전달한다.
- 새·직전 token 교체 구간과 직전 token 제거 뒤 거부를 조립 시나리오로 확인한다.

## 비목표

- BRIEF 사용자 계정·세션·멤버십·CORS
- 공개 Caddy의 조회·생성 허용 목록 확장
- OAuth2 authorization server, 사용자 JWT나 서비스 계정 데이터베이스
- 평문 HTTP, 이번 계약에서 mTLS·인증서 발급 자동화 운영 체계 도입
- 운영자용 수신 증거·재구축 API 공개

## 관련 문서

- [BATON 백엔드 경유 조회](../0023_baton-mediated-brief-query/spec.md)
- [BATON 주도 에디션 생성](../0024_baton-driven-edition-generation/spec.md)
- [이벤트 수신 인증](../0020_baton-event-authentication/spec.md)
- [서비스 API 인증 결정](../../ADR/0007_baton-service-api-security/adr.md)
