# ADR-0003: BATON 이벤트에 전용 Bearer 인증 사용

- 상태: 채택됨
- 결정일: 2026-08-27

## 배경

BATON과 BRIEF의 실제 로컬 이벤트 전달은 검증했지만 수신 경로는 인증되지 않았다.
BRIEF 전체 API의 사용자·운영자 권한과 배포 환경은 아직 정해지지 않았으므로, 생산자
인증을 그 결정과 결합하면 로컬 이벤트 경계까지 불필요하게 지연된다.

## 결정

- BATON이 호출하는 `POST /api/v1/events`에 환경별 전용 정적 Bearer token을 사용한다.
- BRIEF는 Spring Security의 stateless Bearer 처리와 표준 인증 진입점을 사용한다.
- BATON은 Spring `RestClient`의 `HttpHeaders.setBearerAuth`를 사용한다.
- 인증은 로컬 기본 비활성이고 명시적으로 필수화할 때 유효한 token 없이는 BRIEF가
  기동하지 않는다.
- loopback 이외의 생산자 기본 URL은 기존대로 HTTPS origin만 허용한다. TLS 종단과 신뢰
  저장소는 배포 환경이 소유한다.
- 다른 BRIEF API의 권한, token 회전·발급, OAuth2/JWT와 mTLS는 별도 결정으로 남긴다.

## 근거

- 단일 BATON 생산자와 내부 이벤트 경계에는 정적 전용 비밀이 가장 작은 인증 수단이다.
- Spring Security의 Bearer resolver를 사용하면 헤더 중복·형식 오류 처리를 직접 구현하지
  않아도 된다.
- 인증과 HTTPS 책임을 분리하면 애플리케이션에 인증서 우회·신뢰 저장소 관리 코드를 넣지
  않고 배포 환경의 표준 TLS를 사용할 수 있다.

## 결과

### 장점

- 이벤트 수신 경로가 인증 없이 호출되는 운영 위험을 줄인다.
- 기존 이벤트 본문·지문·재전달·결과 분류를 바꾸지 않는다.
- 사용자 세션이나 새 권한 모델을 만들지 않고 생산자 경계만 보호한다.

### 단점

- 하나의 공유 비밀이므로 생산자별 권한 분리와 자체 회전 기능이 없다.
- 실제 HTTPS 스테이징과 비밀 관리 절차 없이는 운영 준비 완료를 주장할 수 없다.
- BRIEF의 조회·재구축 경로는 여전히 로컬 내부 경계다.

## 대안

- JWT/OAuth2 resource server: 복수 발급자·세분 권한 요구가 없어 현재는 과도하다.
- mTLS: 배포 인증서 생명주기 결정이 없어 지금 애플리케이션 계약으로 고정하지 않는다.
- 커스텀 헤더 또는 직접 filter: Spring Security의 표준 Bearer 처리보다 오류 가능성과
  유지 비용이 크다.
