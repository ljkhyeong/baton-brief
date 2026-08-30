# 인수인계

## 현재 상태

BATON BRIEF의 로컬 MVP와 최소 스테이징 실행 경계를 구현했다.

- 이벤트 v1·v2 멱등 수신, 최초 충돌 증거, 집계 리비전과 공백 처리
- 현재 관심 항목 단건·상태별 목록·상태 전이 증거와 원자적 전체 재구축
- 불변 주간 에디션 생성·전역 최신·주간 최신·단건·이력·비교·조건부 조회
- 이벤트 수신 증거 단건·이상 이력, 표준 `ProblemDetail`과 aggregate health
- BATON 전용 Bearer, 비루트 BRIEF·내부 PostgreSQL·파일 기반 비밀의 스테이징 Compose
- 이벤트 수신 한 경로만 허용하는 Caddy HTTPS 앞단과 컨테이너 네트워크 격리
- 조회·생성만 허용하는 별도 Bearer와 호스트 포트 없는 서비스 전용 Caddy HTTPS 앞단
- BRIEF 호스트 포트 비게시와 wrapper·외부 Action·Dockerfile·Caddy 실제 조립을 확인하는 CI 경계

현재 데이터베이스 마이그레이션은 V8까지다. 이벤트 v2 계약 팩 버전은
`2.0.0-rc.4`이며 원격 스테이징 호환을 완료한 안정 버전이 아니다. 계약과 구조 결정의
전체 목록은 [문서 색인](docs/README.md)을 따른다.

## BATON 애플리케이션 연결 설계

ADR-0006·0007과 PRD-0023·0024·0025에서 다음 경계를 채택했다.

- BATON UI는 BRIEF를 직접 호출하지 않고 BATON 백엔드가 사용자 인증과 작업공간·시즌
  권한을 판정한 뒤 허용한 내부 조회만 중계한다.
- BATON이 권위 있는 생성 대상·시즌 시간대·실행 시점을 정하고 BRIEF의 기존 멱등 에디션
  생성 명령을 최소 한 번 호출한다.
- BRIEF는 사용자 계정·멤버십, 생성 대상 registry와 scheduler를 소유하지 않는다.
- 조회·생성은 이벤트 수신 Bearer와 다른 서비스 인증과 비공개 네트워크 경로를 전제로
  하며, 현재 Caddy 공개 허용 목록은 이벤트 수신 한 경로로 유지한다.

BRIEF의 별도 서비스 Bearer와 비공개 HTTPS 앞단, BATON 조회 client와 V26 생성 실행 기록을
구현했다. BATON 선택 실행 교차 서비스 테스트에서 실제 두 실행 JAR과 MySQL 8.4·PostgreSQL
18.4, 서비스 Caddy를 함께 기동해 계정 로그인·멤버십·접근 키 판정부터 HTTPS 생성·조회,
`ETag` 조건부 조회, 생성 성공 상태 유실 뒤 재시도와 서비스 token 교체를 확인했다.

## BATON 생산자 연동 상태

BATON 작업 브랜치에서 권위 있는 다섯 연속성 신호, 신호별 리비전, 설정형 시간 재조정,
불변 outbox와 최소 한 번 HTTP 전달을 구현했다. 2026-08-30 BATON `4a7f6d1`의 격리된
소스 복사본과 BRIEF `0e6cd2a`의 실행 JAR로 현재 계약 팩 `2.0.0-rc.4` 호환성을 확인했다.
BATON 실제 이벤트 record의 Jackson 직렬화가 다섯 신호를 포함한 일곱 예시와 JSON Schema를
모두 통과했다. 실제 두 실행 JAR과 MySQL 8.4·PostgreSQL 18.6을 함께 기동한 선택 실행
테스트에서는 다음 흐름을 확인했다.

- `ROLE_UNASSIGNED` 원본 API 변경과 초기 정합화 후 BRIEF 현재 투영 수렴
- BRIEF 미기동 네트워크 실패 뒤 재시도
- 최초 `202`, 응답 유실을 재현한 동일 이벤트의 `200`
- 원본 심각도 변경과 `RESOLVED` 수렴
- 현재·직전 Bearer 중첩 교체 구간

