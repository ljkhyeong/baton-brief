# PRD-0024: BATON 주도 BRIEF 브리프 생성 계약

- 상태: 채택됨
- 결정일: 2026-08-28
- 범위: BATON이 생성 대상·시점을 정하고 BRIEF에 주간 브리프 생성을 요청하는 방식

## 목적

BATON이 작업공간·시즌·시간대와 생성 시점을 정해 BRIEF에 요청한다.
BRIEF는 정해진 규칙으로 브리프를 만들고, 같은 범위의 직전 생성 상태가 같으면 기존 브리프를 재사용한다.

## 책임 분리

BATON이 담당한다.

- 생성 대상 작업공간·시즌의 존재와 운영 상태
- 시즌 IANA 시간대와 월요일 `weekStart` 계산
- 예약·수동·누락 보정의 실행 조건과 최소 한 번 호출 기록
- 사용자·운영자 권한, 실패 재시도와 실행 결과 기록

BRIEF가 담당한다.

- 기존 `POST /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions` 명령
- 주간 `[windowStart, windowEnd)`, `sourceCursor`, 규칙에 따른 항목 선정과 정렬
- 불변 항목·`stateFingerprint`, 작업공간·시즌별 `generation`과 동시 생성 직렬화
- 같은 범위의 직전 상태 재사용과 `A → B → A` 상태 복귀 시 새 브리프 생성

BRIEF에 대상 registry, 시즌 조회 client, `@Scheduled` 작업과 별도 생성 큐를 추가하지 않는다.

## 생성 명령

BATON은 권한과 대상 상태를 확인한 뒤 다음 기존 요청을 호출한다.

```http
POST /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions
Content-Type: application/json

{
  "weekStart": "2026-08-24",
  "zoneId": "Asia/Seoul"
}
```

- `workspaceId`와 `seasonId`는 BATON이 관리하는 작업공간·시즌에서 가져온다.
- `weekStart`는 해당 시즌 시간대의 월요일이고 `zoneId`는 이름이 있는 IANA 시간대다.
- `201 Created`는 새 브리프 생성, `200 OK`는 같은 범위의 직전 상태 재사용이며 둘 다 완료다.
- 응답의 `editionId`, `generation`, `sourceCursor`와 `ETag`를 BATON 실행 결과에 연결한다.
- 같은 명령을 재시도하는 동안 BRIEF 투영이 바뀌면 새 브리프가 생성될 수 있다. BATON은 요청
  값만으로 브리프 식별자가 영원히 같다고 가정하지 않고 실제 응답을 사용한다.

별도 `Idempotency-Key`나 BATON 실행 식별자를 BRIEF 요청에 추가하지 않는다. BRIEF의 기존
상태 지문과 생성 잠금이 반복·동시 호출의 저장 중복을 막는다.

## 브리프 생성 전 이벤트 전달 확인

BRIEF의 `sourceCursor`만으로 BATON 이벤트의 전달 완료를 판단할 수 없다.
BATON은 정기 브리프에 반영할 이벤트가 모두 전달됐는지 저장된 outbox·실행 기록으로 확인한 뒤
생성을 요청한다.

전달 완료를 확인하지 못해도 운영자가 수동 생성할 수는 있다.
이 경우 모든 원본 이벤트가 반영된 브리프로 안내하지 않는다. 향후 생산자 워터마크를 도입하면 BRIEF의
`sourceCursor`와 다른 필드·계약으로 정의한다.

## 인증과 실패 분류

- BATON→BRIEF 생성은 이벤트 수신 Bearer와 다른 서비스 자격 증명과 비공개 경로를 사용한다.
- `200`·`201`은 완료다.
- 요청 의미가 잘못된 `400`은 같은 본문의 자동 재시도로 고치지 않고 BATON 대상·시간대
  설정을 확인한다.
- 인증 실패는 자격 증명·배포 오류로 분류하고 사용자 요청 성공으로 바꾸지 않는다.
- 네트워크 실패와 `5xx`는 같은 대상 명령을 다시 실행할 수 있다. BRIEF의 멱등 생성이 중복
  브리프를 막는다.
- 정확한 재시도 간격·최대 횟수·운영 경보는 실제 배포 SLO와 함께 별도 결정한다.

## 조회 연결

생성 성공 뒤 BATON UI는 PRD-0023에 따라 BATON 백엔드를 통해 반환된 브리프나 주간 최신
브리프를 조회한다. UI가 BRIEF 생성 API를 직접 호출하거나 생성 시점을 결정하지 않는다.

## 수용 기준

- BATON이 작업공간·시즌·시간대와 실행 시점을 선택하고 BRIEF는 그 정보를 추측하지 않는다.
- 예약·수동·재시도가 같은 BRIEF 생성 명령과 결과 분류를 사용한다.
- 같은 투영 상태의 반복·동시 호출은 기존 브리프를 재사용하고, 상태가 바뀌면 새
  `generation`을 반환한다.
- BATON은 이벤트 전달 완료와 브리프 생성 결과를 저장해 함께 추적할 수 있게 한다.
- BRIEF에 scheduler, 대상 registry, 별도 생성 테이블이나 새 멱등 키를 추가하지 않는다.
- 실제 BATON 실행→BRIEF 생성→BATON 경유 조회의 정상·응답 유실·재시도 종단 간 시나리오를
  검증하기 전에는 구현 완료로 표시하지 않는다.

## 제외 범위

- 이번 BRIEF 변경에서 BATON scheduler·실행 테이블·client와 서비스 인증을 구현하는 작업
- 숫자 cron, 재시도 횟수, 완료 SLO와 사용자 알림의 임의 채택
- BRIEF가 BATON 시즌·멤버십 데이터베이스를 조회하는 동작
- 브리프 내용 수정, 예약 브리프, 초안·발행 상태와 삭제 API
- 브로커, 새 생성 endpoint, 새 스키마·마이그레이션과 배포 자동화

## 관련 문서

- [BATON 백엔드 경유 조회 계약](../0023_baton-mediated-brief-query/spec.md)
- [BATON의 사용자 권한 확인·브리프 생성 요청](../../ADR/0006_baton-brief-application-boundary/adr.md)
- [MVP 이벤트·투영·브리프 계약](../0002_mvp-contract/spec.md)
- [주간 범위 최신 브리프 조회](../0009_weekly-latest-edition/spec.md)
- [BATON 서비스 API 인증과 비공개 연결](../0025_baton-service-api-security/spec.md)
