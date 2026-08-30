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

현재 데이터베이스 마이그레이션은 V7까지다. 이벤트 v2 계약 팩 버전은
`2.0.0-rc.3`이며 원격 스테이징 호환을 완료한 안정 버전이 아니다. 계약과 구조 결정의
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
불변 outbox와 최소 한 번 HTTP 전달을 구현했다. 실제 BATON·BRIEF 실행 JAR과 MySQL 8.4·
PostgreSQL 18.4를 함께 기동한 선택 실행 테스트에서 다음 흐름을 확인했다.

- 원본 API 변경과 초기 정합화 후 BRIEF 현재 투영 수렴
- BRIEF 미기동 네트워크 실패 뒤 재시도
- 최초 `202`, 응답 유실을 재현한 동일 이벤트의 `200`
- 원본 심각도 변경과 `RESOLVED` 수렴
- 현재·직전 Bearer 중첩 교체 구간

두 교차 서비스 검증은 당시 PostgreSQL 18.4에서 수행했다. 현재 BRIEF의 채택 기준인
PostgreSQL 18.6은 저장소 통합 테스트와 스테이징 조립에서 별도로 확인했으며, 같은 BATON
교차 서비스 시나리오를 18.6 조합으로 다시 실행한 근거는 아니다.

BATON 프로덕션 설정에 BRIEF HTTPS origin과 파일 기반 Bearer 주입을 연결했지만 실제 두
스테이징 호스트 사이의 공인 HTTPS 전달은 아직 검증하지 않았다. 로컬 Caddy 내부 CA
검증을 원격 전달 완료로 해석하지 않는다. 위 생산자·소비자 교차 검증은 계약 팩
`2.0.0-rc.1`까지의 근거다. 입력 계약을 엄격하게 한 현재 `2.0.0-rc.3`는 BATON 실제
serializer와 다시 검증해야 한다.

## 현재 검증 근거

### 저장소 전체

```shell
./gradlew --no-daemon test :bootstrap:bootJar contractsZip
```

2026-08-30 Kotlin/JVM·Kotlin BOM 2.4.10과 Gradle 9.2.1·Java 21 조합에서 성공했다.
다섯 모듈의 전체 테스트, PostgreSQL Testcontainers 빈 데이터베이스 V1~V7
적용, 대표 V2 데이터의 V3~V7 업그레이드와
`bootstrap-0.1.0-SNAPSHOT.jar`, 재현 가능한 이벤트 계약 ZIP 생성을 확인했다.

`bootstrap`의 `runtimeClasspath`와 `testRuntimeClasspath`에서 `kotlin-stdlib`·
`kotlin-reflect`가 모두 2.4.10으로 해석되며 실행 JAR에도 두 라이브러리의 2.4.10이 포함된다.
Kotlin 플러그인과 BOM은 같은 version catalog 값을 사용한다. 제품 코드·검증 코드를
추가하지 않았고 Spring Boot 4.1.1·Jackson 3.1.5·PostgreSQL 18.6과 계약 팩 버전은 유지했다.
Gradle 9.7.1 자동 제안은 Kotlin 2.4.10의 완전 지원 상한인 9.5.0을 넘으므로 보류했다.
기술 선택의 근거는 [ADR-0002](docs/ADR/0002_technology-stack/adr.md)를 따른다.

주요 통합 증거는 다음 계약을 포함한다.

- 수신 중복·충돌·미지원·오래된 리비전·리비전 공백, 지원 v1·v2와 기존 미지원 수신의
  고정 지문, 에디션 상태 고정 지문과 최초 충돌 탐지 시각
- 현재 관심 항목 단건·상태별 키셋·적용 전이와 `ETag` 조건부 조회
- 에디션 멱등성·불변성·`A → B → A` 새 세대, 이력·비교·주간 최신과 `ETag`
- 소수 정수·알 수 없는 필드·`24:00`·윤초를 거부하는 엄격한 이벤트 입력과 대표
  `400`·`404` `ProblemDetail`
- 36자 하이픈 UUID 외 별칭과 소수점 표기의 정수 token 거부, Unicode code point 기준
  `sourceReference` 128자 수락·129자와 `U+0000` 거부
- 32비트 양의 미래 `eventVersion`을 문법 오류가 아니라 `UNSUPPORTED`로 보존하는 경계
- 재구축 강제 실패의 원자적 롤백과 재구축·지원 이벤트 수신 잠금 직렬화
- V3 이전 에디션 근거의 `null` 유지, V4 복합 기본 키와 V5·V6 열 제거 업그레이드

### 이벤트 v2 계약 팩

저장소 전체 검증에서 모든 예시의 JSON Schema 일치, 동일 예시의 PostgreSQL 수신·재구축과
재현 가능한 계약 ZIP 생성을 확인했다. 이 근거는 계약 팩 게시나 원격 전달 성공을 뜻하지
않는다.

### 패키지와 스테이징 실행

아래 컨테이너·교차 서비스 실행과 이후에 링크한 원격 CI는 Kotlin 2.3.21 시점의 근거다.
Kotlin 2.4.10에서는 위 저장소 전체 검증과 실행 JAR 구성을 확인했으며, 새 이미지의 실제
기동·BATON 교차 서비스·원격 CI는 아직 다시 검증하지 않았다.

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
허용하지 않은 health `404`와 로그의 서비스 token 비노출을 확인한다. Dockerfile frontend와
검증용 curl 이미지도 digest로 고정한다. 전체 작업은 30분, Compose 기동은 180초로 제한하고
실패하면 원래 종료 상태를 보존한 채 임시 컨테이너·네트워크·볼륨을 제거한다.

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

- 공인 DNS·ACME 인증서와 실제 BATON 스테이징 호스트→BRIEF Caddy 원격 전달
- 운영자 제공 서비스 인증서·실제 배포 비밀을 사용한 스테이징 조회·생성 활성화
- 실제 TCP 응답 절단이나 프로세스 중단 뒤 BATON 생성 실행 재시도
- WATCH·RELAY·GO 생산자 연동과 브로커
- 수신 기록·충돌 증거·에디션의 삭제·압축·외부 보관과 숫자 보존 기간
- 재구축 SLO·잠금 제한 시간, 체크포인트, 백업·복구와 RPO·RTO
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
2. BATON 실제 serializer의 `2.0.0-rc.3` 본문이 Caddy를 거쳐 BRIEF에 저장된다.
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