BATON의 진행 중인 CAL 변경은 포함하지 않았다. 임시 복사본에 BRIEF 계약 팩을 그대로
추가하고 기존 직렬화 테스트의 입력 경로와 전달 테스트의 PostgreSQL 이미지 버전만
바꿨다. 제품 코드와 검증 로직은 추가하지 않았다. BRIEF의 `:bootstrap:bootJar contractsZip`
성공 뒤 BATON 복사본에서 다음 두 대상 테스트가 각각 성공했다.

```shell
./gradlew --no-daemon :adapter-out-external:test \
  --tests com.personal.baton.adapter.out.external.brief.BriefContinuityEventContractTest
./gradlew --no-daemon :application:briefCrossServiceTest \
  -PbriefBootJar=/absolute/path/to/baton-brief/bootstrap/build/libs/bootstrap-0.1.0-SNAPSHOT.jar \
  --tests com.personal.baton.application.brief.BriefDeliveryEndToEndTest
```

이 검증은 loopback HTTP를 사용했다. 응답 유실은 실제 TCP 응답 절단이 아니라 BRIEF 수신
뒤 BATON 전달 상태를 재시도 상태로 되돌려 재현했다. 일곱 예시의 직렬화 성공을 모든
Unicode 경계 입력의 생산자 검증으로 확대하지 않는다. BATON 저장소의 계약 고정 버전은
여전히 `2.0.0-rc.1`이며 임시 복사본의 성공은 버전 고정 변경을 뜻하지 않는다. 양쪽 저장소
전체 테스트와 조회·에디션 생성 교차 서비스 테스트는 다시 실행하지 않았다. 위 조회·생성
교차 검증의 PostgreSQL 18.4 근거는 그대로 유지한다.

BATON 프로덕션 설정에 BRIEF HTTPS origin과 파일 기반 Bearer 주입을 연결했지만 실제 두
스테이징 호스트 사이의 공인 HTTPS 전달은 아직 검증하지 않았다. 이번 작업에는 실제
스테이징 호스트·접속 방법·배포 비밀 파일 경로가 제공되지 않았다. 로컬 검증을 원격 전달
완료로 해석하지 않고 계약 팩도 RC로 유지한다.

## 현재 검증 근거

### 수신 계측과 수동 백업·복원

PRD-0026에 따라 웹 어댑터가 Micrometer `brief.events.received`에 수신 결과별 요청 수를
기록한다. 기존 Actuator 자동 구성을 사용하고 `outcome` 외 식별자 태그는 넣지 않는다.
외부 수집·경보와 지표 HTTP 노출은 활성화하지 않았다.

2026-08-30 기존 수신·health 대상 테스트에서 여섯 결과의 증가량과 `/actuator/metrics`의
비노출을 확인한 뒤 `./gradlew --no-daemon test :bootstrap:bootJar`가 성공했다. 변경 없는
도메인 테스트는 Gradle의 기존 성공 결과를 재사용했다. 계약 팩·스키마·Compose와 Caddy는
바꾸지 않아 ZIP 생성과 컨테이너 이미지·HTTPS 조립은 반복하지 않았다.

같은 실행 JAR과 Java 21, 고정 PostgreSQL 18.6 이미지의 별도 검증 DB 두 개로 `pg_dump`
custom 백업과 빈 DB의 `pg_restore --no-owner --no-privileges --single-transaction`을
확인했다. 수신·최초 충돌·현재 투영·에디션·항목·Flyway 이력 여섯 테이블의 전체 행이 같았고,
복원 DB에 연결한 JAR에서 중복 재전달·재구축·기존 에디션과 `ETag` 보존, 수신 순서 `4→5`와
에디션 세대 `2→3`의 연속성을 확인했다. 백업은 `0600`으로 생성했고 검증 뒤 두 DB 컨테이너와
임시 덤프를 제거했다. 기존 사용자 컨테이너와 데이터는 변경하지 않았다.

복원 절차에 추가한 검증 전용 JAR 명령도 같은 산출물과 별도 PostgreSQL 18.6 DB로 기동했다.
config tree 비밀번호 연결, DB·HTTP의 loopback 바인딩, aggregate health와 로컬 무인증
수신·증거 조회·재구축을 확인하고 임시 자원을 정리했다. 이 추가 확인은 실행 설정만
검증했으며 백업·복원 전체 시나리오와 전체 테스트·빌드는 반복하지 않았다.

