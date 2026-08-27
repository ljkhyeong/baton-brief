# PRD-0006: 최소 상태 확인

- 상태: 채택됨
- 결정일: 2026-08-18
- 구현 상태: 로컬 구현 및 검증 완료
- 범위: 로컬 실행 프로세스와 필수 데이터베이스의 표준 집계 상태 확인

## 목적

현재 BRIEF는 제품 기능과 PostgreSQL 통합 동작을 검증했지만, 실행 중인 프로세스가 요청을
처리할 수 있는지 확인하는 관리 경로는 없다. 제품 API를 새로 만들지 않고 Spring Boot
Actuator의 표준 health 자동 구성을 사용해 최소 상태 확인 경계를 추가한다.

이 경로는 BRIEF의 실행 상태만 나타낸다. 이벤트 전달 완전성, 투영 최신성, 에디션 생성
시각과 다른 BATON 서비스의 가용성을 재판정하지 않는다.

## 구현 수단

- `bootstrap`에서 `spring-boot-starter-actuator`를 사용한다. 이 starter가 기본
  `spring-boot-starter`를 포함하므로 두 의존성을 중복 선언하지 않는다.
- 현재 `DataSource`를 감지하는 Spring Boot의 표준 DB health contributor를 사용한다.
- 커스텀 controller, 응답 DTO, `HealthIndicator`, 상태 enum, HTTP 상태 매퍼와 DB 확인 SQL을
  만들지 않는다.
- 도메인, application, 웹 어댑터, 영속성 어댑터와 Flyway 스키마는 바꾸지 않는다.

## 관리 HTTP 계약

| 메서드 | 경로 | 의미 |
|---|---|---|
| `GET` | `/actuator/health` | Spring Boot 표준 health contributor의 현재 집계 상태 |

- `/api/v1` 제품 API와 분리하고 애플리케이션과 같은 포트를 사용한다.
- 별도 management 포트나 프로젝트 전용 `/healthz` 경로를 만들지 않는다.
- 로컬 계약과 동일하게 인증은 없지만 외부 공개나 운영 사용을 허용하는 결정은 아니다.

## 응답 의미

정상 응답 예시는 다음과 같다.

```json
{"status":"UP"}
```

- 표준 집계 상태가 `UP`이면 `200 OK`를 반환한다.
- 기동한 뒤 표준 집계 상태가 `DOWN` 또는 `OUT_OF_SERVICE`가 되면 Spring Boot 기본 매핑에
  따라 `503 Service Unavailable`을 반환한다. PostgreSQL 연결 상실은 `DOWN`의 한 원인이 될
  수 있다.
- PostgreSQL이 애플리케이션 기동 전부터 없으면 Flyway 또는 데이터 원본 초기화에서 기동이
  실패할 수 있으므로, 그 경우에는 health HTTP 응답 자체가 존재하지 않는다.

응답은 최상위 `status`만 노출한다. 다음 정보는 공개하지 않는다.

- `components`, `details`, DB 제품명·버전과 확인 쿼리
- JDBC URL, 사용자명, 스키마, SQL, 예외 메시지와 스택 트레이스
- 이벤트 수, 충돌 수, 리비전 공백과 최근 에디션 시각
- BATON, WATCH, RELAY와 GO의 연결 상태

## 노출 범위

Spring Boot 4.1.0은 health만 기본 웹 endpoint로 노출하고 상세를 기본 숨김 처리한다. 같은
기본값을 설정에 반복하지 않는다. 반면 health probes의 기본값은 `true`이므로 배포 계약이
없는 현재 MVP에서는 `management.endpoint.health.probes.enabled=false`를 명시한다.

다음 기본 표면도 이번 계약에 포함하지 않는다.

- 기본 활성화되는 Actuator 링크 탐색 페이지 `/actuator`는 표준 속성으로 끈다.
- 기본 활성화되는 `/actuator/health/liveness`와 `/actuator/health/readiness` probe 그룹은
  표준 속성으로 끈다.
- `/livez`, `/readyz` 같은 추가 probe 경로는 기본 비활성 상태를 그대로 유지한다.

Kubernetes나 다른 배포 오케스트레이터를 채택할 때 liveness, readiness, 트래픽 제거와
외부 의존성 포함 정책을 별도 운영 결정으로 정의한다.

## 호환성과 비목표

- 기존 `/api/v1` 요청·응답과 PostgreSQL 스키마를 바꾸지 않는 추가 관리 계약이다.
- `info`, `metrics`, `prometheus`, `env`, `beans`, `loggers`, `flyway` endpoint를 노출하지
  않는다.
- 인증·인가, TLS, 별도 관리 네트워크, 컨테이너·Kubernetes 배포와 SLO를 정의하지 않는다.
- 투영 최신성, 원본 워터마크, 생산자 종단 간 전달과 외부 서비스 상태를 health로
  판정하지 않는다.
- 커스텀 상태 코드, 상태 상세와 경보·대시보드를 추가하지 않는다.

## 수용 기준

- PostgreSQL과 함께 기동한 로컬 애플리케이션의 `GET /actuator/health`가 `200 OK`와
  `status=UP`을 반환한다.
- 정상 응답에 `components`와 `details`가 없다.
- `/actuator` 탐색 페이지와 대표 probe 경로가 노출되지 않는다.
- 커스텀 health 코드와 DB 확인 SQL 없이 Spring Boot 자동 구성만 사용한다.
- 기존 전체 테스트와 실행 JAR 생성이 성공한다.
