# PRD-0020: BATON 이벤트 수신 인증 계약

- 상태: 채택됨
- 결정일: 2026-08-27
- 구현 상태: 로컬 생산자·소비자 Bearer 인증 구현 및 검증 완료
- 범위: BATON이 BRIEF `POST /api/v1/events`를 호출할 때 사용하는 서비스 간 인증

## 목적

PRD-0018과 PRD-0019는 BATON 원본에서 BRIEF PostgreSQL까지의 로컬 수렴을 검증했지만
이벤트 수신 경로는 인증되지 않았다. 다른 BRIEF 조회·운영 API의 권한 모델을 추측하지
않으면서 BATON 생산자가 호출하는 이벤트 수신 경로만 전용 비밀로 보호한다.

## 요청 계약

- 보호 대상은 `POST /api/v1/events` 하나다.
- 인증을 켜면 요청은 `Authorization: Bearer <token>`을 포함해야 한다.
- 누락되거나 올바르지 않은 Bearer는 `401 Unauthorized`와
  `WWW-Authenticate: Bearer` 응답을 받으며 수신 기록과 투영을 만들지 않는다.
- 올바른 Bearer 뒤의 이벤트 본문·멱등성·충돌·HTTP 결과는 PRD-0002와 PRD-0019를
  그대로 따른다.
- `GET` 조회, 에디션 생성과 투영 재구축의 운영자·사용자 권한은 이번 계약에 포함하지
  않는다. 현재 로컬 경계를 운영 공개 API로 해석하지 않는다.

## 설정과 비밀 경계

BRIEF는 다음 표준 환경 설정을 사용한다.

| 환경 변수 | 의미 | 기본값 |
|---|---|---|
| `BRIEF_EVENT_RECEIVER_AUTHENTICATION_REQUIRED` | 이벤트 수신 Bearer 필수 여부 | `false` |
| `BRIEF_EVENT_RECEIVER_BEARER_TOKEN` | 수신자가 비교할 전용 비밀 | 빈 값 |

인증이 필수이면 token은 32~200자의 URL-safe ASCII여야 하며, 조건을 만족하지 않으면
애플리케이션 기동이 실패한다. 기본값은 기존 로컬 MVP 호환을 위해 비활성이다.

BATON은 `BATON_BRIEF_BEARER_TOKEN`이 설정된 경우 Spring `RestClient`의 표준 Bearer
헤더 API로 같은 token을 전송한다. token은 설정 객체 문자열, 로그, 이벤트 본문, 수신
증거와 오류 코드에 포함하지 않는다. BRIEF는 JDK의 일정 시간 바이트 비교를 사용한다.

생산자와 소비자는 같은 환경에서 전용 값을 공유하되 저장소에 값을 커밋하지 않는다.
WATCH·워크스페이스 운영 키와 다른 비밀을 사용한다.

## HTTPS 경계

BATON은 loopback 이외의 BRIEF 기본 URL에 HTTPS origin만 허용하는 기존 검증을 유지한다.
TLS 종단, 인증서 발급·회전, JDK 신뢰 저장소와 스테이징 주소는 배포 환경이 소유하며
애플리케이션에 자체 인증서 검증기나 우회 가능한 trust-all client를 추가하지 않는다.

이번 로컬 검증은 loopback HTTP 위에서 Bearer 계약을 확인했다. 실제 HTTPS 스테이징
전달과 비밀 주입·회전은 아직 검증하지 않았다.

## 구현 원칙

- BRIEF는 Spring Security의 stateless `SecurityFilterChain`, 표준 Bearer token resolver와
  인증 진입점을 사용한다.
- 직접 `Authorization` 헤더 파서, 세션, 사용자 계정, token 저장소와 별도 오류 DTO를
  만들지 않는다.
- BATON의 `401`은 자동 재시도로 고칠 수 없는 영구 전달 실패다.
- 인증 실패는 BATON 원본 트랜잭션이나 이미 기록한 outbox를 되돌리지 않는다.

## 수용 기준

- 인증이 필수인 BRIEF는 Bearer가 없는 요청과 잘못된 요청을 `401`로 거부한다.
- 같은 구성의 정상 Bearer는 기존 v1·v2 이벤트 수신 결과를 유지한다.
- BATON 실제 `RestClient`가 전용 Bearer를 보내며 `401`을 영구 실패로 분류한다.
- 실제 BATON·BRIEF 실행 JAR과 MySQL·PostgreSQL을 사용한 기존 원본 수렴 시나리오가
  Bearer 인증을 켠 상태에서도 성공한다.
- 비밀 값은 문서, 로그, 응답과 영속 데이터에 노출되지 않는다.

## 비목표

- BRIEF 조회·에디션·재구축 API의 사용자·운영자 권한 모델
- OAuth2 authorization server, JWT, token introspection, mTLS와 복수 생산자 권한
- token 발급·회전 자동화와 비밀 관리 제품 선택
- 공개 DNS, 인증서, 방화벽과 실제 HTTPS 스테이징 활성화
- 계약 팩 `2.0.0-rc.1`의 안정 버전 승격

## 현재 검증

- BRIEF PostgreSQL 통합 시나리오에서 Bearer 누락·오류를 `401`로 거부하고 정상 Bearer로
  기존 HTTP 계약을 처리했다.
- BATON 외부 어댑터 테스트에서 실제 JSON 요청에 Bearer 헤더가 포함되고 `401`이 영구
  실패로 분류됨을 확인했다.
- 실제 BATON·BRIEF 실행 JAR과 MySQL 8.4·PostgreSQL 18.4를 연결한 선택 실행 테스트가
  인증을 켠 상태에서 원본 API·초기 정합화·재전달·심각도 변경·해소 수렴을 완료했다.

## 관련 문서

- [BATON 생산자 호환성 선행조건](../0018_baton-producer-compatibility/spec.md)
- [BATON 연속성 신호 이벤트 v2](../0019_baton-continuity-event-v2/spec.md)
- [서비스 간 이벤트 인증 결정](../../ADR/0003_baton-event-authentication/adr.md)