이 검증은 소량의 계약 예시 데이터와 loopback HTTP·로컬 인증 비활성 설정을 사용했다.
운영 비밀·HTTPS·대용량·원격 보관소·백업 이후 BATON 재전달과 운영 DB 전환은 검증하지 않았다.
[수동 백업·격리 복원 절차](docs/operations/postgresql-backup-restore.md)는 자동 백업 정책이나
운영 RPO·RTO를 채택하지 않는다.

### 저장소 전체

```shell
./gradlew --no-daemon test :bootstrap:bootJar contractsZip
```

2026-08-30 Kotlin/JVM·Kotlin BOM 2.4.10과 Gradle 9.2.1·Java 21 조합에서 성공했다.
다섯 모듈의 전체 테스트, PostgreSQL Testcontainers 빈 데이터베이스 V1~V8
적용, 대표 V2 데이터를 V7까지 올린 뒤 지원 v1·v2 기록을 추가한 V8 업그레이드와
`bootstrap-0.1.0-SNAPSHOT.jar`, 재현 가능한 이벤트 계약 ZIP 생성을 확인했다.

`bootstrap`의 `runtimeClasspath`와 `testRuntimeClasspath`에서 `kotlin-stdlib`·
`kotlin-reflect`가 모두 2.4.10으로 해석되며 실행 JAR에도 두 라이브러리의 2.4.10이 포함된다.
Kotlin 플러그인과 BOM은 같은 version catalog 값을 사용한다. Kotlin 갱신에서는 제품 코드·
검증 코드를 추가하지 않았고 Spring Boot 4.1.1·Jackson 3.1.5·PostgreSQL 18.6과 계약 팩 버전은 유지했다.
Gradle 9.7.1 자동 제안은 Kotlin 2.4.10의 완전 지원 상한인 9.5.0을 넘으므로 보류했다.
기술 선택의 근거는 [ADR-0002](docs/ADR/0002_technology-stack/adr.md)를 따른다.

단순 JDBC 행 매핑 6종은 Spring `DataClassRowMapper`를 재사용하며 `Instant` 열 읽기만
JDBC 4.2의 `OffsetDateTime`과 `toInstant()`로 보완했다. 기존 자동 매핑에서 구형
`Timestamp`를 거치며 과거 시각이 달라지던 문제를 막고, 생성자·필드 매핑과 nullable 값은
Spring과 JDBC에 맡긴다. 주간 구간을 포함한 에디션 조립은 유지했다. 재구축 저장은
`NamedParameterJdbcOperations.batchUpdate`로 묶었으며 기존 트랜잭션·잠금과 실패 롤백,
이전 에디션의 리비전 근거 `null` 호환성을 확인했다. 저장 스키마·지문·규칙 버전은 바꾸지
않았고 기존 수신 기록과 불변 에디션을 자동 보정하지 않는다.

계약·통합 테스트의 JSON 값 변경은 Jackson `ObjectNode`로 처리한다. `1.0`·`2.0` 표기는
Jackson `RawValue`로 유지하고, 계약 예시의 기본 수신 시나리오는 원문을 그대로 사용한다.
이벤트 token 검증 결과는 비공개 Kotlin `lazy` 값으로 재사용한다.
인증 비활성일 때 token 없이 기동하고 활성일 때 필수 token 누락으로 실패하는 경계와
이벤트·서비스 token 분리와 현재·직전 token 수락의 기존 테스트를 확인했다.

조회 입력을 추가로 정리한 뒤에도 위 전체 검증이 성공했다. 현재 단건·전이 이력의
`sourceReference`와 목록의 `afterSourceReference`에 이벤트 수신과 같은 `@Pattern`을 적용해
`U+0000`을 표준 `ProblemDetail`의 `400`으로 거부한다. PostgreSQL 통합 테스트에서 세 조회의
거부와 따옴표·역슬래시가 있는 정상 참조의 수신·단건 조회를 확인했다. 이벤트 종류별 계약
버전은 enum 생성자 속성으로 옮겼으며 이름·버전 값·v1·v2 재생 계약은 유지했다. 새 검증기,
예외 처리기, 테스트 도구나 의존성은 추가하지 않았다. 변경 없는 계약 ZIP은 Gradle의 기존
성공 산출물을 재사용했다.

