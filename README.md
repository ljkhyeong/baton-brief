# BATON BRIEF

BATON BRIEF는 BATON 생태계에서 발생한 운영 사실을 설명 가능한 관심 항목으로 투영하고,
일정 시점의 불변 운영 브리프를 만드는 독립 읽기 모델 서비스다.

> 현재 상태: Kotlin/JDK 21과 PostgreSQL 18.4 기반의 내부 HTTP 이벤트 수신,
> 투영/재구축, 불변 주간 에디션, 에디션 이력 조회와 표준 요청 오류 응답 로컬 MVP에 이어
> 두 불변 에디션의 읽기 전용 비교 API와 Spring Boot Actuator 표준 aggregate health를
> 사용하는 최소 상태 확인, 최초 이벤트 수신 증거의 읽기 전용 단건 조회까지 구현했다.
> PostgreSQL 통합 테스트와 전체 테스트·실행 JAR 생성, Java 21/PostgreSQL 18.4 실행
> 점검이 통과했다. 운영 배포
> 구성은 없다. PRD-0008의 수신 증거 `retain-all`과 동기 전역 원자적 재구축 경계는
> 강제 실패 롤백과 재구축·지원 이벤트 수신 잠금 동시성 대상 테스트, 변경 뒤 전체
> 테스트·실행 JAR 생성으로 검증했다.
> PRD-0009의 주간 범위 최신 불변 에디션 조회도 구현했고 PostgreSQL 통합 테스트와 전체
> 테스트·실행 JAR 생성이 성공했다.
> PRD-0010의 새 에디션 항목 리비전 근거 고정과 이전 항목 `null` 호환성도 구현했다.
> PostgreSQL Testcontainers 빈 데이터베이스의 V1~V7 적용, 대표 V2 데이터의 V3~V7
> 업그레이드와 전체 테스트·실행 JAR 생성이 성공했다.
> PRD-0011의 작업공간·시즌별 이상 수신 증거 이력 조회도 구현했고, 기존 PostgreSQL
> 수신 시나리오와 전체 테스트·실행 JAR 생성이 성공했다.
> PRD-0012의 생성·전역 최신·주간 최신·단건 에디션 `ETag`와 표준 `If-None-Match`
> 조건부 조회도 구현했고 대상 PostgreSQL 통합 테스트와 전체 테스트·실행 JAR 생성이
> 성공했다.
> PRD-0013의 현재 관심 항목 단건 조회도 구현했고 대상 PostgreSQL 통합 테스트가
> 성공했다. 전체 테스트·실행 JAR 생성도 성공했다.
> PRD-0014의 현재 활성 관심 항목 키셋 조회도 구현했고 대상 PostgreSQL 통합 테스트가
> 성공했다. 전체 테스트·실행 JAR 생성도 성공했다.
> PRD-0015의 현재 관심 항목 상태 필터도 구현했고 대상 PostgreSQL 통합 테스트가
> 성공했다. 전체 테스트·실행 JAR 생성도 성공했다.
> PRD-0016의 실제 적용 상태 전이 증거 이력도 구현했고 대상 PostgreSQL 통합 테스트가
> 성공했다. 전체 테스트·실행 JAR 생성도 성공했다.
> PRD-0017의 현재 관심 항목 단건 `ETag`와 표준 `If-None-Match` 조건부 조회도 구현했고
> 대상 PostgreSQL 통합 테스트와 전체 테스트·실행 JAR 생성이 성공했다.
> PRD-0018에서 현재 BATON 신호와 BRIEF 이벤트 v1의 의미·정체성·리비전·전송 불일치를
> 확인하고 생산자 연동 선행조건을 채택했다. BATON은 다섯 신호·신호별 리비전·시간 재조정
> 의미를 별도 PRD로 채택했다. PRD-0019의 BRIEF 이벤트 v2 소비와 V7 저장·재구축은
> 구현·검증했다. 이벤트 v2 JSON Schema·예시를 담은 `2.0.0-rc.1` 계약 팩을 BATON의 실제
> Java/Jackson 직렬화와 대조했고, BATON 작업 브랜치에는 신호 스트림·불변 outbox와 설정형
> 시간 재조정을 구현했다. 신호에 영향을 주는 원본 변경과 자동 회차 생성도 같은
> 트랜잭션 재조정에 연결했다. V23의 lease·신호별 리비전 순서·재시도 상태와 기본 비활성
> HTTP 송신기도 구현·검증했다. 실제 BATON·BRIEF 실행 JAR과 MySQL·PostgreSQL을 연결한
> 선택 실행 테스트로 원본 API 변경, 초기 정합화, 장애 재시도, 동일 이벤트 재전달,
> 심각도 변경과 해소 투영을 검증했다. PRD-0020의 전용 Bearer 인증과 현재·직전 token을
> 함께 허용하는 수동 교체 구간도 실제 두 프로세스 흐름에서 검증했다. HTTPS·스테이징
> 활성화는 아직 없다.

