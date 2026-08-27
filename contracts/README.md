# BATON BRIEF 이벤트 계약 팩

이 디렉터리는 PRD-0019의 BATON 연속성 신호 이벤트 v2 요청 계약을 언어 중립적인
JSON Schema와 예시로 제공한다. BRIEF 소비자와 BATON 실제 serializer·outbox·송신기는
같은 예시를 사용하며, 로컬 원본 API·초기 정합화부터 BRIEF PostgreSQL 수렴까지 검증했다.
운영 인증·HTTPS·스테이징 활성화가 완료됐다는 뜻은 아니다.

## 포함 파일

- `schemas/source-event.v2.schema.json`: `POST /api/v1/events`에 전달하는 이벤트 v2 요청
- `examples/*.json`: BATON 다섯 신호와 `ROLE_UNASSIGNED`의 심각도 변경·해소 예시
- `VERSION`: 계약 팩 버전의 단일 기준

스키마는 개별 필드 형식과 v2 열거형을 정의한다. 같은 `sourceReference`의 리비전 증가,
`ACTIVE`·`RESOLVED` 생명주기, 멱등성·충돌·HTTP 결과와 재구축 의미는 PRD-0019가 기준이다.

현재 버전 `2.0.0-rc.1`은 로컬 생산자·소비자 호환성을 검증한 사전 버전이다. 운영 인증·
HTTPS·스테이징 전달 경계를 검증하기 전에는 안정 버전으로 올리지 않는다.

## 검증과 생성

```shell
./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest'
./gradlew --no-daemon contractsZip
```

`contractsZip`은 `build/distributions/baton-brief-contracts-2.0.0-rc.1.zip`에
`contracts/**`와 PRD-0019를 재현 가능한 순서와 고정된 파일 시각으로 묶는다. 공유 JVM DTO
JAR, 별도 배포 플러그인과 계약 서비스는 만들지 않는다.