주간 날짜와 v2 저장 제약 보완 뒤에도 위 전체 검증이 성공했다. 생성·주간 최신 조회의
`weekStart`는 같은 DTO에서 JDK `DateTimeFormatterBuilder`로 네 자리 연도 `0000`~`9999`만
받는다. UTC의 `0000-01-03`·`9999-12-27` 이벤트는 수신 증거·현재 항목·전이 조회와 주간
에디션에서 같은 시각을 보존하며 재구축 뒤에도 같은 에디션을 재사용한다. 기존에 `500`을
만들던 범위 밖 날짜와 `+10000` 연도의 생성·주간 조회 `400 ProblemDetail`도 PostgreSQL
통합 테스트로 확인했다. 현재 시점 기준 제한이나 별도 검증·예외 처리 클래스는 추가하지 않았다.

V8은 기존 이름의 지원 이벤트 제약을 교체해 v2 필수 심각도의 `NULL` 통과를 막는다.
기존 업그레이드 시나리오에 V7 고정 데이터를 추가해 v1·미지원 기록의 `null`, 지원 v2의
`CRITICAL` 보존과 v2 심각도를 `null`로 바꾸는 SQL 거부를 확인했다. 기존 데이터에 이런
불일치가 있으면 마이그레이션이 실패하며 원본 근거 없이 값을 채우거나 삭제하지 않는다.
이벤트 요청·지문·JSON Schema와 계약 팩 버전은 유지했고, 저장 제약 설명을 반영한 PRD-0019를
포함해 계약 ZIP을 다시 생성했다. V8 반영 당시에는 새 실행 JAR 생성까지 확인했고 스테이징
이미지와 BATON 교차 서비스 시나리오는 실행하지 않았다. 이후 검증은 위 BATON 생산자
연동 상태와 아래 패키지와 스테이징 실행 절을 따른다.

원본 참조의 Unicode·공백 규칙 보완 뒤에도 위 전체 검증이 성공했다. 수신·단건 조회·전이
이력·목록 커서는 같은 `@Pattern`으로 JDK 21 공백 기준과 `U+0000`·짝이 없는 surrogate
거부를 적용한다. 중복된 `@NotBlank`와 커서의 별도 공백 판정을 제거하고 커서 조합은 두
필드의 동시 제공 여부만 확인한다. PostgreSQL 통합 테스트에서 잘못된 surrogate 요청의
`400`, 같은 이벤트 ID로 이어진 정상 요청의 신규 수신 `202`, 응답에 포함된 NBSP 커서의
다음 페이지 조회 `200`을 확인했다. 기존 지문 알고리즘·저장 기록과 V8 스키마는 유지했다.

계약 팩은 `2.0.0-rc.4`로 올리고 JSON Schema에 같은 공백 문자와 정상 surrogate 쌍을
명시했다. 계약 테스트와 별도로 ECMAScript 기본·Unicode 모드에서 대표 문자열 20건의
패턴 판정을 확인했다. 이 대조는 다른 언어의 전체 JSON Schema 검증이나 BATON 실제
serializer 검증을 대신하지 않는다. 새 실행 JAR·계약 ZIP을 생성했으며, 이후 수행한
BATON 실제 serializer와 로컬 전달 검증 범위는 위 BATON 생산자 연동 상태를 따른다.

주요 통합 증거는 다음 계약을 포함한다.

- 수신 중복·충돌·미지원·오래된 리비전·리비전 공백, 지원 v1·v2와 기존 미지원 수신의
  고정 지문, 에디션 상태 고정 지문과 최초 충돌 탐지 시각
- 현재 관심 항목 단건·상태별 키셋·적용 전이와 `ETag` 조건부 조회
- 에디션 멱등성·불변성·`A → B → A` 새 세대, 이력·비교·주간 최신과 `ETag`
- 소수 정수·알 수 없는 필드·`24:00`·윤초를 거부하는 엄격한 이벤트 입력과 대표
  `400`·`404` `ProblemDetail`