## 왜 BRIEF인가

BATON, WATCH, RELAY와 GO는 각각 조직 운영 원본, 자료 상태 점검, 전달, 링크 생명주기를
소유한다. BRIEF는 그 책임을 복제하지 않고, 여러 시점의 사실을 사용자가 읽고 행동할 수
있는 하나의 운영 스냅샷으로 고정한다.

```text
BATON 도메인 이벤트 ──> BRIEF 수신함 ──> AttentionItem 투영
                                             │
                                             └─> 불변 BriefEdition
                                                        │
                                                        ├─> BATON UI
                                                        └─> BRIEF_READY -> RELAY (향후)
```

WATCH의 상태 사실은 WATCH 데이터베이스에서 직접 읽지 않는다. BATON이 수신하고
정규화한 권위 있는 도메인 이벤트를 통해서만 BRIEF로 전달한다.

## 서비스 경계

BRIEF가 소유한다.

- 원본 이벤트 수신함과 멱등 처리 결과
- 작업공간/시즌별 `AttentionItem` 읽기 모델
- 선정 규칙 버전, 이유 코드, 심각도와 원본 참조
- 작업공간/시즌의 `generation`, 로컬 수신 기록 `sourceCursor`와 재구축 상태
- 생성 시점의 항목을 고정한 불변 `BriefEdition`
- 에디션 생성 성공·실패와 재생 가능한 운영 증거

BRIEF가 소유하지 않는다.

- 팀, 시즌, 역할, 루틴, 결정, 인수인계와 최종 권한: BATON
- URL 점검 일정, 시도, 결과와 현재 상태: BATON WATCH
- 구독, 채널, 제공자 연결, 전송 재시도와 결과: BATON RELAY
- 공개 코드, 만료, 폐기와 리디렉션: BATON GO
- 운영 사실의 최종 판정이나 원본 감사 사건

## 첫 MVP

1. BATON이 커밋 후 발행한 소수의 버전이 있는 이벤트만 수신한다.
2. `reasonCode`, `severity`, `sourceReference`, `observedAt`을 가진 설명 가능한
   `AttentionItem`을 만들고 정확한 복합 정체성 단건, 현재 상태별 목록과 실제 적용 상태
   전이 증거를 조회한다.
3. 작업공간/시즌 단위의 주간 `BriefEdition`을 결정적으로 생성한다.
4. 전역 최신·주간 범위 최신·특정 에디션과 작업공간·시즌별 에디션 이력을 조회한다.
5. 동일 이벤트 재생을 멱등 처리하고 투영 전체 재구축을 지원한다.
6. 첫 소비자는 BATON UI로 제한한다. AI 요약과 외부 제공자 전달은 포함하지 않는다.

## 설계 원칙

