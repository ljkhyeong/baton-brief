# 수신 진단·재구축과 지표 조회

## 실행 권한과 기록

[ADR-0008](../ADR/0008_host-authorized-operations/adr.md)에 따라 승인된 배포 호스트의 셸·
Docker 실행 권한으로만 실행한다. 실행자, 대상 환경·이미지 digest, 명령 종류, 시작·완료
시각과 종료 코드를 호스트의 운영 기록에 남긴다. 조회 결과 파일은 접근을 제한하며 비밀·
원본 참조를 메트릭 레이블이나 일반 애플리케이션 로그에 복사하지 않는다.

아래 명령은 실제 대상의 `.env.staging`과 같은 이미지·DB 설정을 사용한다. 운영 명령은
HTTP 서버를 열지 않고 Flyway를 실행하지 않는다. 애플리케이션 배포로 마이그레이션을 완료한
뒤 실행하며 조회·재구축은 현재 DB 스키마에 맞는 이미지를 사용한다.

## 수신 기록 단건·이상 기록 조회

```shell
docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps -T brief \
  --spring.profiles.active=operations \
  --brief.operations.command=RECEIPT \
  --brief.operations.event-id=<이벤트-UUID>

docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps -T brief \
  --spring.profiles.active=operations \
  --brief.operations.command=ANOMALIES \
  --brief.operations.workspace-id=<작업공간-UUID> \
  --brief.operations.season-id=<시즌-UUID> \
  --brief.operations.limit=20
```

`operations` 프로필은 성공 결과 JSON 한 건만 표준 출력으로 보내고 시작·종료·오류 로그는
표준 오류로 보낸다. `-T`는 가상 터미널을 끄고 두 출력을 분리한다. 다른 로그 설정을 지정할
때도 이 구분을 유지한다.

결과를 저장하려면 접근이 제한된 디렉터리에서 `umask 077`을 적용하고 위 명령 끝에
`> receipt.json 2> receipt.log`를 붙인다. 종료 코드가 `0`일 때만 결과 파일을 사용한다.
`jq` 등 다른 도구와 연결할 때는 Bash에서 `set -o pipefail`을 켜 명령 실패를 놓치지 않는다.

결과는 기존 조회 API와 같은 JSON이다. 과거 페이지는 반환된
`nextBeforeIngestionSequence`를 `--brief.operations.before-ingestion-sequence`로 넘긴다.
첫 페이지를 다시 조회하려면 커서를 생략한다. 조회 결과는 최초 수신 결과를 유지하며
충돌 지문과 원문 payload를 포함하지 않는다.

`RECEIPT`는 해당 이벤트의 수신 기록이 없으면 오류로 종료한다. `ANOMALIES`는 조건에 맞는
기록이 없으면 빈 `receipts`와 `nextBeforeIngestionSequence=null`을 반환하고 정상 종료한다.
입력이나 처리에 오류가 있으면 두 명령 모두 0이 아닌 종료 코드로 끝난다.

## 전체 재구축

진단 결과 재구축이 필요할 때만 다음 명령을 실행한다. 실행 전 대상과 최근 백업을 확인하고
수신·생성의 잠금 대기를 고려해 운영 시간을 정한다. 재구축은 누락 이벤트를 생성하지 않으며
이미 기록된 `revisionGap`도 지우지 않는다. 미지원·충돌 기록은 임의로 재처리하지 않는다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps -T brief \
  --spring.profiles.active=operations \
  --brief.operations.command=REBUILD
```

성공하면 처리한 수신 기록 수(`receiptCount`)와 점검 항목 수(`itemCount`)를 출력하고 종료한다.
전역 잠금과 한 트랜잭션을 사용하며, 실패하면 재구축 전의 점검 항목으로 롤백한다.
수신 기록과 기존 브리프는 보존한다.

## 추가 이용료 없는 지표 수집

Compose에 지표 수집 설정을 추가한다. 서비스 API를 함께 사용하면 기존
`-f compose.service-api.yml`도 같은 명령에 유지한다.

```shell
docker compose --env-file .env.staging \
  -f compose.staging.yml -f compose.observability.yml config --quiet
docker compose --env-file .env.staging \
  -f compose.staging.yml -f compose.observability.yml up --build -d --wait
docker compose --env-file .env.staging \
  -f compose.staging.yml -f compose.observability.yml \
  exec -T brief wget -q -T 10 -O - http://127.0.0.1:9091/actuator/prometheus
