# ADR-0008: 호스트 실행 권한을 사용하는 운영 명령과 지표 조회

- 상태: 채택됨
- 결정일: 2026-09-05

## 결정

운영 진단·복구는 승인된 배포 호스트의 셸·Docker 권한으로 실행한다. BRIEF에
운영자 계정이나 공개 관리 API를 추가하지 않고, 호스트의 접근 통제·실행 감사가 호출 주체를
기록한다. Docker 실행 권한은 광범위한 호스트 권한이므로 일반 사용자나 웹 클라이언트에
제공하지 않는다.

수신 기록 단건·이상 수신 기록·재구축은 같은 실행 JAR의 단발성 운영 명령으로 실행한다.
`operations` 프로필은 HTTP 서버와 Flyway를 비활성화하고, 명령은 기존 유스케이스를 호출한
뒤 종료한다. HTTP 인증 필터는 Servlet 실행에서만 구성하며 평상시 웹 인증은 유지한다.
수신 원문·fingerprint를 출력하지 않고 기존 수신 조회 결과와 재구축 건수만 반환한다.

선택적인 `compose.observability.yml`은 Spring Boot 표준 속성으로 관리 서버를 컨테이너의
`127.0.0.1:9091`에 둔다. `health`와 `prometheus`만 제공한다. Docker healthcheck는
`/actuator/health`로 앱과 DB 상태를 확인한다. 앱 포트·공개 Caddy·서비스 Caddy 허용 경로는 유지한다.
지표 조회는 호스트에서 컨테이너 내부 명령을 실행할 권한이 있어야 하며 호스트 포트를
게시하거나 BRIEF를 외부 송신 네트워크에 연결하지 않는다.

## 수집·경보 책임

Micrometer의 Prometheus registry와 Spring Boot 자동 구성을 사용한다. 업무 수신 결과는
기존 `brief.events.received`, 요청 오류·지연은 기존 HTTP 지표로 수집한다.
추가 이용료 없이 운영하도록 선택적 Compose에 자체 호스팅 Prometheus를 둔다.
BRIEF의 네트워크 공간을 공유해 loopback 지표를 수집하며 외부 송신·호스트 포트를 추가하지 않는다.
수집기는 비루트·읽기 전용이고 지표는 전용 볼륨에 보관한다. BRIEF 재생성 시 함께 갱신한다.

수집은 30초 간격, 보관은 7일 또는 1GB 기준이다. 수집 실패·대상 누락, 지속적인 HTTP 서버
오류, 충돌·미지원 수신 증가와 DB 연결 대기를 경보 규칙으로 판정한다.
DB 대기는 HikariCP의 기본 지표를 사용하며 별도 수집기를 추가하지 않는다.
외부 알림 수신처는 미연결 상태로 둔다.
같은 서버의 수집기는 호스트 전체 장애를 감지할 수 없다. 유료 관리형 저장소·API 대신
기존 서버 자원을 사용한다.

BATON의 outbox 전달 장애는 BATON의 `ops/show-integration-metrics.sh`와
`ops/check-integration-delivery.sh`로 확인한다. BRIEF의 최근 수신 시각·카운터·공백 여부로
생산자 전달 완료를 추정하거나 같은 outbox 조회를 BRIEF에 구현하지 않는다.

## 대안과 한계

별도 운영 HTTPS 서비스와 Bearer를 추가하면 원격 운영 위임이 가능하지만 인증·인증서·
네트워크를 더 관리해야 한다. 현재는 호스트 실행 권한을 기준으로 한 단발성 명령을 채택한다.
향후 원격 수집기가 직접 접속해야 하면 별도 인증·HTTPS 경로를 채택하며 loopback 바인딩을
임의로 `0.0.0.0`으로 바꾸지 않는다.

재구축은 전역 잠금을 사용하는 동기 작업이다. 운영 명령으로 바뀌어도 실행 중 수신·생성의
대기 특성과 실패 롤백은 동일하다. 운영자는 실행 기록을 남기고 유지보수 시간을 정하며 백업을 확인한다.

## 근거

- [Spring Boot 관리 서버 주소와 포트](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
- [Spring Boot 지표 registry 자동 구성](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus Docker 실행](https://prometheus.io/docs/prometheus/latest/installation/)
- [운영 실행 절차](../../operations/diagnostics-and-metrics.md)
