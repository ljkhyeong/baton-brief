# 수신 진단·재구축과 지표 조회

## 실행 권한과 기록

[ADR-0008](../ADR/0008_host-authorized-operations/adr.md)에 따라 승인된 배포 호스트의 셸·
Docker 실행 권한으로만 실행한다. 실행자, 대상 환경·이미지 digest, 명령 종류, 시작·완료
시각과 종료 코드를 호스트의 운영 기록에 남긴다. 조회 결과 파일은 접근을 제한하며 비밀·
원본 참조를 메트릭 레이블이나 일반 애플리케이션 로그에 복사하지 않는다.

아래 명령은 실제 대상의 `.env.staging`과 같은 이미지·DB 설정을 사용한다. 운영 명령은
HTTP 서버를 열지 않고 Flyway를 실행하지 않는다. 앱 정상 배포에서 마이그레이션을 완료한
뒤 실행하며 조회·재구축은 현재 DB 스키마에 맞는 이미지를 사용한다.

## 수신 증거와 이상 이력

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

각 결과는 기존 조회와 같은 JSON으로 표준 출력에 기록된다. 과거 페이지는 반환된
`nextBeforeIngestionSequence`를 `--brief.operations.before-ingestion-sequence`로 넘긴다.
최신 상태는 커서를 빼고 다시 조회한다. 단건 미존재·입력 오류·처리 실패는 0이 아닌 종료
코드로 끝난다. 조회 결과는 최초 수신 결과를 유지하며 충돌 지문과 원문 payload를 포함하지 않는다.

## 전체 재구축

진단 결과 재구축이 필요할 때만 다음 명령을 실행한다. 실행 전 대상과 최근 백업을 확인하고
수신·생성의 잠금 대기를 고려해 운영 시간을 정한다. 재구축은 누락 이벤트를 생산하거나
과거 `revisionGap` 증거를 지우는 수단이 아니다. 미지원·충돌 기록은 임의로 재처리하지 않는다.

```shell
docker compose --env-file .env.staging -f compose.staging.yml run --rm --no-deps brief \
  --spring.profiles.active=operations \
  --brief.operations.command=REBUILD
```

성공하면 `receiptCount`, `itemCount`를 출력하고 종료한다. 기존 전역 잠금·한 트랜잭션을
사용하며 실패 시 이전 투영으로 롤백한다. 수신 증거와 기존 에디션은 보존한다.

## 선택적인 지표 조회

실행 조립에 관측 설정을 추가한다. 서비스 API를 함께 사용하면 기존
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

관리 포트는 컨테이너 내부 loopback에만 바인딩한다. health도 같은 관리 서버로 이동하므로
이 override의 healthcheck를 함께 적용해야 한다. 기본 조립은 기존 health 경로만 유지한다.

`brief_events_received_total{outcome="..."}`은 결과별 요청 수이며 고유 이벤트 수가 아니다.
첫 수신 전에는 카운터가 아직 없을 수 있다. 재시작 초기화·수집 실패·업무 이벤트 없음은
서로 다르게 다뤄야 한다. `CONFLICT`·`UNSUPPORTED` 증가와 HTTP 인증 실패·`5xx`·지연을
운영 조사 근거로 수집한다. 경보 주기·임계값은 실제 트래픽과 운영 목표에 맞춰 설정한다.

BATON 호스트에서는 기존 `ops/check-integration-delivery.sh`로 영구 실패·만료된 처리 임대·
지표 갱신 실패를 확인한다. `ops/show-integration-metrics.sh`의 `integration="brief"`
값과 함께 보되, BRIEF 카운터만으로 BATON 전달 완료나 최신성을 선언하지 않는다.

외부 수집 저장소·알림 수신처가 연결되기 전에는 자동 감시가 완료된 상태가 아니다. 실제
배포 주소·접속 방법·수집 시스템과 비밀 파일 경로는 저장소 예시로 대체하지 않는다.
