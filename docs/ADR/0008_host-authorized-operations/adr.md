# ADR-0008: 호스트 실행 권한을 사용하는 운영 명령과 지표 조회

- 상태: 채택됨
- 결정일: 2026-09-05

## 결정

첫 운영 진단·복구 경계는 승인된 배포 호스트의 셸·Docker 실행 권한으로 제한한다. BRIEF에
운영자 계정이나 공개 관리 API를 추가하지 않고, 호스트의 접근 통제·실행 감사가 호출 주체를
기록한다. Docker 실행 권한은 광범위한 호스트 권한이므로 일반 사용자나 웹 클라이언트에
제공하지 않는다.

수신 증거 단건·이상 이력·재구축은 같은 실행 JAR의 단발성 운영 명령으로 실행한다.
`operations` 프로필은 HTTP 서버와 Flyway를 비활성화하고, 명령은 기존 유스케이스를 호출한
뒤 종료한다. HTTP 인증 필터는 Servlet 실행에서만 구성하며 평상시 웹 인증은 유지한다.
수신 원문·fingerprint를 출력하지 않고 기존 안전한 수신 표현과 재구축 건수만 반환한다.

선택적인 `compose.observability.yml`은 Spring Boot 표준 속성으로 관리 서버를 컨테이너의
`127.0.0.1:9091`에 둔다. `health`와 `prometheus`만 노출하며 Docker healthcheck도 이
aggregate health를 사용한다. 앱 포트·공개 Caddy·서비스 Caddy 허용 경로는 유지한다.
지표 조회는 호스트에서 컨테이너 내부 명령을 실행할 권한이 있어야 하며 호스트 포트를
게시하거나 BRIEF를 외부 송신 네트워크에 연결하지 않는다.

## 수집·경보 책임

Micrometer의 Prometheus registry와 Spring Boot 자동 구성을 사용한다. 업무 수신 결과는
기존 `brief.events.received`, 요청 오류·지연은 기존 HTTP 지표로 수집한다. 프로세스 밖의
보관·수집 주기·경보 수신처는 실제 수집 시스템 설정이 제공된 뒤 연결한다. 지표 조회를
지원하는 것만으로 자동 감시·알림이 완료됐다고 기록하지 않는다.

BATON의 outbox 전달 장애는 BATON의 `ops/show-integration-metrics.sh`와
`ops/check-integration-delivery.sh`가 소유한다. BRIEF의 최근 수신 시각·카운터·공백 여부로
생산자 전달 완료를 추정하거나 같은 outbox 조회를 BRIEF에 구현하지 않는다.

## 대안과 한계

별도 운영 HTTPS 서비스와 Bearer를 추가하면 원격 운영 위임이 가능하지만 인증·인증서·
네트워크를 더 관리해야 한다. 현재는 호스트 실행 권한을 기준으로 한 단발성 명령을 채택한다.
향후 원격 수집기가 직접 접속해야 하면 별도 인증·HTTPS 경로를 채택하며 loopback 바인딩을
임의로 `0.0.0.0`으로 바꾸지 않는다.

재구축은 전역 잠금을 사용하는 동기 작업이다. 운영 명령으로 바뀌어도 실행 중 수신·생성의
대기 특성과 실패 롤백은 동일하다. 실행 기록·유지보수 시간·백업 확인은 운영자가 소유한다.

## 근거

- [Spring Boot 관리 서버 주소와 포트](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
- [Spring Boot 지표 registry 자동 구성](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [운영 실행 절차](../../operations/diagnostics-and-metrics.md)