- 서비스별 데이터베이스를 유지하고 다른 서비스의 테이블이나 엔티티를 공유하지 않는다.
- 외부 I/O는 원본 트랜잭션 안에서 수행하지 않는다.
- 최소 한 번 수신, 중복, 지연 도착과 재생을 정상 흐름으로 다룬다.
- 시간 의존 규칙에는 주입 가능한 `Clock`과 명시적인 시간대를 사용한다.
- 에디션은 생성 후 수정하지 않는다. 정정은 새 에디션 또는 명시적인 대체 관계로
  표현한다.
- 새 에디션 항목은 `aggregateRevision`과 `revisionGap`을 함께 고정한다. 이 값을 저장하지
  않았던 이전 항목은 알 수 없는 근거를 `null`로 유지하며 추정해 채우지 않는다.
- 사람에게 보이는 문장만 저장하지 않고 안정적인 이유 코드와 원본 참조를 함께
  보존한다.
- 개인 식별 정보(PII), 자격 증명, 제공자 주소와 원문 비밀 값을 이벤트, 로그, 메트릭
  레이블에 넣지 않는다.
- 명시적인 대체 계약 전에는 멱등 판정, 최초 수신 증거 조회, 재구축과 에디션
  `sourceCursor`의 근거인 모든 수신 기록 및 이벤트별 최초 충돌 한 건을 삭제·압축하지
  않는다.

## 채택한 로컬 계약

- 내부 HTTP로 이벤트 v1·v2를 수신하며, 운영 연동에서는 PRD-0020의 전용 Bearer를
  선택적으로 필수화한다.
- v1 세 타입은 기존 규칙을 유지하고, v2의 BATON 다섯 연속성 신호는 원본
  `CRITICAL`·`WARNING`을 보존해 `HIGH`·`MEDIUM`으로 일대일 표시한다.
- 이벤트 지문과 집계 리비전으로 `DUPLICATE`, `CONFLICT`, `STALE`과 리비전 공백을 구분한다.
- 이벤트 식별자로 최초 수신 결과와 제한된 충돌 탐지 시각을 조회한다. fingerprint와
  원문 payload는 조회 응답에 노출하지 않는다.
- 작업공간·시즌별로 리비전 공백, 오래된 리비전, 미지원 또는 최초 충돌이 있는 과거 수신
  증거를 `ingestionSequence` 역순으로 탐색하는 PRD-0011 계약을 채택했다. 여러 페이지는
  하나의 스냅샷이 아니며 최신 상태는 첫 페이지부터 다시 조회한다. 로컬 구현과 검증을
  완료했다.
- IANA 시간대의 월요일 시작 주간 구간과 수신 기록 `sourceCursor`로 불변 에디션을
  생성한다. 같은 요청 범위의 직전 상태는 멱등하게 재사용하고, 상태가 바뀌었다가 과거
  상태로 돌아오면 새 `generation`으로 기록한다.
- 투영 재구축, 전역 최신·특정 에디션 조회와 `generation` 기반 에디션 이력 페이지를
  제공한다.
- 기존 전역 최신 조회와 별도로 정확한 작업공간·시즌·주간·시간대의 최대 `generation`
  에디션을 저장된 불변 스냅샷으로 조회하는 PRD-0009 경로를 구현했다.
- PRD-0010은 새 에디션 항목의 집계 리비전·리비전 공백 근거를 응답과 상태 지문에
  포함하고, 근거만 달라진 항목도 비교 `changed`로 분류하도록 확장한다. 투영·에디션
  `ruleVersion`은 계속 `1`이며 로컬 구현과 검증을 완료했다.
- PRD-0012는 불변 에디션 전체 응답에 `ETag`를 제공한다. 적용 대상 `GET`은 Spring
  MVC의 표준 `If-None-Match` 처리로 같은 에디션이면 `304`, 선택된 최신 에디션이
  바뀌면 새 본문과 `200`을 반환한다.
- PRD-0013은 작업공간·시즌·이벤트 종류·원본 참조가 정확히 같은 현재 관심 항목 한 건을
  조회한다. 변경 가능한 현재 상태이며 불변 에디션이나 과거 상태 이력으로 해석하지 않는다.