- 36자 하이픈 UUID 외 별칭과 소수점 표기의 정수 token 거부, Unicode code point 기준
  `sourceReference` 128자 수락·129자와 `U+0000`·짝이 없는 surrogate 거부, NBSP 커서 재사용
- 32비트 양의 미래 `eventVersion`을 문법 오류가 아니라 `UNSUPPORTED`로 보존하는 경계
- 재구축 강제 실패의 원자적 롤백과 재구축·지원 이벤트 수신 잠금 직렬화
- V3 이전 에디션 근거의 `null` 유지, V4 복합 기본 키와 V5·V6 열 제거 업그레이드
- 주간 날짜의 네 자리 연도 입력과 저장 경계, V8의 지원 v2 필수 심각도 및 이전 `null` 호환성

### 이벤트 v2 계약 팩

저장소 전체 검증에서 모든 예시의 JSON Schema 일치, 동일 예시의 PostgreSQL 수신·재구축과
재현 가능한 계약 ZIP 생성을 확인했다. ZIP에는 PRD-0019와 직접 참조하는 PRD-0002·0007·0018을
원래 경로로 포함하며, 포함 문서의 상대 링크 대상이 ZIP 안에 존재하는지 확인했다. 문서
포함 범위만 보완했으므로 요청 스키마·예시와 계약 버전은 유지한다. 이 근거는 계약 팩 게시나
원격 전달 성공을 뜻하지 않는다.

### 패키지와 스테이징 실행

다음 목록과 이후에 링크한 원격 CI는 Kotlin 2.3.21 시점의 근거다.

- Eclipse Temurin 21.0.12+8과 PostgreSQL 18.6에서 패키지 JAR의 Tomcat·Flyway·DataSource·
  aggregate health 결합을 실제 스테이징 컨테이너 내부 HTTP로 확인했다. 같은 검증에서
  무인증 이벤트는 `401`이었고 파일 기반 현재 Bearer 요청은 수신 기록 한 건을 만들었다.
- 기본 스테이징 Compose에서 BRIEF와 PostgreSQL이 호스트 포트를 게시하지 않고 BRIEF가
  내부 `data`·`proxy`에만 연결된 것을 확인했다. BRIEF는 비루트 UID/GID `10001`, 읽기 전용
  루트와 capability 없는 상태로 실행됐다.
- Caddy `https` profile을 `BRIEF_STAGING_HOST=localhost`와 비기본 host port로 기동하고
  내부 CA 루트를 `curl --cacert`로 신뢰했다. 무인증 이벤트 `401`, 현재 Bearer의 계약 v2
  예시 `202 APPLIED`, 외부 `/actuator/health` `404`를 확인했다.
- Caddy 접근 로그에 Authorization 원문이 없고 읽기 전용 루트,
  `no-new-privileges`, `cap_drop=ALL`, `cap_add=NET_BIND_SERVICE`를 확인했다.
- 실제 Docker 연결은 내부 `data`에 PostgreSQL·BRIEF, 내부 `proxy`에 BRIEF·Caddy,
  외부 송신 가능한 `egress`에 Caddy만 존재했다.
- 서비스 전용 Caddy 이미지를 같은 digest 고정 기반 이미지에서 빌드해 실행 파일의
  `cap_net_bind_service`를 제거하고 UID/GID `10001`을 확인했다. 읽기 전용 루트,
  `no-new-privileges`, `cap_drop=ALL`과 임시 인증서로 내부 Docker 네트워크에서 기동했다.
  인증서를 정상 검증한 허용 조회는 `200`, 외부 health와 메서드가 다른 허용 경로는 각각
  `404`였다.
- BATON 선택 실행 테스트는 BATON data, BRIEF data·proxy와 `Internal=true` 서비스 네트워크를
  분리하고 BATON 앱과 서비스 Caddy만 서비스 네트워크에 연결했다. 실제 BATON client가
  PKCS12 truststore로 Caddy 인증서를 검증하고 직전 서비스 token으로 생성·조회한 뒤,
  BRIEF에서 직전 값을 제거하면 `503`으로 실패하고 BATON을 새 token으로 바꾸면 조회와 새
  범위 생성이 다시 성공했다. BRIEF 저장 뒤 BATON 성공 상태만 재시도 상태로 되돌렸을 때
  같은 실행·에디션과 저장 한 건으로 수렴했으며 양쪽 앱·Caddy 로그에 token 원문이 없었다.

