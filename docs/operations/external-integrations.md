# 추가 요금 없는 외부 연동 검토

2026-09-12 기준 BRIEF 코드와 BATON·CAL·RELAY·WATCH·GO의 연동 안내를 확인했다.
Cloudflare DNS, Ubuntu 홈서버, 공인 IP·인증서와 공유기 80·443 포트포워딩은 준비됐고,
k3s는 도입 예정이다. 이 문서는 연동 선택과 설정을 다루며 서버 설치를 포함하지 않는다.

## 검토 결과

| 대상 | 기존 기능 또는 연동 방법 | 판단 |
| --- | --- | --- |
| 운영 장애 알림 | Prometheus → Alertmanager → Slack Incoming Webhook | 이번에 BRIEF 경보 전달 설정과 Slack 수신 설정을 추가했다. 공용 Alertmanager와 웹훅을 연결하면 사용한다. |
| 공휴일 표시 | BATON의 `KasiPublicHolidayClient`가 한국천문연구원 API를 조회하고 1시간 캐시 | 이미 구현돼 있다. BATON의 `BATON_HOLIDAYS_ENABLED`와 서비스 키를 설정한다. BRIEF·CAL에 같은 호출을 추가하지 않는다. |
| 캘린더 앱 연결 | CAL의 `.ics` 구독, BATON의 Google·Apple·Outlook 등록 안내 | 기존 기능을 사용한다. 앱이 정한 주기로 갱신되며 실시간 양방향 동기화는 아니다. |
| 업무 알림 발송 | RELAY의 Discord·HTTP 웹훅 어댑터 | 기존 발송·재시도 기능을 사용한다. 이번 Slack 선택은 운영 경보에 적용하며 RELAY의 업무 발송 채널은 바꾸지 않는다. |
| 외부 백업 보관 | BATON은 이미 `rclone crypt`로 외부 저장소를 연결. BRIEF는 PostgreSQL 백업·격리 복원 제공 | 보유한 별도 저장소가 정해지면 rclone으로 연결한다. 저장소별 API 클라이언트를 만들거나 유료 저장소를 추가하지 않는다. |
| DNS 갱신 | Cloudflare DNS API를 사용하는 ddclient | 공인 IP가 바뀌는 환경에만 필요하다. 고정 IP면 생략한다. 직접 IP 감지·DNS 갱신 코드를 만들지 않는다. |
| 인증서 갱신 | 기존 Caddy의 ACME, 향후 공용 앞단의 인증서 관리 기능 | 준비된 인증서의 종류·갱신 방식을 따른다. 서비스마다 별도 인증서 발급 코드를 추가하지 않는다. |
| 서버 전체 장애 감지 | BATON의 선택적 외부 상태 감시와 WATCH의 Grafana Cloud Free 연결 예시 | 이미 준비된 외부 감시를 검토한다. 같은 홈서버의 Alertmanager만으로 전원·회선 장애를 알릴 수는 없다. 무료 플랜·실행 한도를 확인한 뒤 연결한다. |
| URL 점검·단축 링크 | WATCH의 점검, GO의 링크 만료·폐기와 권한 경계 | 외부 서비스로 교체하면 기존 이벤트·접근 제한과 데이터 정책을 다시 구현해야 한다. 현재 구현을 유지한다. |
| 브리프 생성 | BATON 이벤트를 기준으로 점검 항목과 불변 브리프 생성 | 외부 요약 API로 대체하지 않는다. 현재 규칙은 BRIEF의 핵심 기능이며 외부 API가 구현을 줄여 주지 않는다. |

