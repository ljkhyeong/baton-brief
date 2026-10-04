---
name: baton-brief-api-flows
description: BATON BRIEF의 HTTP 경로·요청/응답 DTO·Problem Details·커서·ETag 표현과 BATON 서비스 API 허용 목록을 추가하거나 바꿀 때 사용한다. 조회·생성 규칙 자체는 투영·에디션 스킬을 함께 사용한다.
---

# BATON BRIEF HTTP API

## 관련 계약

변경 대상의 문서만 읽는다.

- 요청 오류: [PRD-0004](../../../docs/PRD/0004_problem-detail/spec.md)
- BATON 경유 조회·생성: [PRD-0023](../../../docs/PRD/0023_baton-mediated-brief-query/spec.md),
  [PRD-0024](../../../docs/PRD/0024_baton-driven-edition-generation/spec.md)
- 서비스 API 인증·허용 경로: [PRD-0025](../../../docs/PRD/0025_baton-service-api-security/spec.md),
  [ADR-0007](../../../docs/ADR/0007_baton-service-api-security/adr.md)
- 경로별 의미는 [투영 스킬](../baton-brief-projection-flows/SKILL.md)·
  [에디션 스킬](../baton-brief-edition-flows/SKILL.md)의 해당 PRD를 따른다.

## 유지할 규칙

- 경로는 `BriefController`의 `/api/v1` 아래에 두고 `BriefUseCases`에 위임한다. 요청·응답은
  `BriefDtos.kt`의 전송 DTO로 표현하고 도메인 객체·DB 행·지문·원문 payload를 노출하지 않는다.
- 입력 형식은 DTO의 Bean Validation, Spring 표준 `ProblemDetail`과 Jackson 엄격 설정에 맡긴다.
  전용 오류 DTO·`ResponseEntityExceptionHandler` 하위 클래스·별도 본문 검사기를 만들지 않는다.
- BATON이 호출하는 조회·생성 경로를 추가·변경·삭제하면 세 허용 목록을 함께 맞춘다.
  `BriefServiceApiSecurityConfiguration`의 `SERVICE_API`, `ops/Caddyfile.service`의 `path_regexp`,
  PRD-0025의 허용 경로다. 앱 목록이 빠지면 인증 환경에서 `403`, Caddy 목록이 빠지면 `404`가 된다.
- 이벤트 수신·수신 기록·이상 기록·재구축·Actuator는 서비스 API 허용 목록에 넣지 않는다.
- 목록 커서는 정렬 키의 배타 키셋이며 스냅샷이 아니다. 커서 형식·정렬·기본 필터를 바꾸면
  BATON 호출자가 가진 다음 페이지 요청과 호환되는지 확인한다.
- ETag는 `brief-<표현>-v<N>-...` 형식으로 표현 버전을 포함한다. 응답 필드나 의미를 바꾸면 해당
  표현 버전을 올리고 조건부 요청 처리는 Spring MVC에 맡긴다.
- 경로·필드·상태 코드가 바뀌면 소유 PRD를 갱신하고 BATON 소비자 영향을 HANDOFF에 남긴다.
  BATON 저장소 수정은 사용자가 요청한 범위에서만 한다.

## 변경에 맞는 검증

- 응답·입력 오류 변경은 `BriefMvpIntegrationTest`의 해당 시나리오에서 상태 코드·
  `application/problem+json`·필드·시간 형식을 확인한다.
- 허용 목록 변경은 `BriefServiceApiSecurityIntegrationTest`에서 무인증·이벤트 token `401`,
  서비스 token 성공과 목록 밖 경로 `403`을 확인한다. Caddy는
  [공통 설정 스킬](../baton-brief-flows/SKILL.md)의 Caddy 검증을 따른다.
- ETag 변경은 같은 표현의 `304`와 표현·선택이 바뀐 뒤의 `200`을 확인한다.