```

관리 포트는 컨테이너 내부 loopback에만 바인딩한다. `/actuator/health`도 관리 포트로
이동하므로 `compose.observability.yml`의 healthcheck를 함께 적용해야 한다.
이 파일을 사용하지 않는 기본 구성은 기존 health 경로를 유지한다.

같은 Compose 구성의 Prometheus가 BRIEF의 네트워크 공간에서 30초마다 지표를 수집한다.
계정·API 키·외부 저장소가 필요 없으며, BRIEF와 수집기 모두 호스트 포트와 외부 송신 경로가 없다.
Prometheus는 비루트·읽기 전용으로 실행하고 전용 볼륨에 지표를 저장한다.
애플리케이션 재배포 때는 위 명령으로 두 서비스를 함께 갱신해 네트워크 연결도 맞춘다.

보관 기준은 7일 또는 1GB 중 먼저 도달하는 값이다. 1GB는 디스크 사용량의 강제 상한이 아니며
WAL·작업 파일 여유 공간이 추가로 필요하다. 메모리는 256MB, CPU는 0.5개로 제한했다.
추가 서비스 이용료는 없지만 기존 서버의 자원을 사용하므로 실제 부하에 맞춰 조정한다.
보관 방식은 [Prometheus 공식 문서](https://prometheus.io/docs/prometheus/latest/storage/)를 따른다.

수집 상태와 현재 경보는 호스트 권한으로 조회한다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml -f compose.observability.yml \
  exec -T brief wget -q -T 10 -O - http://127.0.0.1:9090/api/v1/targets
docker compose --env-file .env.staging -f compose.staging.yml -f compose.observability.yml \
  exec -T brief wget -q -T 10 -O - http://127.0.0.1:9090/api/v1/alerts
```

[경보 규칙](../../ops/prometheus/alerts.yml)은 다음 조건에서 작동한다.

| 경보 | 조건 |
| --- | --- |
| `BriefMetricsUnavailable` | 수집 실패 또는 `brief` 수집 대상 지표 누락이 2분간 지속 |
| `BriefServerErrors` | 최근 5분의 HTTP `5xx` 발생 건수가 5건 이상인 상태가 1분간 지속 |
| `BriefEventRejected` | 최근 5분의 `CONFLICT` 또는 `UNSUPPORTED` 카운터 증가가 감지된 상태가 1분간 지속 |
| `BriefDatabaseConnectionWait` | DB 연결을 기다리는 요청이 있는 상태가 연결 풀별로 2분간 지속 |
| `BriefEventRevisionGap` | 최근 5분의 `APPLIED_WITH_GAP` 카운터 증가가 감지된 상태가 1분간 지속 |

이벤트 거부 경보는 `outcome`으로 충돌과 미지원을 구분한다. HTTP `409`·`422`도 확인할 수
있으며, BATON의 이벤트 버전·본문과 BRIEF 수신 기록을 조사한다. 정상 적용·중복·오래된 리비전은
이 경보에 포함하지 않는다. 최근 5분에 증가가 없으면 해제되며 미해결 오류 목록을 뜻하지 않는다.

변경 번호 공백 경보는 HTTP `202`로 적용한 이벤트 중 앞선 원본 변경 번호를 받지 못한 경우를 알린다.
이상 수신 기록의 `APPLIED_WITH_GAP`과 BATON의 outbox 전달 상태를 확인해 전달 지연·역순 도착·누락을
구분한다. 경보 자체에는 작업공간이나 원본 참조를 넣지 않는다.
최근 5분에 새 공백 탐지가 없으면 경보가 해제된다. 이미 기록된 공백이 해소됐거나 모든 이벤트가
전달됐다는 뜻은 아니며, 재구축으로 누락된 이벤트를 복구할 수도 없다.

DB 연결 대기는 기본 HikariCP 지표인 `hikaricp_connections_pending`으로 확인한다.
경보의 `instance`·`pool`로 대기 중인 연결 풀을 찾고, DB 상태·장기 쿼리·잠금을 확인한다.
연결이 모두 사용 중이어도 대기 요청이 없으면 경보하지 않으며, 대기가 없어지면 해제한다.
이 값만으로 원인을 DB 장애나 연결 수 부족으로 단정하지 않는다.

수집 시작 전의 오류나 수집 사이에 프로세스가 재시작되며 사라진 오류는 놓칠 수 있다.
외부 알림은 [공용 Alertmanager·Slack·Discord 연결 설정](external-integrations.md)을 제공하며 실제 수신처는
미연결이다. 같은 서버의 Prometheus로 서버 전체 장애를 감지할 수는 없다. BRIEF 본문 발송은 RELAY가 담당한다.

`brief_events_received_total{outcome="..."}`은 결과별 요청 수이며 고유 이벤트 수가 아니다.
기동 시 여섯 결과를 `0`으로 등록한다. 재시작 초기화·수집 실패·업무 이벤트 없음은
서로 다르게 다뤄야 한다. `CONFLICT`·`UNSUPPORTED`·`APPLIED_WITH_GAP` 증가와 HTTP 인증 실패·`5xx`·지연을
운영 조사 근거로 수집한다. 경보 주기·임계값은 실제 트래픽과 운영 목표에 맞춰 설정한다.

BATON 호스트에서는 기존 `ops/check-integration-delivery.sh`로 영구 실패·만료된 처리 임대·
지표 갱신 실패를 확인한다. `ops/show-integration-metrics.sh`의 `integration="brief"`
값과 함께 보되, BRIEF 카운터만으로 BATON의 최신 이벤트가 모두 전달됐다고 판단하지 않는다.

로컬 수집·규칙 검증과 실제 서버의 감시·알림 운영은 구분한다. 현재 검증과 미연결 범위는
[HANDOFF](../../HANDOFF.md)를 따른다.
