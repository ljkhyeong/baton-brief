# BATON BRIEF 이벤트 계약 팩

이 디렉터리는 PRD-0019의 BATON 연속성 신호 이벤트 v2 요청 계약을 언어 중립적인
JSON Schema와 예시로 제공한다. BRIEF 소비자는 `VERSION`의 현재 계약을 사용한다. BATON
실제 serializer·outbox·송신기는 별도로 고정한 계약 버전과 같은 예시를 사용해야 한다.

## 포함 파일

- `schemas/source-event.v2.schema.json`: `POST /api/v1/events`에 전달하는 이벤트 v2 요청
- `examples/*.json`: BATON 다섯 신호와 `ROLE_UNASSIGNED`의 심각도 변경·해소 예시
- `VERSION`: 계약 팩 버전의 단일 기준

스키마는 개별 필드 형식과 v2 열거형을 정의한다. 같은 `sourceReference`의 리비전 증가,
`ACTIVE`·`RESOLVED` 생명주기, 멱등성·충돌·HTTP 결과와 재구축 의미는 PRD-0019가 기준이다.

Draft 2020-12의 `format`은 기본적으로 주석이므로 계약 검증기는 `format-assertion`을
활성화해야 한다. UUID와 `date-time`을 실제 제약으로 검사하지 않는 기본 설정만으로 계약
일치를 판단하지 않는다.

JSON Schema는 숫자의 표기 형태를 구분하지 않으므로 수학적으로 같은 `2`와 `2.0`, `1`과
`1.0`을 같은 값으로 다룬다. 생산자는 `eventVersion`과 `aggregateRevision`을 소수점이나
지수 표기가 없는 JSON 정수 token으로 직렬화해야 한다. BRIEF는 소수 형태를 정수로
변환하지 않고 `400 Bad Request`로 거부한다. `sourceReference`의 최대 128자는 Unicode
code point를 기준으로 한다.

현재 버전 `2.0.0-rc.2`는 `aggregateRevision`을 JVM `Long`·PostgreSQL `BIGINT`와 같은
부호 있는 64비트 양수 범위로 제한하고, 계약에 없는 필드와 정수가 아닌 숫자를 거부하도록
소비자 수신 경계를 맞춘 사전 버전이다. BRIEF 소비자와 계약 팩 검증은 완료했지만 BATON
실제 serializer·outbox·송신기와 전용 Bearer를 포함한 교차 검증 근거는 `2.0.0-rc.1`까지다.
현재 버전의 생산자 재검증과 실제 BATON 스테이징 호스트의 공인 HTTPS 전달을 확인하기
전에는 안정 버전으로 올리지 않는다.

## 검증과 생성

```shell
./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest'
./gradlew --no-daemon contractsZip
```

`contractsZip`은 `build/distributions/baton-brief-contracts-2.0.0-rc.2.zip`에
`contracts/**`와 PRD-0019를 재현 가능한 순서와 고정된 파일 시각으로 묶는다. 공유 JVM DTO
JAR, 별도 배포 플러그인과 계약 서비스는 만들지 않는다.