네트워크·capability 축소는 Compose만 바꿨으므로 동일 이미지의 실제 기동과 HTTPS 요청으로
검증하고 제품 테스트와 `bootJar`를 반복하지 않았다. 모든 임시 컨테이너·네트워크·볼륨,
내부 CA와 임시 비밀 파일은 검증 뒤 제거했다.

2026-08-30 서비스 Caddy에도 기존 스테이징과 같은 `json-file` 로그 회전
(`max-size: 10m`, `max-file: 3`)을 적용했다. Compose 병합 결과와 실제 컨테이너의
`HostConfig.LogConfig`를 확인했다. 별도 로그 관리 코드나 검증 도구는 추가하지 않고 기존
CI 컨테이너 검사에 로그 설정 확인만 포함했다.

이 검증에서는 Kotlin 2.4.10·V8을 포함한 현재 BRIEF 이미지와 서비스 Caddy 이미지를 빌드하고,
기존 CI의 HTTPS 조립 스크립트를 별도 로컬 프로젝트·이미지 태그로 실행했다. DB aggregate
health, BRIEF·서비스 Caddy의 비루트·읽기 전용 실행, 호스트 포트 비게시와 내부 네트워크를
확인했다. 인증서를 검증한 이벤트 수신·서비스 조회, 무인증 `401`, 허용하지 않은 health
`404`와 token 로그 비노출도 통과했다. 검증용 컨테이너·네트워크·볼륨·임시 비밀은 제거했다.
제품 코드와 계약은 바꾸지 않아 전체 테스트와 계약 ZIP 생성을 반복하지 않았다. 이 조립
검증에는 BATON 실제 serializer·교차 서비스와 공인 HTTPS·원격 CI를 포함하지 않았다.

### 자동 검증 구성

`.github/workflows/verify.yml`은 pull request와 `main` push에서 Java 21을 사용한다.
외부 Action은 전체 commit SHA로 고정했고 `gradle/actions/setup-gradle`이 Gradle 실행 전에
wrapper JAR을 검증하고 `basic` cache provider로 캐시를 구성한다. 이어
`test :bootstrap:bootJar contractsZip`, 로컬·HTTPS profile의 Compose 구문, 고정 PostgreSQL
이미지 pull, 실제 Dockerfile 빌드와 Caddy 설정 유효성을 검증한다. HTTPS profile도 실제로
기동해 BRIEF 호스트 포트 비게시, 내부 네트워크, 비루트·읽기 전용 실행, Caddy capability,
신뢰한 내부 CA의 무인증 `401`·정상 Bearer `202 APPLIED`, 외부 health `404`와 로그의 token
비노출을 확인한다. 같은 조립에서 호스트 포트가 없는 서비스 Caddy도 기동해 내부 네트워크,
비루트·읽기 전용·capability 제거, 인증서를 검증한 서비스 Bearer 조회 `200`, 무인증 `401`,
허용하지 않은 health `404`, 로그의 서비스 token 비노출과 Docker 로그 회전 설정을 확인한다.
Dockerfile frontend와 검증용 curl 이미지도 digest로 고정한다. 전체 작업은 30분, Compose 기동은
180초로 제한한다. 공개 이벤트·서비스 API 검증의 curl 호출은 연결 수립 5초, 전체 요청 10초로
제한한다. 실패하면 원래 종료 상태를 보존한 채 임시 컨테이너·네트워크·볼륨을 제거한다.

2026-08-30 CI의 로그 수집과 비밀 검색을 분리했다. 각 컨테이너 로그를 한 번 수집하고 BRIEF
로그는 이벤트·서비스 token 검사에 재사용한다. 수집 명령을 조건문 밖에서 실행해 로그를
읽지 못하면 CI가 실패하며, `grep --quiet`의 조기 종료가 수집 명령에 영향을 주지 않는다.

워크플로의 Bash 구문과 실제 로그 검사 구간을 합성 Docker 로그로 확인했다. 정상 로그는
종료 코드 `0`, 큰 로그 앞부분의 비밀 표식과 로그 수집 실패는 각각 `1`이었다. 검사 출력에
표식 원문이 없었고 재현용 컨테이너는 제거했다. 별도 검증 도구나 제품 코드는 추가하지
않았으며, 이번 CI 수정에서는 제품 빌드·테스트와 전체 HTTPS 조립·원격 CI를 반복하지 않았다.

