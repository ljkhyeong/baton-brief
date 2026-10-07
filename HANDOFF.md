# 인수인계

## 현재 상태

BRIEF의 로컬 MVP와 스테이징 실행 구성을 구현했다. 기능은 [README](README.md),
계약·구조 결정은 [문서 색인](docs/README.md), 계약 버전은 [VERSION](contracts/VERSION)을 따른다.
스키마는 Flyway `V1__create_brief_schema.sql` 하나이고 이벤트는 v2만 투영한다. 보존할 데이터와 운영 소비자가
없어 이벤트 v1·이전 데이터 호환은 두지 않는다. 2026-10-06 운영 전 정리로 V1 내용(지문 열 삭제·열거형 DOMAIN)을
다시 고쳤다. 이전 V1~V10 이력이나 그 전 V1로 만든 로컬·스테이징 DB는 Flyway 체크섬 검증에서 기동이 거부되므로
볼륨을 지우고 다시 만든다. 계약 팩은 원격 호환 검증 전인 RC 상태다.

2026-09-12 조회·운영 연동과 입력 검증 개선을 원격 `main`의 `791c8f4`에 병합했다
([PR #17](https://github.com/ljkhyeong/baton-brief/pull/17)). 해당 PR의
[필수 CI](https://github.com/ljkhyeong/baton-brief/actions/runs/34675956047)는 통과했다.
후속 보안 패치·실행 설정·연동 준비와 코드 정리는 [PR #18](https://github.com/ljkhyeong/baton-brief/pull/18)로
원격 `main`의 `2a96b04`에 병합했다. 최종 CI 상태는 해당 PR, 각 변경의 로컬 검증 범위는 아래 표를 따른다.
2026-10-05 Jackson 보안 패치([PR #24](https://github.com/ljkhyeong/baton-brief/pull/24))와 Claude Code 스킬 공유·코드 정리
([PR #23](https://github.com/ljkhyeong/baton-brief/pull/23))를 원격 `main`의 `6f1c88b`에 병합했고 두 PR의 필수 CI는 통과했다.
병합 뒤 의존성 제출에서 Dependabot 경고 7건이 모두 닫혔다.
2026-10-06 표준 API 정리([PR #28](https://github.com/ljkhyeong/baton-brief/pull/28))를 원격 `main`의 `27a5b08`에 병합했고
필수 CI는 통과했다. Gradle wrapper는 보안 권고가 있는 9.2.1에서 Kotlin 2.4.20 완전 지원 상한인 9.7.0으로 올렸다.
Dependabot이 제안한 9.8.0([PR #20](https://github.com/ljkhyeong/baton-brief/pull/20))은 상한을 넘어 채택하지 않았다
([ADR-0002](docs/ADR/0002_technology-stack/adr.md)).
BATON 연결 변경은 계정 권한 조회와 열람자 생성 제한을 포함해 `1916d8c8`에 병합했다.
이 값은 연동 병합 기준이며, 다른 작업에서 바뀔 수 있는 현재 BATON HEAD를 뜻하지 않는다.

공개 이벤트 수신 주소는 `brief.b4ton.com`이다. 사용자는 Cloudflare DNS·Ubuntu 홈서버·공인 IP·인증서와
공유기 80·443 포트포워딩을 준비했다. k3s는 아직 구축하지 않았다. 이번 요청은 외부 연동 검토이며
API·웹훅 코드, 빌드와 임시 환경변수까지 준비한다. 이미지 빌드·홈서버·공유기·k3s·DNS·TLS 설정은
사용자가 직접 수행한다. 실제 DNS 변경·인증서 적용·원격 배포는 실행하지 않았다.
BRIEF에 주입할 값은 [.env.runtime.example](.env.runtime.example)에 정리했다. Git에서 제외한
`.env.runtime.local`은 권한 `0600`의 로컬 검증용 임시 값이며 운영 비밀로 사용하지 않는다.
추가 이용료 없는 Prometheus와 PostgreSQL 백업·격리 복원 확인을 제공한다. 운영 알림은 Slack·Discord를
선택하거나 함께 사용할 수 있다. 공용 Alertmanager 연결 대상과 실제 웹훅은 미등록이다.
[외부 연동 검토](docs/operations/external-integrations.md)를 따른다.

## 재사용할 검증 근거

아래 결과는 각 행의 기준에만 적용한다.
재사용 전에는 기준 이후의 관련 소스·테스트·설정·환경 차이를 [검증 절차](docs/development/verification.md)로 확인한다.
기준 커밋이 없는 과거 실행은 현재 코드의 검증을 생략할 근거로 단독 사용하지 않는다.

### 최근 로컬 검증

| 대상·기준 | 실행·결과 | 적용 범위와 한계 |
| --- | --- | --- |
| BRIEF Gradle wrapper 9.7.0, 2026-10-07 | wrapper JAR·배포 SHA-256을 Gradle 공식 값과 대조. `./gradlew test :bootstrap:bootJar contractsZip --warning-mode all` 성공(21개 작업 모두 실행). bootstrap 45건·ArchUnit 4건·domain 8건 통과, 지원 중단 경고 없음. `docker build`가 9.7.0을 받아 빌드하고 이미지 기동 확인 | JDK 21·Testcontainers PostgreSQL 18.6. Gradle 10에서 제거될 `by tasks.registering` 대신 `tasks.register<Test>` 사용. 9.8.0도 같은 전체 검증은 통과했지만 Kotlin 2.4.20 완전 지원 상한(9.7.0)을 넘어 채택하지 않음. 원격 CI는 PR 기준 |
| BRIEF 표준 API 정리 `e848173`, 2026-10-06 | `./gradlew test :bootstrap:bootJar` 성공. bootstrap 45건·ArchUnit 4건·domain 8건 통과, `baton-brief.jar` 생성. `contractsZip` 성공(문서 5개·내부 링크 9개 확인). 실제 JAR에서 yml placeholder 없이 환경변수만으로 두 경계의 인증 켜기·token 누락 기동 실패 문구, 이벤트·서비스 무인증 `401`·정상 token 통과, 목록 밖 경로 무인증 `401`·서비스 token `403`, 로그 token 비노출·기본 사용자 미생성 확인. `docker build` 성공, 이미지 안 JAR 경로·`0444`·UID 10001과 기동 확인. PR #28 필수 CI 통과 | JDK 21·Testcontainers PostgreSQL 18.6, 빈 DB. JAR·이미지 확인은 DB 없이 Flyway를 끈 기동이라 수신·조회 동작은 통합 테스트 근거다. 수신 중복·충돌은 저장 필드 동등 비교, 브리프 재사용·최신 여부는 항목 목록 비교로 바뀌어 0000·9999년 재전달 `DUPLICATE`를 추가 검증했다. 스테이징 Compose 실행·배포는 미실행 |
| BRIEF 이전 호환 제거·타입 바인딩, 2026-10-05 | `ea6b4e2`·`61ac26c` 각각 `./gradlew test :bootstrap:bootJar` 성공. bootstrap 44건·ArchUnit 4건·domain 7건 통과, 실행 JAR 생성. 이후 테스트만 추가해 형식 오류 통합 1건·운영 설정 4건·`WeeklyWindowTest` 3건을 선택 실행해 통과했고, 문자열 숫자 변환 금지·DTO 리비전 양수·운영 `limit`·기준 순번·주간 월요일 검사를 지우면 각각 실패함을 확인했다 | JDK 21·Testcontainers PostgreSQL 18.6. 단일 V1 스키마를 빈 DB에 적용한 결과로 검증했고 보존 데이터 업그레이드는 대상이 없다. 이벤트 v1 제거, 요청 UUID·`Instant`·`LocalDate` 타입 바인딩 전환. 원격 배포 미실행 |
| BRIEF `98005ac` 독립 실행 환경변수, 2026-09-12 | `:bootstrap:test`의 인증 설정·서비스 인증·이벤트 계약 8건 통과, `:bootstrap:bootJar`는 입력 불변으로 기존 JAR 재사용(13초). 실제 JAR에서 서비스 토큰 누락 기동 실패, 인증 분리·수신 202·중복 200·요약 조회·브리프 생성, DB 중단 시 readiness 503·liveness 200과 앱 재시작 없는 복구 확인 | JDK 21.0.10·PostgreSQL 18.6. 임시 환경의 DB·HTTP 주소는 loopback과 시험 포트 사용. 최초 DB 재시작 때 Docker 자동 포트 재할당으로 복구 확인 실패해 원인 재현 후 고정 포트로 해당 범위만 재검증. 로그 `/tmp/brief-runtime-env-build-20260912.log`·`/tmp/brief-runtime-env-check-20260912.log`·`/tmp/brief-runtime-env-port-check-20260912.log`·`/tmp/brief-runtime-env-recovery-check-20260912.log`. 임시 프로세스·DB·볼륨 정리, 로그 비밀 비노출·문서 링크·전체 diff 확인. 이미지 빌드·k3s·실제 웹훅 발송·공인 HTTPS·원격 CI 미실행 |
| BRIEF `7c02472` CI 실패 보고서·`53a6aa1` 컨테이너 진단, 2026-09-12 | actionlint 1.7.12 통과. 워크플로의 보관 경로로 기존 도메인·통합·ArchUnit 보고서 25개(83,263바이트)를 선택하며 HTML의 CSS/JS 포함·Gradle 바이너리 결과 제외 확인. 로그 `/tmp/brief-ci-test-reports-actionlint-20260912.log`·`/tmp/brief-ci-test-reports-paths-20260912.log` | 공식 upload-artifact v7.0.1 커밋 고정·Gradle 실패 조건·3일 보관 설정 확인. 실제 원격 업로드·전체 CI·배포는 미실행. 기존 cleanup 6개 실패 조합·Bash 검사의 `53a6aa1` 근거(`/tmp/brief-ci-cleanup-before-20260912.log`·`/tmp/brief-ci-cleanup-after-20260912.log`) 유지. 검사 도구 `/tmp/brief-ci-diagnostics-tools-20260912/actionlint`. 전체 diff·문서 링크 확인. 제품 입력 불변으로 Gradle·JAR 검증 재실행 제외 |
| BRIEF `3bcaaf7` 운영 명령 누락 처리, 2026-09-12 | `./gradlew :bootstrap:test --tests '*BriefOperationsConfigurationTest' :bootstrap:bootJar` 성공(3초), 설정 테스트 3건·ArchUnit 4건 통과. 실제 JAR에서 명령 누락이 출력 없이 성공 종료하는 문제 재현 후, 활성·기본 운영 프로필 모두 실패 종료·표준 출력 없음·DB/웹 미기동 확인. 각 프로필의 REBUILD는 정상 JSON 출력·수신 기록 보존·Flyway 미실행 확인 | JDK 21.0.10·PostgreSQL 18.6의 임시 DB, 정리 완료. 프로필 판정 방식은 실제 JAR의 기본 프로필 변환에서 누락을 놓쳐 제거하고, 운영 YAML의 빈 명령과 기존 필수 열거형 바인딩 사용. 기존 false/FALSE 거부·일반 설정의 명령 생략·웹/Flyway 초기 검사도 테스트에 포함. 로그 `/tmp/brief-missing-command-verified-build-20260912.log`·`/tmp/brief-missing-command-runtime-20260912.log`. 전체 diff·구조·문서 링크 확인. 운영 설정만 바꿔 전체 제품 테스트·계약 ZIP·원격 CI·배포는 미실행 |
| BRIEF `522ff3c` PostgreSQL 준비 검사, 2026-09-12 | 개발용·스테이징과 HTTPS·서비스 API·지표 Compose 조합 5개 구문 확인. PostgreSQL 18.6의 초기화를 지연해 기존 검사가 TCP 접속 불가 상태를 `healthy`로 표시하는 문제 재현. 수정 후 초기화 중 `starting`, 완료 후 `healthy`·TCP SQL 성공, 기존 DB 재시작 확인 | 네트워크·호스트 포트 없는 임시 컨테이너, 검사 간격만 1초로 단축. 초기 검증용 파일 마운트 권한과 Compose 달러 이스케이프 처리 오류를 수정한 뒤 통과·정리. 로그 `/tmp/brief-postgres-readiness-before-20260912.log`·`/tmp/brief-postgres-readiness-after-20260912.log`. 전체 diff·문서 링크 확인. 앱·빌드·스키마 불변으로 `fbea014`의 테스트·JAR 근거 재사용. 전체 서비스 재기동·원격 CI·배포 미실행 |
| BRIEF `9d6e9f2` 운영 안내·경보 문구, 2026-09-12 | `:bootstrap:test`에서 `BriefOperationsConfigurationTest` 1건과 `contractsZip` 성공. Prometheus 경보 시나리오 8개 통과. 계약 ZIP의 문서 5개·내부 링크 8개 확인 | 오류·경보 문자열만 변경. 검증 조건·경보 규칙·API·실행 예시는 유지. 문서 로컬 링크 177개 확인. 전체 테스트·JAR 생성·배포는 문구 수정 범위에서 제외 |
| BRIEF `2d2521d` 운영·전달 경보, 2026-09-12 | Prometheus 3.14.0 `promtool check config`·`test rules`로 규칙 6개·시나리오 18개 통과. 실제 Alertmanager 중단으로 전송 오류 증가·전달 실패 경보를 확인하고 재시작 후 HTTPS 전달·Slack 수신 대역의 경보/해제 확인. 기본 대상 `[]`에서 업무 경보가 발생해도 전송 오류·유실 지표와 전달 실패 경보가 없음을 별도 확인. 로그 `/tmp/brief-notification-rules-20260912.log`·`/tmp/brief-notification-runtime-20260912.log`·`/tmp/brief-notification-disabled-20260912.log` | Alertmanager 0.32.1·Python 3.14.7, 비루트·읽기 전용·외부 네트워크/호스트 포트 없는 임시 환경. 시험에만 간격 단축·임시 CA 사용 후 정리. 실패 없음. 유실 카운터 증가·5분 후 해제·재시작 초기화는 규칙 시나리오로 검증. 앱·빌드 입력 불변으로 `6ed7922`의 Gradle·JAR 근거 재사용. 기존 DB 대기 실측은 `7d54ba6`·`42b744f` JAR의 `/tmp/brief-db-wait-runtime-20260912-retry.log` 근거 유지. 실제 Slack·Discord 수신·원격 배포·CI는 미실행 |
| BRIEF `ae06f1c` 운영 출력 분리, 2026-09-12 | `./gradlew test :bootstrap:bootJar` 성공(15초). bootstrap 39건 통과, 도메인 6건 기존 결과 재사용. JDK 21.0.10·PostgreSQL 18.6. 실제 JAR로 단건·이상 기록 2페이지·재구축과 미존재·필수 입력 누락을 실행해 JSON/로그 분리·종료 코드 확인. 로그 `/tmp/brief-ops-output-build-20260912.log`·`/tmp/brief-ops-output-runtime-20260912.log` | 운영 프로필에만 적용. 포트 점유 상태에서도 정상 실행하고 검증용 미적용 마이그레이션을 실행하지 않음을 확인. 웹 기본 로그, 수신·충돌·브리프·Flyway 이력 보존과 임시 앱·DB 정리 확인. 실행 JAR는 이 커밋 기준. 실패·제외 없음. 기존 주간 해소 필터·비교 조건부 조회 포함 전체 테스트 통과. 원격 배포·운영 Compose 실행은 미실행. 계약 ZIP 입력 변경 없음 |
| BRIEF `3c7636c` 동시 수신 예시·`adae4e0` 단독 채널, 2026-09-12 | Alertmanager 0.32.1로 동시 수신 예시 구문·BRIEF/RELAY 경로 2건, actionlint 1.7.12 통과. 기존 수신 대역을 재사용해 새 예시에서 Slack·Discord 경보·해제 4건과 제목·본문·내부 URL 비노출 확인 | 비루트·읽기 전용·외부 네트워크와 호스트 포트 없는 임시 환경, 발송 간격만 시험용으로 단축. 실패 없음·임시 컨테이너 정리. 로그 `/tmp/brief-dual-webhook-config-20260912.log`·`/tmp/brief-dual-webhook-runtime-20260912.log`·`/tmp/brief-dual-webhook-actionlint-20260912.log`. 기존 단독 채널과 Prometheus/HTTPS 전달 근거는 `adae4e0`·`0d9b064` 유지. 문서 링크·전체 diff 확인. 제품·빌드·환경변수 입력 불변으로 `98005ac`의 JAR·API 검증 재사용. 실제 채널 발송·서버 설정·이미지 빌드·원격 CI는 미실행 |
| BRIEF `5e2f5ae` 백업 자동 검증, 스크립트 `93345fc`, 2026-09-12 | actionlint 1.7.12와 CI에 추가한 Bash 블록 실행 성공. 기존 `ae06f1c` JAR·JDK 21.0.10·PostgreSQL 18.6으로 V9 스키마와 계약 이벤트를 준비해 백업 생성·격리 복원·손상 파일 실패 확인. 원본 DB·`0700`/`0600` 권한 보존과 임시 앱·DB·볼륨 정리 확인. 로그 `/tmp/brief-backup-ci-20260912-retry.log` | 최초 로컬 시도는 내부 네트워크의 호스트 접속 불가로 준비 중 실패해 검증 환경에만 loopback 접속 경로를 추가했다. CI 추가 블록만 실행했으며 전체 Actions·원격 실행은 미실행. 기존 `93345fc`의 20,001행·외래 키 오류·공백 경로·격리 조건 검증은 유지. 앱·스키마·스크립트 변경이 없어 Gradle·JAR·계약 ZIP 재검증 제외. Linux 타이머 설치·대용량·외부 보관·서버 장애 복구는 미실행 |
| BATON `1916d8c8` | `build checkApiContract`, 후속 계약 문서 수정의 `generateApiContract checkApiContract`, 최종 프런트 빌드 성공 | 병합한 코드·API 계약·프런트 빌드. 원격 배포 근거는 아님 |
| BATON 병합 중 전체 브라우저 실행 | API 대역 환경에서 614건 통과·기존 조건에 따라 43건 제외 | 이후 열람자 제한 보완이 있어 최종 코드 전체 재실행 결과는 아님 |
| BATON 열람자 제한 보완 후 | 관련 브라우저 시나리오 54건 통과 | 위 전체 실행과 별도 결과. 실제 DB를 사용하는 브라우저 통합은 미실행 |

병합 후 실제 두 서비스 JAR의 교차 검증과 공인 스테이징 검증은 다시 실행하지 않았다.
이후 전체 실행으로 대체된 제품 테스트 기록은 `git show 9e920c2:HANDOFF.md`, 그 이전 세부 명령은
`git show 845b601:HANDOFF.md`에서 확인할 수 있다.

### 필요한 경우 참조할 과거 검증

| 범위·실행일 | 확인한 내용 | 남은 범위 |
| --- | --- | --- |
| 조회·생성 연결, 2026-09-05 | BATON `BriefEditionHttpsEndToEndTest` 성공. 실제 두 JAR·MySQL 8.4·PostgreSQL 18.6·서비스 Caddy로 분류·원본 심각도·주간 해소 상세 확인 | 당시 BATON 작업 브랜치 기준. 이후 메인 계정 권한 변경과 원격 인증서·비밀은 별도 확인 필요 |
| 이벤트 생산·전달, 2026-08-30~31 | BATON `4a7f6d1`·BRIEF `0e6cd2a`로 실제 직렬화·두 JAR 전달 확인. 이후 BATON 계약 핀 갱신과 `BriefDeliveryEndToEndTest` 성공 | loopback HTTP·MySQL 8.4·PostgreSQL 18.6. 응답 유실은 전달 상태를 되돌려 재현했고 실제 TCP 절단은 미실행 |
| 운영 명령·지표 구성, 2026-09-05 | 격리 PostgreSQL 18.6·임시 비밀로 비루트·읽기 전용·내부 네트워크·호스트 포트 비게시·파일 Bearer 확인. 지표 loopback 제한과 명령 종료·웹/Flyway 비활성 확인 | 로컬 선택적 Compose 기준. 외부 수집·경보는 미연결 |
| 수동 백업·복원, 2026-08-30 | PostgreSQL 18.6의 소량 계약 예시를 별도 DB에 복원해 중복 수신·재구축·브리프·ETag·순서 보존 확인 | loopback·인증 비활성 환경. 운영 비밀·대용량·원격 보관·운영 DB 전환은 미검증 |

검증용 컨테이너·볼륨·임시 비밀·덤프는 당시 정리했다. 임시 산출물이 지금도 남아 있다고 가정하지 않는다.
공개·서비스 Caddy와 Compose의 실행 기준은 [배포 준비](docs/operations/brief-b4ton-com-deployment.md)를 따른다.

## 미검증·미결정 범위

- 공인 DNS·ACME 인증서와 실제 BATON 호스트에서 BRIEF Caddy까지의 원격 이벤트 전달
- 실제 서비스 인증서·truststore·배포 비밀을 사용한 조회·생성, 로그인 사용자 화면과 권한 확인
- 실제 TCP 응답 절단·프로세스 중단 뒤 생성 실행 재시도, 운영 브라우저의 인쇄·PDF 결과
- WATCH·RELAY·GO 생산자 연동과 브로커
- 수신 기록·충돌 기록·브리프의 삭제·압축·외부 보관·보존 기간
- 재구축 SLO·잠금 제한 시간·체크포인트, 백업 보관 정책·복구 전환·RPO·RTO
- 실제 서버의 Prometheus·정기 백업 설치와 수집·전달 장애의 외부 알림 연결
- 공개 Caddy의 인증서 볼륨 소유권을 포함한 비루트 전환, 다중 인스턴스 구성
- 이미지 registry·릴리스 정책·라이선스
- Gradle dependency verification checksum의 최초 검토와 플랫폼 간 유지 절차
- Dependabot 경고 7건(Tomcat 3·Jackson 3·Commons Lang 1)의 원격 해소는 병합 뒤 `main`의 의존성 제출 결과로
  확인한다. 2026-10-05 기준 Tomcat 경고가 남은 원인은 웹 어댑터에만 건 제약이 `:bootstrap:testCompileClasspath`에
  닿지 않아 11.0.24가 그래프에 남은 것이었다. 루트 빌드로 옮긴 뒤 모든 해결 가능 구성과 `buildEnvironment`에서
  Tomcat 11.0.25·Jackson 3.1.7·Commons Lang 3.20.0을 확인했다. 기존 `main` 전용 Actions 캐시 쓰기 정책을 유지한다.

## 다음 작업

1. 사용할 채널의 웹훅과 공용 Alertmanager의 비공개 HTTPS 주소가 준비되면 [연결 절차](docs/operations/external-integrations.md)를 적용한다.
   서버 설치 요청이 있을 때만 k3s 배포를 진행한다. [기존 배포 참고](docs/operations/brief-b4ton-com-deployment.md)의
   Compose 연결을 k3s에 그대로 적용한 것으로 간주하지 않는다.
2. 주간 분류·해소 상세 응답을 제공하는 BRIEF를 먼저 배포하고 BATON을 연결한다.
   서비스 인증서·truststore와 이벤트/서비스별 현재 Bearer를 주입한다. 직전 token은 교체할 때만 사용한다.
3. 실제 BATON serializer의 현재 계약 본문으로 공인 HTTPS 이벤트 수신을 확인한다.
   정상 Bearer 성공·잘못된 Bearer `401`·동일 이벤트 재전달 `200`, 비밀 로그 비노출과 실패 시 outbox 재시도를 확인한다.
4. 로그인한 BATON 화면의 요약·필터·전이·해소 상세·조회·생성과 열람자 제한을 확인한다.
   장애 주입은 실행 기록을 보존하고 별도 진행한다. 원격 전달 검증 전에는 계약 팩을 안정 버전으로 올리지 않는다.
5. BATON이 [PRD-0032](docs/PRD/0032_scoped-edition-comparison/spec.md) 범위 지정 비교로 두 브리프의 사전 조회를 줄이고,
   [PRD-0031](docs/PRD/0031_edition-freshness/spec.md) 최신 여부를 추가 전달 안내와 함께 표시하도록 BATON PRD-0010과
   BRIEF 클라이언트를 갱신한다. BATON 저장소 작업은 요청이 있을 때 진행한다.

## 다음 세션의 환경 참고

2026-09-12 `6de5ab8` 기준으로 수신·조회 DTO, 서비스·저장 처리, Bearer 인증, 백업·복원 스크립트,
Compose·Caddy·Prometheus 설정과 파일 검사 도구를 정적 검토했다. 추가 수정이 필요한 오류는
확인하지 못했다. 제품 코드·설정·테스트·의존성 변경이 없어 위 실행 근거를 유지하고 테스트는
반복하지 않았다. 다음 검토는 이후 변경이나 새 실패 근거를 우선하며, 같은 범위의 일반 점검을
반복하지 않는다. 이 검토는 위 미검증 항목의 실행 검증을 대신하지 않는다.

스킬 검증은 공통 지침의 `~/.codex/venvs/skill-validation/bin/python`과 공식 `quick_validate.py`를
사용한다. 이전 작업의 임시 환경에 의존하지 않으며 메타데이터가 바뀌지 않으면 검증을 반복하지 않는다.

이전 기능·테스트 나열은 Git 이력에 보존했다. 이 문서에는 현재 재사용할 근거와 남은 작업만 갱신한다.
기존 세션에서 확인한 검증 범위 확대·환경 재시도 문제의 처리 기준은 [검증 절차](docs/development/verification.md)에 있다.