- PRD-0014는 작업공간·시즌의 현재 `ACTIVE` 항목을 `eventType`·`sourceReference` 복합
  정체성 키셋으로 조회한다. 여러 페이지는 하나의 스냅샷이 아니며 최신 상태는 첫
  페이지부터 다시 조회한다.
- PRD-0015는 같은 목록의 기본 `ACTIVE` 동작을 보존하면서 `status=RESOLVED`로 현재 해소
  항목을 조회하게 한다. 해소 목록도 과거 상태 이력은 아니다.
- PRD-0016은 같은 복합 정체성에 실제 적용된 상태 전이를 집계 리비전 내림차순으로
  조회한다. `STALE`·`UNSUPPORTED`, 중복과 충돌은 전이에서 제외한다.
- PRD-0017은 현재 관심 항목 단건 응답에 규칙 버전과 마지막 적용 리비전에 결합한
  `ETag`를 제공한다. 같은 현재 표현은 Spring MVC의 표준 `If-None-Match` 처리로 `304`,
  후속 적용 리비전으로 표현이 바뀌면 새 본문과 `200`을 반환한다.
- 재구축은 `UNSUPPORTED`를 제외한 보존 수신 기록을 순서대로 재생하는 동기 전역 명령이다.
  현재 투영만 전역 잠금과 하나의 트랜잭션으로 교체하며 수신 증거와 불변 에디션은
  바꾸지 않는다. 숫자 TTL과 재구축 SLO는 아직 정하지 않았다.
- 저장된 같은 작업공간·시즌의 두 불변 에디션을 안정적인 항목 키와 스냅샷 순서로
  비교하는 읽기 전용 API를 제공한다.
- 요청 검증 실패와 명시적 미존재 응답은 RFC 9457 `ProblemDetail` 형식으로 제공한다.
- `/actuator/health`는 프로세스와 필수 데이터베이스의 Spring Boot 표준 aggregate 상태만
  노출하고 상세와 배포 probe는 노출하지 않는다.
- 이벤트와 생성 시각은 PostgreSQL `TIMESTAMPTZ` 의미와 맞게 마이크로초로 정규화하고,
  HTTP 시간·날짜·시간대 입력은 엄격한 문자열 형식으로 검증한다.

정확한 경로, 필드, 결과와 비목표는 [MVP 계약](docs/PRD/0002_mvp-contract/spec.md),
[에디션 이력 조회 계약](docs/PRD/0003_edition-history/spec.md)과
[표준 요청 오류 계약](docs/PRD/0004_problem-detail/spec.md),
[불변 에디션 비교 계약](docs/PRD/0005_edition-comparison/spec.md),
[최소 상태 확인 계약](docs/PRD/0006_minimum-health/spec.md),
[이벤트 수신 증거 조회 계약](docs/PRD/0007_event-receipt-query/spec.md)과
[수신 증거 보존·재구축 운영 경계](docs/PRD/0008_retention-rebuild-boundary/spec.md),
[주간 범위 최신 불변 에디션 조회 계약](docs/PRD/0009_weekly-latest-edition/spec.md)과
[불변 에디션 리비전 근거 계약](docs/PRD/0010_edition-revision-evidence/spec.md),
[이상 이벤트 수신 증거 이력 조회 계약](docs/PRD/0011_event-receipt-anomalies/spec.md)과
[불변 에디션 조건부 조회 계약](docs/PRD/0012_edition-etag/spec.md),
[현재 관심 항목 단건 조회 계약](docs/PRD/0013_current-attention-item/spec.md)과
[현재 활성 관심 항목 키셋 조회 계약](docs/PRD/0014_active-attention-items/spec.md)과
[현재 관심 항목 상태 필터 조회 계약](docs/PRD/0015_attention-item-status-filter/spec.md),
[현재 관심 항목 상태 전이 증거 이력 계약](docs/PRD/0016_attention-item-transitions/spec.md)과
[현재 관심 항목 조건부 조회 계약](docs/PRD/0017_current-attention-item-etag/spec.md)과
[BATON 생산자 호환성 선행조건](docs/PRD/0018_baton-producer-compatibility/spec.md)과
[BATON 연속성 신호 이벤트 v2 소비 계약](docs/PRD/0019_baton-continuity-event-v2/spec.md)과
[BATON 이벤트 수신 인증 계약](docs/PRD/0020_baton-event-authentication/spec.md)을
따른다.
이벤트 수신 외의 인증·인가, 브로커, 스케줄러와 운영 배포는 첫 MVP 범위가 아니다.