공개 HTTPS 호출 세 곳에도 같은 curl 제한 시간을 적용한 뒤 YAML·Bash 구문을 확인했다.
워크플로의 해당 호출 구간을 임시 loopback HTTPS 서버에서 실행해 합성 `401`·`202 APPLIED`·
`404` 응답은 통과하고, 응답을 멈추면 약 10초 뒤 종료 코드 `28`로 후속 요청 없이 중단됨을
확인했다. 인증서를 정상 검증했고 임시 서버·인증서는 정리했다. 이 변경에서는 제품
빌드·테스트, 실제 Caddy 조립과 원격 CI를 다시 실행하지 않았다.

`.github/workflows/dependency-submission.yml`은 `main` push에서 Gradle 직접·전이 의존성
그래프 생성과 제출을 두 작업으로 분리한다. 소스를 체크아웃하고 Gradle을 실행하는 생성
작업은 `contents: read`만 사용하고 wrapper를 검증한 뒤 그래프를 업로드한다. 생성 작업이
성공하면 소스를 체크아웃하지 않는 제출 작업만 `actions: read`와 `contents: write`로 그래프를
내려받아 제출한다. 두 Gradle workflow는 Node.js 24를 사용하는 `gradle/actions` v6.3.0의
같은 commit SHA로 고정하고, MIT 허가의 `basic` cache provider를 명시해 별도 캐시
구성요소를 사용하지 않는다.

GitHub 저장소의 Dependabot 취약점 알림과 보안 업데이트는 2026-08-29에 활성화했다.
`.github/dependabot.yml`은 함께 유지해야 하는 Kotlin 플러그인과 BOM을 minor·patch 그룹으로
묶고 Gradle wrapper와 개별 라이브러리는 분리한다. GitHub Actions minor·patch는 함께
제안하고 major 후보도 개별 pull request로 검토한다. JSON Schema 검증기 3.0.7은 테스트에
Jackson 3.2.1 BOM을 적용해 제품과 classpath를 다르게 만들므로 정확히 이 버전만 제외한다.
Dockerfile·Compose 이미지는 하나의 주간 pull request로 묶고 major 자동 후보는 제외한다.

`main` 브랜치 보호는 최신 `main` 기준의 엄격한 `빌드와 설정 검증` 성공을 필수로 하고,
관리자에게도 같은 규칙을 적용한다. pull request 경유를 요구하되 승인 인원은 현재 0명이며,
force push와 브랜치 삭제는 허용하지 않는다. 이 설명보다 GitHub의 실제 보호 규칙 조회
결과를 운영 판단의 기준으로 사용한다.

