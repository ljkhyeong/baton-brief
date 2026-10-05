---
name: baton-brief-operations-flows
description: BATON BRIEF의 Micrometer 지표·Prometheus 수집과 경보 규칙·Alertmanager 알림, 호스트 운영 명령(수신 진단·재구축)과 PostgreSQL 백업·복원 절차를 변경할 때 사용한다.
---

# BATON BRIEF 운영 지표와 복구

## 관련 문서

변경 대상의 문서만 읽는다.

- 수신 결과 지표: [PRD-0026](../../../docs/PRD/0026_event-ingestion-metrics/spec.md)
- 운영 명령·지표 접근: [ADR-0008](../../../docs/ADR/0008_host-authorized-operations/adr.md),
  [진단·지표 절차](../../../docs/operations/diagnostics-and-metrics.md)
- Slack·Discord 알림: [외부 연동](../../../docs/operations/external-integrations.md)
- 백업·격리 복원: [백업 절차](../../../docs/operations/postgresql-backup-restore.md)
- 컨테이너·Compose 자체 변경은 [공통 설정 스킬](../baton-brief-flows/SKILL.md)을 함께 사용한다.

## 유지할 규칙

- 지표 레이블은 값이 제한된 항목만 쓴다. 작업공간·시즌·이벤트 식별자, 원본 참조, 지문, URL,
  token을 넣지 않는다. `brief.events.received`는 `outcome` 하나로 여섯 결과를 기동 시 `0`으로 등록하고
  웹 어댑터에서만 증가시킨다. HTTP·JVM·HikariCP 계측은 Spring Boot 자동 구성을 복제하지 않는다.
- 지표는 `compose.observability.yml`을 쓸 때만 컨테이너 내부 관리 서버 `127.0.0.1:9091`에서 제공한다.
  Prometheus도 같은 네트워크 공간의 `127.0.0.1:9090`에만 열고 호스트 포트·공개 관리 API를 추가하지 않는다.
- 경보는 `ops/prometheus/alerts.yml`과 `alerts.test.yml`을 함께 고친다. 지표 이름·의미가 바뀌면 경보
  쿼리와 진단 절차의 경보 표를 맞춘다. 주석에 수신처 URL·식별자를 넣지 않고, 해제를 문제 해결로 표현하지 않는다.
- Prometheus가 모든 경보에 `service=brief`를 붙인다. Alertmanager 예시 세 개(`slack`·`discord`·
  `slack-discord`)의 라우팅·`group_by`를 함께 맞추고 웹훅은 `*_url_file` 비밀 파일로만 주입한다.
- 운영 명령은 `operations` 프로필로 웹 서버·Flyway를 끄고 `RECEIPT`·`ANOMALIES`·`REBUILD` 중 하나를
  한 번 실행한 뒤 종료한다. 누락·미지원 명령은 0이 아닌 종료 코드로 실패한다.
- 백업은 `pg_dump` custom 형식, 확인은 격리된 빈 DB의 `pg_restore`다. 운영 DB를 덮어쓰는 복구와
  앱 내부 스케줄러를 추가하지 않고 정기 실행은 `ops/systemd` 타이머가 맡는다.

## 변경에 맞는 검증

- 지표 변경은 `BriefEventMetricsTest`와 해당 수신 시나리오의 결과별 증가량을 확인한다.
- 수집·경보 변경은 CI `verify.yml`의 `지표 설정과 경보 규칙 검증` 단계와 같은 이미지로
  `promtool check config`·`promtool test rules alerts.test.yml`, Alertmanager는 `amtool check-config`와
  `config routes test`를 실행한다. 설정 검사를 실제 Slack·Discord 메시지 도착으로 보고하지 않는다.
- 운영 명령 변경은 `BriefOperationsConfigurationTest`와 실행 JAR의 종료 상태·웹/Flyway 비활성·출력의
  비밀 비노출을 확인한다.
- 백업 스크립트 변경은 `bash -n`과 `ops/verify-postgresql-backup.sh`의 격리 복원으로 확인한다.
  문법 검사만으로 백업·복원 성공을 보고하지 않는다.
