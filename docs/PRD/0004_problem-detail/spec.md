# PRD-0004: 표준 요청 오류 응답

- 상태: 채택됨
- 결정일: 2026-08-14
- 구현 상태: 로컬 MVP 구현 및 검증 완료
- 범위: `/api/v1`의 클라이언트 수정 가능 요청 오류와 명시적 미존재 응답

## 목적

현재 로컬 MVP는 성공·도메인 처리 결과의 HTTP 상태와 본문을 정의하지만, 요청 검증 실패와
없는 에디션은 Spring Boot 기본 오류 map으로 응답한다. 호출자가 상태마다 다른 오류 형식을
해석하지 않도록 Spring Boot와 Spring Framework가 제공하는 RFC 9457 `ProblemDetail`
자동 구성을 사용한다.

별도 오류 DTO와 전역 예외 처리 기반을 다시 구현하지 않는다. 기존 도메인 처리 결과와
예상하지 못한 서버·데이터베이스 실패의 분류를 이번 계약으로 확대하지 않는다.

## 적용 범위

다음 Spring MVC 요청 오류는 `application/problem+json`으로 응답한다.

- Bean Validation 요청 본문 실패
- 요청 매개변수의 `@Positive`, `@Min`, `@Max` 검증 실패
- JSON, enum과 타입 역직렬화 실패. 소수를 정수로 바꾸지 않는다.
- 대상 요청 DTO에 선언하지 않은 JSON 필드
- UUID·숫자 경로 또는 요청 매개변수 변환 실패
- 필수 요청 매개변수 누락
- 명시적인 `ResponseStatusException` 기반 `404 Not Found`
- 지원하지 않는 HTTP 메서드 또는 미디어 타입

Spring Boot 표준 속성 `spring.mvc.problemdetails.enabled=true`를 사용한다. 프로젝트 전용
오류 DTO나 `ResponseEntityExceptionHandler` 하위 클래스를 만들지 않는다.

## 응답 의미

응답은 RFC 9457 표준 필드를 사용한다.

| 필드 | 의미 |
|---|---|
| `type` | 선택적인 문제 유형 URI. 생략하면 RFC 9457에 따라 `about:blank`와 같은 의미 |
| `title` | HTTP 상태의 짧은 설명 |
| `status` | HTTP 상태 코드 |
| `detail` | Spring이 제공하는 안전한 오류 설명 |
| `instance` | 오류가 발생한 요청 경로 |

첫 MVP에서는 확장 `code`, 오류 배열, 거부된 값과 추적 식별자를 추가하지 않는다. 호출자는
`detail` 문구 전체를 기계적으로 파싱하지 않고 HTTP 상태와 표준 필드를 사용한다.

## 기존 결과와의 관계

- 이벤트 처리의 `CONFLICT` `409`, `UNSUPPORTED` `422`, `DUPLICATE`, `STALE`, `APPLIED`와
  `APPLIED_WITH_GAP`은 오류 예외가 아니라 PRD-0002가 정의한 도메인 처리 결과다. 기존
  `IngestResponse`를 유지한다.
- 에디션 생성의 `200`·`201`, 조회의 성공 본문과 PRD-0003 이력 응답은 바꾸지 않는다.
- 예상하지 못한 `DataAccessException`과 애플리케이션 `RuntimeException`은 이번
  `ProblemDetail` 자동 처리 범위가 아니다. 기본 500 응답의 예외명·메시지·스택 비노출
  설정을 유지하며, 재시도 가능성이나 오류 코드가 채택되기 전 임의 분류하지 않는다.

## 민감정보 제한

- 원문 요청 본문, `sourceReference`, 페이로드 지문, PII, 자격 증명과 원문 URL을 오류
  확장 속성에 복사하지 않는다.
- SQL, PostgreSQL 진단, 예외 클래스, 예외 메시지와 스택 트레이스를 응답에 포함하지 않는다.
- `spring.web.error.include-message`, `include-binding-errors`, `include-exception`,
  `include-stacktrace`를 활성화하지 않는다.

## 호환성과 비목표

- 기존 요청 오류의 HTTP 상태는 바꾸지 않고 오류 표현만 표준화한다.
- 계약에 없는 JSON 필드를 보내던 요청은 더 이상 해당 필드를 조용히 무시하지 않고
  `400 Bad Request`로 거부한다. `/api/v1`의 명시적 입력 계약을 엄격하게 적용하는 변경이다.
- 애플리케이션별 오류 코드 체계, 필드별 오류 배열과 다국어 메시지를 정의하지 않는다.
- 데이터베이스 장애의 `503` 분류, 재구축 중복 실행 오류와 운영 인증·인가는 포함하지
  않는다.

## 수용 기준

- 대표적인 Bean Validation `400`과 없는 에디션 `404`가
  `application/problem+json`으로 응답한다.
- 대표 요청에서 정수가 아닌 숫자와 선언하지 않은 JSON 필드를 `400`으로 거부한다.
- 응답에 `title`, `status`, `detail`, `instance`가 있고 상태와 요청 경로가 일치한다.
  `type`은 별도 문제 유형이 있을 때만 포함하며, 생략된 경우 `about:blank`로 해석한다.
- 기존 이벤트 도메인 결과와 성공 응답 본문은 바뀌지 않는다.
- 별도 전역 예외 처리 클래스나 프로젝트 전용 오류 DTO를 추가하지 않는다.
