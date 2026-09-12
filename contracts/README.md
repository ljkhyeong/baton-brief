# BATON BRIEF 이벤트 계약 팩

이벤트 v2 요청 형식을 JSON Schema와 예시로 제공한다. BRIEF는 `VERSION`에 명시된 계약을
사용한다. BATON은 사용할 계약 버전을 고정하고 해당 버전의 예시로 실제 직렬화·outbox·전달 처리를 검증한다.

## 포함 파일

- `schemas/source-event.v2.schema.json`: `POST /api/v1/events`에 전달하는 이벤트 v2 요청
- `examples/*.json`: BATON 다섯 신호와 `ROLE_UNASSIGNED`의 심각도 변경·해소 예시
- `VERSION`: 계약 팩 버전의 단일 기준
- ZIP에는 [이벤트 v2 수신 계약](../docs/PRD/0019_baton-continuity-event-v2/spec.md)과
  직접 참조하는 [MVP 계약](../docs/PRD/0002_mvp-contract/spec.md),
  [수신 기록 조회](../docs/PRD/0007_event-receipt-query/spec.md),
  [BATON 이벤트 연동 조건](../docs/PRD/0018_baton-producer-compatibility/spec.md)을 원래 경로로 포함한다.

스키마는 개별 필드 형식과 v2 열거형을 정의한다. 같은 `sourceReference`의 리비전 증가,
`ACTIVE`·`RESOLVED` 상태 변경, 멱등성·충돌·HTTP 응답과 재구축 규칙은 PRD-0019가 기준이다.

Draft 2020-12의 `format`은 기본적으로 주석이므로 계약 검증기는 `format-assertion`을
활성화해야 한다. UUID와 `date-time`을 실제 제약으로 검사하지 않는 기본 설정만으로 계약
일치를 판단하지 않는다.

JSON 객체의 각 필드명은 한 번만 보낸다. 같은 필드명을 반복하면 값이 같아도 BRIEF는
저장 전에 `400 Bad Request`로 거부한다. 같은 이벤트를 다시 보내는 멱등 수신 규칙은 유지한다.

JSON Schema는 숫자의 표기 형태를 구분하지 않으므로 수학적으로 같은 `2`와 `2.0`, `1`과
`1.0`을 같은 값으로 다룬다. 생산자는 `eventVersion`과 `aggregateRevision`을 소수점이나
지수 표기가 없는 JSON 정수 token으로 직렬화해야 한다. BRIEF는 소수 형태를 정수로
변환하지 않고 `400 Bad Request`로 거부한다. `sourceReference`의 최대 128자는 Unicode
code point를 기준으로 하며 `U+0000`과 짝이 없는 UTF-16 surrogate는 허용하지 않는다.
정상 surrogate 쌍으로 표현되는 이모지는 허용한다. 공백 판정은 기존 수신과 같은 JDK 21
`Character.isWhitespace` 기준이며 NBSP(`U+00A0`)는 원문 그대로 허용한다. 스키마는 해당
공백 문자와 surrogate 쌍을 명시해 Java·ECMAScript의 기본 문자 분류 차이를 피한다.
`eventId`, `workspaceId`, `seasonId`는 ASCII 16진수의 36자 `8-4-4-4-12` 하이픈 UUID로
직렬화한다.

v2 계약은 `aggregateRevision`을 JVM `Long`·PostgreSQL `BIGINT`와 같은 부호 있는 64비트
양수 범위로 제한하고, 계약에 없는 필드와 정수가 아닌 숫자를 거부한다. 현재 계약 버전과
생산자 교차 검증 범위는 각각 `VERSION`과 BRIEF 저장소의 `HANDOFF.md`를 기준으로 확인한다.
BATON 실제 serializer·outbox·송신기와 전용 Bearer를 포함한 현재 버전의 생산자 재검증과
실제 스테이징 공인 HTTPS 전달을 확인하기 전에는 안정 버전으로 올리지 않는다.

## 검증과 생성

```shell
./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest'
./gradlew --no-daemon contractsZip
```

`contractsZip`은 `build/distributions/baton-brief-contracts-<VERSION>.zip`에
`contracts/**`와 위 PRD 네 문서를 재현 가능한 순서와 고정된 파일 시각으로 묶는다.
ZIP에 포함한 문서의 상대 링크는 ZIP 안에서도 열 수 있어야 한다. 공유 JVM DTO JAR,
별도 배포 플러그인과 계약 서비스는 만들지 않는다.