## 이벤트 v2 계약 팩

[이벤트 계약 팩](contracts/README.md)은 BATON 생산자가 사용할 v2 요청 JSON Schema와
다섯 신호·심각도 변경·해소 예시를 제공한다. 현재 `2.0.0-rc.1`은 로컬 생산자·소비자와
Bearer 인증까지 검증한 사전 버전이다. 실제 HTTPS 스테이징 호환 완료를 뜻하지 않는다.

```shell
./gradlew --no-daemon :bootstrap:test --tests 'com.personal.baton.brief.BriefEventContractTest'
./gradlew --no-daemon contractsZip
```

생성 파일은 `build/distributions/baton-brief-contracts-2.0.0-rc.1.zip`이다.

## 문서

- [제품 기준](docs/PRD/0001_product-baseline/spec.md)
- [MVP 이벤트·투영·에디션 계약](docs/PRD/0002_mvp-contract/spec.md)
- [불변 에디션 이력 조회 계약](docs/PRD/0003_edition-history/spec.md)
- [표준 요청 오류 응답 계약](docs/PRD/0004_problem-detail/spec.md)
- [불변 에디션 비교 계약](docs/PRD/0005_edition-comparison/spec.md)
- [최소 상태 확인 계약](docs/PRD/0006_minimum-health/spec.md)
- [이벤트 수신 증거 조회 계약](docs/PRD/0007_event-receipt-query/spec.md)
- [수신 증거 보존·재구축 운영 경계](docs/PRD/0008_retention-rebuild-boundary/spec.md)
- [주간 범위 최신 불변 에디션 조회 계약](docs/PRD/0009_weekly-latest-edition/spec.md)
- [불변 에디션 리비전 근거 계약](docs/PRD/0010_edition-revision-evidence/spec.md)
- [이상 이벤트 수신 증거 이력 조회 계약](docs/PRD/0011_event-receipt-anomalies/spec.md)
- [불변 에디션 조건부 조회 계약](docs/PRD/0012_edition-etag/spec.md)
- [현재 관심 항목 단건 조회 계약](docs/PRD/0013_current-attention-item/spec.md)
- [현재 활성 관심 항목 키셋 조회 계약](docs/PRD/0014_active-attention-items/spec.md)
- [현재 관심 항목 상태 필터 조회 계약](docs/PRD/0015_attention-item-status-filter/spec.md)
- [현재 관심 항목 상태 전이 증거 이력 계약](docs/PRD/0016_attention-item-transitions/spec.md)
- [현재 관심 항목 조건부 조회 계약](docs/PRD/0017_current-attention-item-etag/spec.md)
- [BATON 생산자 호환성 선행조건](docs/PRD/0018_baton-producer-compatibility/spec.md)
- [BATON 연속성 신호 이벤트 v2 소비 계약](docs/PRD/0019_baton-continuity-event-v2/spec.md)
- [BATON 이벤트 수신 인증 계약](docs/PRD/0020_baton-event-authentication/spec.md)
- [이벤트 v2 계약 팩](contracts/README.md)
- [마이크로서비스 경계](docs/ADR/0001_microservice-boundary/adr.md)
- [기술 스택과 모듈 경계](docs/ADR/0002_technology-stack/adr.md)
- [BATON 이벤트 전용 Bearer 인증](docs/ADR/0003_baton-event-authentication/adr.md)
- [인수인계](HANDOFF.md)

## 기술 스택

ADR-0002에서 다음 기준을 채택했다.

