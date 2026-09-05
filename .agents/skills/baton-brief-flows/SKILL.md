---
name: baton-brief-flows
description: BATON BRIEF의 빌드·모듈·공통 설정·인증·스테이징 배포·운영 명령을 변경할 때 사용한다. 이벤트·투영·에디션만의 변경은 해당 스킬을 사용한다.
---

# BATON BRIEF 공통 설정과 실행

## 관련 결정

변경 대상의 문서만 읽는다.

- 기술·모듈·JDBC: [ADR-0002](../../../docs/ADR/0002_technology-stack/adr.md)
- 표준 HTTP 오류·상태 확인: [PRD-0004](../../../docs/PRD/0004_problem-detail/spec.md),
  [PRD-0006](../../../docs/PRD/0006_minimum-health/spec.md)
- 이벤트 Bearer: [PRD-0020](../../../docs/PRD/0020_baton-event-authentication/spec.md)
- 스테이징 컨테이너·공개 HTTPS: [ADR-0004](../../../docs/ADR/0004_staging-container-runtime/adr.md),
  [ADR-0005](../../../docs/ADR/0005_caddy-event-ingress/adr.md)
- BATON 조회·생성 연결: [PRD-0025](../../../docs/PRD/0025_baton-service-api-security/spec.md),
  [ADR-0007](../../../docs/ADR/0007_baton-service-api-security/adr.md)
- 운영 명령·지표: [ADR-0008](../../../docs/ADR/0008_host-authorized-operations/adr.md)

## 변경 시 주의점

- 버전은 빌드 설정에서 관리한다. Kotlin 플러그인·BOM은 같은 catalog 값을 사용하고 갱신 시
  Gradle 호환 범위와 실제 stdlib·reflect 버전을 확인한다. 테스트 의존성은 사용하는 모듈에만 둔다.
- PostgreSQL 시각은 JDBC `OffsetDateTime`으로 읽고 `Instant`로 변환한다. 구형 `Timestamp`로
  과거 날짜가 달라지지 않게 하며 나머지 행 매핑은 Spring에 맡긴다.
- 데이터 원본·Flyway·Problem Details·aggregate health는 Spring 표준 설정을 사용한다.
  커스텀 빈을 자동 구성으로 바꾸면 실제 컨텍스트에서 대체 여부를 확인한다.
- 이벤트 수신과 서비스 API는 별도 Bearer를 사용한다. Spring Security의 표준 Bearer 처리와
  stateless chain을 유지한다. 교체 중에는 현재·직전 token 한 건만 허용하고 전환 후 직전 값을 제거한다.
- 비밀은 파일 기반 Compose secrets·Spring config tree로 주입한다. digest 고정 이미지,
  UID/GID `10001`, 읽기 전용 루트와 `/tmp` tmpfs를 유지한다.
- BRIEF는 외부 송신 경로가 없는 `data`·`proxy`에만 연결하고 호스트 포트를 게시하지 않는다.
  공개 Caddy만 `proxy`·`egress`에 연결하고 정확한 `POST /api/v1/events` 외에는 `404`를 반환한다.
- 서비스 Caddy만 `proxy`와 BATON 공유 `--internal` 네트워크에 연결해 호스트 포트 없이
  `8443` HTTPS·허용 목록을 제공한다. 사설망에서도 인증서 검증을 끄거나 평문 HTTP를 쓰지 않는다.
- 공개 Caddy는 `cap_drop=ALL`에 `NET_BIND_SERVICE`만 추가한다. 서비스 Caddy는 빌드 시
  file capability도 제거하고 런타임 `cap_drop=ALL`을 유지한다. Bearer 판정은 앱이 맡고
  Caddy 접근 로그에는 Authorization을 남기지 않는다.
- 기본 health probes는 비활성화한다. 선택적 지표는 관리 서버 `127.0.0.1:9091`에서만 제공한다.
  운영 명령은 웹 서버·Flyway를 끄고 한 번 실행하며 호스트 권한을 사용한다. 공개 관리 API를 추가하지 않는다.

## 변경에 맞는 검증

- 공유 빌드 로직·모듈 의존성 변경은 `./gradlew test :bootstrap:bootJar`로 확인한다. 실행 환경을
  바꾸면 해당 JAR을 격리 환경에서 한 번 기동한다. 같은 제품 시나리오의 반복 검증은 추가하지 않는다.
- 스테이징 조립은 Compose 구문·이미지 빌드·실제 비루트/읽기 전용 실행·DB aggregate health·
  호스트 포트 비게시·파일 Bearer 수신을 확인한다.
- Caddy 변경은 신뢰한 HTTPS에서 설정 유효성, 무인증 `401`, 정상 Bearer, 허용 목록 밖 `404`와
  Authorization 로그 비노출을 확인한다. 네트워크·capability 변경은 실제 연결·권한도 확인한다.
- 운영 명령·지표 변경은 실행 포트·종료 상태·Flyway 비활성·비밀 비노출을 해당 시나리오로 확인한다.