공휴일 API는 [공공데이터포털](https://www.data.go.kr/data/15012690/openapi.do)에 무료로 명시돼 있다.
활용 신청·서비스 키·요청 제한은 필요하며 조회한 공휴일로 확정 일정을 자동 변경하지 않는다.
DNS 자동 갱신은 [Cloudflare가 안내하는 ddclient](https://developers.cloudflare.com/dns/manage-dns-records/how-to/managing-dynamic-ip-addresses/)를 우선 검토한다.
이미 준비된 공인 IP·포트포워딩 경로에 Tunnel을 추가할 필요는 현재 확인되지 않았다.

## 이번에 추가한 Slack 운영 알림

```text
BRIEF 지표 → 기존 Prometheus 경보 → 공용 Alertmanager → Slack 운영 채널
```

앱 코드·DB·공개 API는 바꾸지 않는다. Alertmanager의 기본 Slack 연동이 발송·재시도·경보 묶음과
해제 알림을 처리한다. BRIEF 본문이나 업무 이벤트는 이 경로로 보내지 않는다.

1. 기존 Slack 앱의 Incoming Webhooks에서 운영 채널의 웹훅을 준비한다.
   [공식 등록 절차](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/)를 따른다.
   URL은 비밀이므로 저장소 밖 파일에 저장하고 Alertmanager 안의
   `/run/secrets/brief-slack-webhook-url`에 읽기 전용으로 연결한다. 실행 사용자만 읽게 한다.
2. [Slack 설정 예시](../../ops/alertmanager/brief-slack.example.yml)의 `route.routes` 항목과
   `brief-slack` 수신처를 공용 Alertmanager 설정에 합친다. BRIEF 경로는 먼저 일치하는 포괄 경로보다
   앞에 둔다. `unmatched`는 예시 검사용 수신처이므로 기존 기본 수신처를 덮어쓰지 않는다.
3. [Prometheus 연결 대상](../../ops/prometheus/alertmanager-targets.yml)의 빈 목록을 실제 비공개
   Alertmanager 주소로 바꾼다. `targets`에는 URL 경로 없이 `호스트:포트`를 넣는다.

   ```yaml
   - targets: [alertmanager.internal.example:9093]
   ```

4. 연결은 HTTPS이며 인증서의 호스트명을 검증한다. 사설 CA를 쓰면 `prometheus.yml`의 해당
   `alertmanagers` 항목에 `tls_config.ca_file`로 신뢰할 CA 파일을 지정한다. 수신처가 인증을 요구하면
   같은 항목에 표준 `basic_auth` 또는 `authorization.credentials_file`을 설정한다.
   인증서 검증을 끄거나 관리 포트를 공개하지 않는다. Alertmanager의 Slack 발송 경로만 외부 HTTPS가 필요하다.
5. 실행 버전의 `promtool check config`와 `amtool check-config`로 설정을 확인하고 적용한다.
   연결 대상 파일의 내용 변경은 자동 반영된다. CA·인증 등 본 설정 변경은 Prometheus 재적용이 필요하다.
   승인된 시험 경보로 Slack 도착과 해제 알림을 확인한 뒤 사용한다.

현재 RELAY의 선택적 Alertmanager는 RELAY 컨테이너의 loopback에 바인딩한다. BRIEF에서 그 주소에
바로 연결할 수는 없다. 향후 공용 감시 구성에 비공개 HTTPS 연결 경로를 마련할 때 위 설정을 사용한다.
k3s에서도 같은 Prometheus·Alertmanager 설정을 사용할 수 있으며 설치 매니페스트는 추가하지 않았다.

Prometheus는 경보에 `service=brief`를 붙인다. Slack 수신 경로는 이 값만 선택하며 다른 서비스 경보는
기존 경로로 처리한다. 최초 30초 동안 같은 종류의 경보를 묶고, 변경은 5분 간격, 지속 경보는 4시간마다
알린다. 장애 조건이 사라지면 해제 메시지를 보낸다. 공백·이벤트 거부 경보의 해제는 관측 구간에서
새 발생이 없다는 뜻이며 원본 문제 해결을 보장하지 않는다.

기본 연결 대상은 `[]`이므로 설정을 추가하기 전에는 외부로 보내지 않는다. 중단할 때도 이 파일을
`[]`로 되돌린다. 이미 Alertmanager에 전달된 경보는 만료되기 전까지 남을 수 있으므로 즉시 발송 중단이
필요하면 Alertmanager에서 `service=brief`를 일시 중지한다. 로컬 지표 수집과 경보 판정은 계속된다.

## 비용과 검증 범위

Slack Incoming Webhook과 자체 호스팅 Alertmanager를 사용하며 별도 유료 발송 서비스는 추가하지 않는다.
[Slack 무료 플랜](https://slack.com/help/articles/115002422943-Usage-limits-for-free-workspaces)은 앱 최대 10개와
최근 90일 메시지 조회 제한이 있다. 기존 앱을 재사용하고, 한도 때문에 유료 전환하지 않는다.
홈서버 자원·네트워크 사용과 Slack 요청 제한은 적용된다.

CI는 Prometheus 설정·경보 규칙과 Alertmanager 설정·서비스별 분기를 검사한다.
실제 Slack 워크스페이스 인증·채널 권한·도착 여부는 웹훅을 연결한 뒤 확인한다.
현재 검증 결과는 [HANDOFF](../../HANDOFF.md)를 따른다.

- [Alertmanager Slack 기본 연동](https://prometheus.io/docs/alerting/latest/configuration/#slack_config)
- [수집·경보 조건](diagnostics-and-metrics.md)
- [PostgreSQL 백업·복원](postgresql-backup-restore.md)