- Kotlin/JVM 2.3.21, Java 21 도구 체인·JVM 21 바이트코드 대상·JDK 21 실행 환경
- Spring Boot/BOM 4.1.0, Gradle wrapper 9.2.1과 Kotlin DSL
- PostgreSQL 18.4
- Spring JDBC `JdbcClient`와 Flyway 마이그레이션, JPA 미사용
- Spring Boot Actuator 표준 aggregate health와 자동 구성된 DB contributor
- Spring Boot BOM으로 버전을 관리하는 JUnit Jupiter, AssertJ와 PostgreSQL Testcontainers
- `domain`, `application`, `adapter-in-web`, `adapter-out-persistence`, `bootstrap`의 다섯
  Gradle 모듈

의존은 `bootstrap`과 어댑터에서 `application`, 다시 `domain` 쪽으로만 향한다. 두
어댑터는 서로 의존하지 않고 안쪽 모듈은 바깥쪽 모듈을 참조하지 않는다.

Java 25 호환 빌드와 기동은 검토 과정에서 확인했지만 MVP에 필요한 기능상 이점이나
고정된 CI/실행 이미지 기준이 없어 Java 21을 채택했다. 특정 JDK 공급자, 실행
컨테이너 이미지와 운영 배포 방식은 아직 채택하지 않았다. 현재 구현·검증 상태는
`HANDOFF.md`에 기록한다.

## 로컬 데이터베이스

로컬 PostgreSQL 18.4는 저장소의 `compose.yml`로 준비한다.

```shell
docker compose up -d --wait postgres
./gradlew :bootstrap:bootRun
```

기본 연결은 `jdbc:postgresql://localhost:5432/baton_brief`, 사용자와 비밀번호는 각각
`brief`이며 Flyway는 기본 활성화된다. 다른 환경에서는 Spring Boot 표준 환경변수
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`와
`SPRING_FLYWAY_ENABLED`로 덮어쓴다. `BRIEF_DB_PORT`는 Compose가 호스트에 공개할 PostgreSQL
포트만 바꾸며, 바꾼 경우 데이터 원본 URL도 같은 포트로 지정해야 한다.

이벤트 수신 인증은 로컬에서 기본 비활성이다. 활성화하려면
`BRIEF_EVENT_RECEIVER_AUTHENTICATION_REQUIRED=true`와 32~200자의 URL-safe ASCII
`BRIEF_EVENT_RECEIVER_BEARER_TOKEN`을 함께 설정한다. 보호 범위는 `POST /api/v1/events`이며
다른 API의 운영 권한을 의미하지 않는다.

중단 없이 token을 바꿀 때는 BRIEF의 `BRIEF_EVENT_RECEIVER_BEARER_TOKEN`에 새 값을,
`BRIEF_EVENT_RECEIVER_PREVIOUS_BEARER_TOKEN`에 기존 값을 먼저 배포한다. BATON의
`BATON_BRIEF_BEARER_TOKEN`을 새 값으로 바꾼 뒤 직전 token 설정을 제거한다. 직전 token은
교체 구간 한 번만 허용하며 비어 있으면 추가 token을 받지 않는다.

PostgreSQL 18 공식 이미지의 데이터 디렉터리에 맞춰 이름 있는 볼륨은
`/var/lib/postgresql`에 마운트한다.

이 구성은 로컬 개발용이며 운영 배포 구성을 뜻하지 않는다.

현재 검증은 MockMvc 기반 HTTP 통합 테스트, 패키지 JAR의 실제 로컬 HTTP 상태 확인과
현재·직전 Bearer를 함께 허용한 BATON→BRIEF 실행 JAR 전달을 포함한다. 실제 HTTPS
스테이징이나 운영 배포를 검증한 것은 아니다. 상세한 실행 증거와 남은 범위는
`HANDOFF.md`에 기록한다.

## 라이선스

아직 라이선스를 채택하지 않았다. 공개 저장소이지만 라이선스가 추가되기 전에는 별도의
사용 허가가 부여되지 않는다.
