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
docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps brief \
  --spring.profiles.active=operations \
  --brief.operations.command=RECEIPT \
  --brief.operations.event-id=<이벤트-UUID>

docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps brief \
  --spring.profiles.active=operations \
  --brief.operations.command=ANOMALIES \
  --brief.operations.workspace-id=<작업공간-UUID> \
  --brief.operations.season-id=<시즌-UUID> \
  --brief.operations.limit=20
```

결과는 기존 조회 API와 같은 JSON으로 표준 출력에 기록된다. 과거 페이지는 반환된
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
docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps brief \
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

이벤트 거부 경보는 `outcome`으로 충돌과 미지원을 구분한다. HTTP `409`·`422`도 확인할 수
있으며, BATON의 이벤트 버전·본문과 BRIEF 수신 기록을 조사한다. 정상 적용·중복·오래된 리비전은
이 경보에 포함하지 않는다. 최근 5분에 증가가 없으면 해제되며 미해결 오류 목록을 뜻하지 않는다.

수집 시작 전의 오류나 수집 사이에 프로세스가 재시작되며 사라진 오류는 놓칠 수 있다.
외부 알림 발송은 미연결이며, 같은 서버의 Prometheus로 서버 전체 장애를 감지할 수는 없다.
알림 수신 채널이 정해지면 기존 무료 채널에 연결한다. BRIEF 본문 발송은 RELAY가 담당한다.

`brief_events_received_total{outcome="..."}`은 결과별 요청 수이며 고유 이벤트 수가 아니다.
기동 시 여섯 결과를 `0`으로 등록한다. 재시작 초기화·수집 실패·업무 이벤트 없음은
서로 다르게 다뤄야 한다. `CONFLICT`·`UNSUPPORTED` 증가와 HTTP 인증 실패·`5xx`·지연을
운영 조사 근거로 수집한다. 경보 주기·임계값은 실제 트래픽과 운영 목표에 맞춰 설정한다.

BATON 호스트에서는 기존 `ops/check-integration-delivery.sh`로 영구 실패·만료된 처리 임대·
지표 갱신 실패를 확인한다. `ops/show-integration-metrics.sh`의 `integration="brief"`
값과 함께 보되, BRIEF 카운터만으로 BATON의 최신 이벤트가 모두 전달됐다고 판단하지 않는다.

로컬 수집·규칙 검증과 실제 서버의 감시·알림 운영은 구분한다. 현재 검증과 미연결 범위는
[HANDOFF](../../HANDOFF.md)를 따른다.