2026-08-29 PR #4 병합 뒤 기존 v4.4.3 구성의
[저장소 검증](https://github.com/ljkhyeong/baton-brief/actions/runs/33262434555)과
[의존성 그래프 최초 제출](https://github.com/ljkhyeong/baton-brief/actions/runs/33262434669)이
성공했다. JSON Schema 검증기는 3.0.6을 유지하고 Spring Boot BOM으로 제품과 테스트의
Jackson 3.1.5가 일치하는 것과 저장소 전체 검증을 확인했다.

2026-08-30 PR #8의
[pull request 검증](https://github.com/ljkhyeong/baton-brief/actions/runs/33298731080)과 병합 뒤
[main 저장소 검증](https://github.com/ljkhyeong/baton-brief/actions/runs/33298896589),
[분리된 의존성 그래프 생성·제출](https://github.com/ljkhyeong/baton-brief/actions/runs/33298896603)이
성공했다. v6.3.0 Action, 생성·제출 권한 분리, 공개 이벤트 Caddy와 서비스 Caddy의 동시
실행 조립, Linux 비루트 Caddy의 TLS 파일 읽기 권한과 dependency graph 실제 갱신까지
원격 근거로 확인했다.

## 미검증·미결정 범위

- BATON 저장소의 계약 팩·직렬화 테스트 입력을 `2.0.0-rc.4`로 고정하는 변경
- 공인 DNS·ACME 인증서와 실제 BATON 스테이징 호스트→BRIEF Caddy 원격 전달
- 운영자 제공 서비스 인증서·실제 배포 비밀을 사용한 스테이징 조회·생성 활성화
- 실제 TCP 응답 절단이나 프로세스 중단 뒤 BATON 생성 실행 재시도
- WATCH·RELAY·GO 생산자 연동과 브로커
- 수신 기록·충돌 증거·에디션의 삭제·압축·외부 보관과 숫자 보존 기간
- 재구축 SLO·잠금 제한 시간, 체크포인트, 운영 백업 보관 정책·복구 전환과 RPO·RTO
- 수신 지표의 비공개 수집 경로·인증·수집 시스템과 경보
- Caddy 인증서 볼륨 소유권을 포함한 비루트 전환과 다중 인스턴스 고가용성
- 이미지 registry와 릴리스 정책
- Gradle dependency verification metadata의 신뢰 가능한 최초 checksum 검토와
  플랫폼 간 유지 절차. 현재 wrapper 배포본 checksum과 최소 CI를 우선하고, 실제 작업
  의존성에서 생성된 대규모 metadata는 검토 없이 추가하지 않는다.
- Kotlin Gradle plugin의 [GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp)는
  build cache metadata 역직렬화에 영향을 주며 2.4.10도 영향 범위에 있다. 안정 패치가
  아직 없으므로 이번 갱신을 보안 경고 해소로 기록하지 않는다. `main`만 쓰는 Actions 캐시 경계를 유지하고
  `2.4.20-RC2` 같은 사전 릴리스를 경고 제거만을 위해 채택하지 않으며 안정 버전과 ADR 변경을
  함께 검토한다.
- Spring Boot Gradle plugin이 빌드 classpath로 가져오는 Commons Lang의
  `GHSA-j288-q9x7-2f5v`는 제품 실행 의존성이 아니다. 현재 고정 입력의 Gradle 스크립트가
  취약 메서드에 외부 입력을 전달하지 않으므로 임의 강제 버전을 추가하지 않고 Spring Boot
  후속 패치나 검증된 build classpath 제약을 따로 검토한다.
- 라이선스

## 다음 진입점

운영 우선순위는 실제 BATON 스테이징 호스트에서 BRIEF의 공인 HTTPS origin으로 이벤트를
보내는 종단 간 검증이다. 다음 조건을 모두 확인하기 전에는 계약 팩을 안정 버전으로
승격하지 않는다.

1. 공인 DNS와 ACME 인증서를 사용하고 인증서 검증을 끄지 않는다.
2. BATON 실제 serializer의 `2.0.0-rc.4` 본문이 Caddy를 거쳐 BRIEF에 저장된다.
3. 현재 Bearer 성공, 잘못된 Bearer `401`, 동일 이벤트 재전달 `200`을 확인한다.
4. Caddy 로그와 BRIEF 로그에 Authorization·token·원문 비밀이 남지 않는다.
5. 실패 시 BATON 원본 트랜잭션은 유지되고 outbox가 재시도 가능한 상태를 보존한다.

이 작업은 실제 스테이징 DNS·비밀·방화벽 변경 권한이 필요하다. 그 권한이 없는 로컬
작업에서는 숫자 SLO나 별도 배포 자동화를 추측해 추가하지 않는다.

사용자 조회·생성 연결의 로컬 구현과 교차 서비스 검증은 완료했다. 다음 운영 진입점은
실제 스테이징 배포의 서비스 인증서·truststore·서로 다른 현재 token을 주입해 조회와 생성을
한 번 확인하고, 교체할 때만 BRIEF의 직전 token 슬롯을 사용한 뒤 제거하는 것이다. 로컬
검증은 자체 생성 인증서와 저장 상태 되돌리기를 사용했으므로 실제 TCP 응답 절단이나
프로세스 중단을 포함한 장애 주입은 스테이징 실행 기록을 보존한 상태에서 별도로 확인한다.

## 문서 진입점

- [제품 소개와 실행 방법](README.md)
- [PRD·ADR 전체 색인](docs/README.md)
- [이벤트 v2 계약 팩](contracts/README.md)
- [개발 작업 규칙](AGENTS.md)
