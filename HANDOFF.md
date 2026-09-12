# 인수인계

## 현재 상태

BRIEF의 로컬 MVP와 스테이징 실행 구성을 구현했다. 기능은 [README](README.md),
계약·구조 결정은 [문서 색인](docs/README.md), 계약 버전은 [VERSION](contracts/VERSION)을 따른다.
현재 마이그레이션은 V10이며 계약 팩은 원격 호환 검증 전인 RC 상태다.

2026-09-12 조회·운영 연동과 입력 검증 개선을 원격 `main`의 `791c8f4`에 병합했다
([PR #17](https://github.com/ljkhyeong/baton-brief/pull/17)). 해당 PR의
[필수 CI](https://github.com/ljkhyeong/baton-brief/actions/runs/34675956047)는 통과했다.
이후 보완한 코드·설정과 검증 변경은 로컬 커밋 상태이며 원격 반영 전이다.
각 변경의 기준 커밋과 검증 범위는 아래 표를 따른다.
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
| BRIEF `98005ac` 독립 실행 환경변수, 2026-09-12 | `:bootstrap:test`의 인증 설정·서비스 인증·이벤트 계약 8건 통과, `:bootstrap:bootJar`는 입력 불변으로 기존 JAR 재사용(13초). 실제 JAR에서 서비스 토큰 누락 기동 실패, 인증 분리·수신 202·중복 200·요약 조회·브리프 생성, DB 중단 시 readiness 503·liveness 200과 앱 재시작 없는 복구 확인 | JDK 21.0.10·PostgreSQL 18.6. 임시 환경의 DB·HTTP 주소는 loopback과 시험 포트 사용. 최초 DB 재시작 때 Docker 자동 포트 재할당으로 복구 확인 실패해 원인 재현 후 고정 포트로 해당 범위만 재검증. 로그 `/tmp/brief-runtime-env-build-20260912.log`·`/tmp/brief-runtime-env-check-20260912.log`·`/tmp/brief-runtime-env-port-check-20260912.log`·`/tmp/brief-runtime-env-recovery-check-20260912.log`. 임시 프로세스·DB·볼륨 정리, 로그 비밀 비노출·문서 링크·전체 diff 확인. 이미지 빌드·k3s·실제 웹훅 발송·공인 HTTPS·원격 CI 미실행 |
| BRIEF `a9d77e4` 수신 저장 실패·재시도, 2026-09-12 | `./gradlew :bootstrap:test --tests '*BriefMvpIntegrationTest.점검 항목 저장 실패*'` 성공(12초). 통합 테스트 1건에서 신규 저장·기존 항목 갱신의 실패 2경로와 롤백·같은 이벤트 재시도 APPLIED·이후 DUPLICATE 확인. ArchUnit 4건 통과 | JDK 21.0.10·PostgreSQL 18.6. 영속성 경계에 잘못된 투영 규칙 버전을 주입해 실제 DB 제약 오류 발생. 실패한 수신 기록 없음·기존 항목 전체 값 보존·정상 재시도 뒤 수신 기록과 항목 유지 확인. 임시 DB 정리 완료, 로그 `/tmp/brief-ingest-rollback-tests-20260912.log`. 전체 diff·구조 검사 통과. 테스트만 추가해 전체 제품 테스트·JAR 재생성·계약 ZIP·원격 CI·배포는 제외. 프로세스 강제 종료·네트워크 절단 검증은 아님 |
| BRIEF `7c02472` CI 실패 보고서·`53a6aa1` 컨테이너 진단, 2026-09-12 | actionlint 1.7.12 통과. 워크플로의 보관 경로로 기존 도메인·통합·ArchUnit 보고서 25개(83,263바이트)를 선택하며 HTML의 CSS/JS 포함·Gradle 바이너리 결과 제외 확인. 로그 `/tmp/brief-ci-test-reports-actionlint-20260912.log`·`/tmp/brief-ci-test-reports-paths-20260912.log` | 공식 upload-artifact v7.0.1 커밋 고정·Gradle 실패 조건·3일 보관 설정 확인. 실제 원격 업로드·전체 CI·배포는 미실행. 기존 cleanup 6개 실패 조합·Bash 검사의 `53a6aa1` 근거(`/tmp/brief-ci-cleanup-before-20260912.log`·`/tmp/brief-ci-cleanup-after-20260912.log`) 유지. 검사 도구 `/tmp/brief-ci-diagnostics-tools-20260912/actionlint`. 전체 diff·문서 링크 확인. 제품 입력 불변으로 Gradle·JAR 검증 재실행 제외 |
| BRIEF `3bcaaf7` 운영 명령 누락 처리, 2026-09-12 | `./gradlew :bootstrap:test --tests '*BriefOperationsConfigurationTest' :bootstrap:bootJar` 성공(3초), 설정 테스트 3건·ArchUnit 4건 통과. 실제 JAR에서 명령 누락이 출력 없이 성공 종료하는 문제 재현 후, 활성·기본 운영 프로필 모두 실패 종료·표준 출력 없음·DB/웹 미기동 확인. 각 프로필의 REBUILD는 정상 JSON 출력·수신 기록 보존·Flyway 미실행 확인 | JDK 21.0.10·PostgreSQL 18.6의 임시 DB, 정리 완료. 프로필 판정 방식은 실제 JAR의 기본 프로필 변환에서 누락을 놓쳐 제거하고, 운영 YAML의 빈 명령과 기존 필수 열거형 바인딩 사용. 기존 false/FALSE 거부·일반 설정의 명령 생략·웹/Flyway 초기 검사도 테스트에 포함. 로그 `/tmp/brief-missing-command-verified-build-20260912.log`·`/tmp/brief-missing-command-runtime-20260912.log`. 전체 diff·구조·문서 링크 확인. 운영 설정만 바꿔 전체 제품 테스트·계약 ZIP·원격 CI·배포는 미실행 |
| BRIEF `522ff3c` PostgreSQL 준비 검사, 2026-09-12 | 개발용·스테이징과 HTTPS·서비스 API·지표 Compose 조합 5개 구문 확인. PostgreSQL 18.6의 초기화를 지연해 기존 검사가 TCP 접속 불가 상태를 `healthy`로 표시하는 문제 재현. 수정 후 초기화 중 `starting`, 완료 후 `healthy`·TCP SQL 성공, 기존 DB 재시작 확인 | 네트워크·호스트 포트 없는 임시 컨테이너, 검사 간격만 1초로 단축. 초기 검증용 파일 마운트 권한과 Compose 달러 이스케이프 처리 오류를 수정한 뒤 통과·정리. 로그 `/tmp/brief-postgres-readiness-before-20260912.log`·`/tmp/brief-postgres-readiness-after-20260912.log`. 전체 diff·문서 링크 확인. 앱·빌드·스키마 불변으로 `fbea014`의 테스트·JAR 근거 재사용. 전체 서비스 재기동·원격 CI·배포 미실행 |
| BRIEF `fbea014` Kotlin·Tomcat 보안 수정, 2026-09-12 | `./gradlew test :bootstrap:bootJar buildEnvironment` 성공(16초), bootstrap 42건·도메인 6건 통과. 최종 구조 검사 4건 통과 후 전체 실행에서는 결과 재사용. 실제 JAR로 DB health·이벤트/서비스 인증 분리·정상 및 중복 수신·현재 항목 조회·잘못된 JSON 거부 확인 | JDK 21.0.10·PostgreSQL 18.6·Kotlin 2.4.20·Tomcat 11.0.25. JAR의 stdlib/reflect와 Tomcat 3개 버전 정렬·Commons Lang 미포함 확인. 로그 `/tmp/brief-security-full-20260912.log`·`/tmp/brief-security-http-20260912.log`. 최초 임시 HTTP 검사에서 경로·필수 조건을 잘못 지정해 수정했으며 제품 오류는 없었음. 비밀 로그 비노출·임시 앱/DB 정리·전체 diff·문서 링크 확인. 원격 CI·HTTPS·배포 미실행. 계약 ZIP 입력 변경 없음 |
| BRIEF `216d73d` JSON 타입·중복 필드 거부, 2026-09-12 | `./gradlew test :bootstrap:bootJar contractsZip` 성공(54초), bootstrap 41건 통과·ArchUnit 4건 및 도메인 6건 성공 결과 재사용. 숫자 sourceReference가 문자열로 변환되어 `202 APPLIED`로 저장되는 문제 재현 후, 문자열·열거형의 잘못된 타입 7가지가 `400 ProblemDetail`이며 저장 0건임을 확인. 정상 문자열 `"123"` 수신·재전달 및 기존 중복 필드 거부 포함. 로그 `/tmp/brief-scalar-before-20260912.log`·`/tmp/brief-scalar-after-20260912.log`·`/tmp/brief-scalar-full-20260912.log` | MockMvc·PostgreSQL 18.6·JDK 21.0.10·Jackson 3.1.5. `JsonMapperBuilderCustomizer`의 Textual coercion과 `spring.jackson.datatype.enum.fail-on-numbers-for-enums` 사용. 기존 scalar 옵션만으로 문자열·숫자 열거형 변환을 막을 수 없음. 최초 컴파일 의존성 누락과 Jackson 2 방식 설정 경로를 수정한 뒤 전체 검증 통과. 배포 JAR 라이브러리 84개는 이전과 동일하며 설정 반영 확인. 전체 diff·구조·문서 링크와 ZIP 문서 5개·내부 링크 8개 확인. 이벤트 스키마·계약 버전 유지. 원격 생산자·실제 HTTP 서버·배포 검증은 미실행 |
| BRIEF `6902274` 주간 해소 집계, 2026-09-12 | `./gradlew :bootstrap:test --tests '*BriefMvpIntegrationTest.주간 해소*'` 3건·ArchUnit 4건 통과(7초). 필터·페이지·DST·재활성화·공백 이후 새 해소와 재구축 확인. 파일 검사·전체 diff 검토 통과. 로그 `/tmp/brief-resolution-tests-20260912.log` | PostgreSQL 18.6·JDK 21.0.10. V10 인덱스를 적용한 동일 합성 DB(전체 10만 건, 대상 작업공간 1만 건)에서 기존/변경 SQL 결과 일치, 1730.794→21.300ms 확인. 기록 재조회 5천 회를 윈도 집계 1회로 대체. `/tmp/brief-resolution-plans-20260912.json`·`/tmp/brief-resolution-benchmark-20260912.log`, 임시 DB 정리 완료. 단일 데이터의 비교이며 운영 성능 보장은 아님. 주간 SQL만 변경해 전체 테스트·JAR 재생성·계약 ZIP·배포는 제외. 당시 JAR는 `99e9579` 기준으로 이 변경 미포함. 최신 JAR는 운영 명령 누락 처리 행 참조 |
| BRIEF `99e9579` 수신 기록 조회 인덱스, 2026-09-12 | `./gradlew test :bootstrap:bootJar` 성공(18초). bootstrap 39건 통과, ArchUnit 4건·도메인 6건 성공 결과 재사용. V2·V7 대표 데이터의 V9→V10 업그레이드 전후 전체 행 보존, JAR의 V10 포함 확인. 로그 `/tmp/brief-receipt-index-tests-20260912.log` | PostgreSQL 18.6·JDK 21.0.10. 100개 작업공간·수신 10만 건·현재 항목 5만 건·충돌 2천 건의 격리 DB에서 기존 SQL 4개의 결과 일치와 조회 계획 개선 확인. 생성 기준 9.327→0.035ms, 이상 수신 7.516→0.428ms, 전이 8.646→0.181ms, 주간 해소 33.288→20.131ms, 인덱스 약 5.7MiB. 단일 합성 데이터의 비교이며 운영 지연 보장·쓰기 처리량 검증은 아님. 근거 `/tmp/brief-receipt-index-plans-20260912.json`·`/tmp/brief-receipt-index-20260912-retry.log`. 첫 실측은 임시 DB 초기화 완료 오인으로 실패해 TCP 준비 확인으로 수정한 뒤 통과·정리. 전체 diff·구조 검사 통과. 원격 CI·배포 미실행. V10 생성 중 쓰기 대기는 배포 문서 참고 |
| BRIEF `9d6e9f2` 운영 안내·경보 문구, 2026-09-12 | `:bootstrap:test`에서 `BriefOperationsConfigurationTest` 1건과 `contractsZip` 성공. Prometheus 경보 시나리오 8개 통과. 계약 ZIP의 문서 5개·내부 링크 8개 확인 | 오류·경보 문자열만 변경. 검증 조건·경보 규칙·API·실행 예시는 유지. 문서 로컬 링크 177개 확인. 전체 테스트·JAR 생성·배포는 문구 수정 범위에서 제외 |
| BRIEF `2d2521d` 운영·전달 경보, 2026-09-12 | Prometheus 3.14.0 `promtool check config`·`test rules`로 규칙 6개·시나리오 18개 통과. 실제 Alertmanager 중단으로 전송 오류 증가·전달 실패 경보를 확인하고 재시작 후 HTTPS 전달·Slack 수신 대역의 경보/해제 확인. 기본 대상 `[]`에서 업무 경보가 발생해도 전송 오류·유실 지표와 전달 실패 경보가 없음을 별도 확인. 로그 `/tmp/brief-notification-rules-20260912.log`·`/tmp/brief-notification-runtime-20260912.log`·`/tmp/brief-notification-disabled-20260912.log` | Alertmanager 0.32.1·Python 3.14.7, 비루트·읽기 전용·외부 네트워크/호스트 포트 없는 임시 환경. 시험에만 간격 단축·임시 CA 사용 후 정리. 실패 없음. 유실 카운터 증가·5분 후 해제·재시작 초기화는 규칙 시나리오로 검증. 앱·빌드 입력 불변으로 `6ed7922`의 Gradle·JAR 근거 재사용. 기존 DB 대기 실측은 `7d54ba6`·`42b744f` JAR의 `/tmp/brief-db-wait-runtime-20260912-retry.log` 근거 유지. 실제 Slack·Discord 수신·원격 배포·CI는 미실행 |
| BRIEF `6867d37` 파일 검사·`6ed7922` 구조 규칙, 2026-09-12 | `python3 -m unittest discover -s scripts -p 'test_*.py'` 6건 통과(3.3초), 계약 JSON 8개 구문 검사 통과. 수정 전 JSON 오류 4종이 files·final 모두 성공 종료하는 문제 재현 후 거부 확인. 정상 한글·배열·문자열은 허용하며 JSON만 검사할 때 Gradle 미실행 확인 | Python 3.14.7. 로그 `/tmp/brief-feedback-json-before-20260912.log`·`/tmp/brief-feedback-json-tests-20260912.log`·`/tmp/brief-feedback-json-contract-files-20260912.log`. 전체 diff·파일 검사 통과. ArchUnit 규칙·CI 등록은 불변이며 `6ed7922`의 네 규칙 강제 실패 확인·전체 Gradle 성공(14초) 근거는 `/tmp/brief-architecture-probes-20260912.log`·`/tmp/brief-feedback-build-20260912.log`로 유지. 검사 도구만 바꿔 제품 테스트·JAR·계약 ZIP·원격 CI·배포는 미실행 |
| BRIEF `ae06f1c` 운영 출력 분리, 2026-09-12 | `./gradlew test :bootstrap:bootJar` 성공(15초). bootstrap 39건 통과, 도메인 6건 기존 결과 재사용. JDK 21.0.10·PostgreSQL 18.6. 실제 JAR로 단건·이상 기록 2페이지·재구축과 미존재·필수 입력 누락을 실행해 JSON/로그 분리·종료 코드 확인. 로그 `/tmp/brief-ops-output-build-20260912.log`·`/tmp/brief-ops-output-runtime-20260912.log` | 운영 프로필에만 적용. 포트 점유 상태에서도 정상 실행하고 검증용 미적용 마이그레이션을 실행하지 않음을 확인. 웹 기본 로그, 수신·충돌·브리프·Flyway 이력 보존과 임시 앱·DB 정리 확인. 실행 JAR는 이 커밋 기준. 실패·제외 없음. 기존 주간 해소 필터·비교 조건부 조회 포함 전체 테스트 통과. 원격 배포·운영 Compose 실행은 미실행. 계약 ZIP 입력 변경 없음 |
| BRIEF `c1e68a1` 오류 문구, 2026-09-08 | `:bootstrap:test` 선택 4건과 `contractsZip` 성공. 수신 조회·이벤트 입력 형식·브리프 조회·비교 오류 확인. 백업 경로 안내·문서 링크 163개·ZIP 내 문서 5개 확인 | 코드 변경은 안내 문자열에 한정. API 필드·상태 코드·검증 조건 유지. 전체 테스트·JAR 생성·컨테이너 재기동은 문구 변경 범위에서 제외 |
| BRIEF `adae4e0` Slack·Discord 경보 연동, 2026-09-12 | Alertmanager 0.32.1의 채널 설정 2개·서비스 분기 4건, actionlint 1.7.12 통과. 문서대로 두 채널 설정을 한 수신처에 합쳐 실제 Alertmanager와 Python 수신 대역에서 양쪽 경보·해제 4건, 제목·본문·내부 URL 비노출 확인. 로그 `/tmp/brief-discord-config-check-20260912.log`·`/tmp/brief-discord-runtime-20260912.log` | 비루트·읽기 전용·외부 네트워크 및 호스트 포트 없는 임시 환경, 시험 발송 간격만 단축. 실패 없음·임시 환경 정리. 변경 없는 Prometheus 설정·경보 13개 시나리오·HTTPS 전달은 `0d9b064` 근거(`/tmp/brief-slack-config-check-20260912.log`·`/tmp/brief-slack-runtime-20260912-retry.log`) 재사용. 앱·빌드·계약 입력은 `6ed7922`와 같아 Gradle·JAR 재검증 제외. 실제 채널·공용 Alertmanager·원격 CI·배포는 미연결·미실행 |
| BRIEF `5e2f5ae` 백업 자동 검증, 스크립트 `93345fc`, 2026-09-12 | actionlint 1.7.12와 CI에 추가한 Bash 블록 실행 성공. 기존 `ae06f1c` JAR·JDK 21.0.10·PostgreSQL 18.6으로 V9 스키마와 계약 이벤트를 준비해 백업 생성·격리 복원·손상 파일 실패 확인. 원본 DB·`0700`/`0600` 권한 보존과 임시 앱·DB·볼륨 정리 확인. 로그 `/tmp/brief-backup-ci-20260912-retry.log` | 최초 로컬 시도는 내부 네트워크의 호스트 접속 불가로 준비 중 실패해 검증 환경에만 loopback 접속 경로를 추가했다. CI 추가 블록만 실행했으며 전체 Actions·원격 실행은 미실행. 기존 `93345fc`의 20,001행·외래 키 오류·공백 경로·격리 조건 검증은 유지. 앱·스키마·스크립트 변경이 없어 Gradle·JAR·계약 ZIP 재검증 제외. Linux 타이머 설치·대용량·외부 보관·서버 장애 복구는 미실행 |
| BRIEF `5e7cd53` 조회 위임 정리, 2026-09-05 | `./gradlew test :bootstrap:bootJar` 성공. `bootstrap` 35건 실행·통과, 도메인 6건 기존 결과 재사용. 실패·제외 없음. JDK 21·PostgreSQL 18.6 | 동일한 조회 10개의 선언을 `BriefQueries`에 모으고 서비스의 단순 전달을 Kotlin 위임으로 대체. 기존 RowMapper 교체를 포함해 조회·페이지·인증·재구축·계약 검증. 원격·교차 서비스 검증은 미실행 |
| BATON `1916d8c8` | `build checkApiContract`, 후속 계약 문서 수정의 `generateApiContract checkApiContract`, 최종 프런트 빌드 성공 | 병합한 코드·API 계약·프런트 빌드. 원격 배포 근거는 아님 |
| BATON 병합 중 전체 브라우저 실행 | API 대역 환경에서 614건 통과·기존 조건에 따라 43건 제외 | 이후 열람자 제한 보완이 있어 최종 코드 전체 재실행 결과는 아님 |
| BATON 열람자 제한 보완 후 | 관련 브라우저 시나리오 54건 통과 | 위 전체 실행과 별도 결과. 실제 DB를 사용하는 브라우저 통합은 미실행 |

병합 후 실제 두 서비스 JAR의 교차 검증과 공인 스테이징 검증은 다시 실행하지 않았다.
세부 명령·과거 실행 기록은 `git show 845b601:HANDOFF.md`에서 확인할 수 있다.

### 필요한 경우 참조할 과거 검증

| 범위·실행일 | 확인한 내용 | 남은 범위 |
| --- | --- | --- |
| BRIEF 주간 분류·운영 명령, 2026-09-05 | `test :bootstrap:bootJar contractsZip` 성공. JDK 21·Kotlin 2.4.10·PostgreSQL 18.6·V9, 대표 이전 데이터 업그레이드와 브리프 불변성 확인 | 원격 배포 미실행 |
| BRIEF 주간 해소 상세, 2026-09-05 | `test :bootstrap:bootJar` 성공. 상세 값·페이지·재활성화·재구축·잘못된 커서 확인 | BATON 병합 후 교차 서비스 재실행은 미실행 |
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
- Commons Lang 3.16.0의 `GHSA-j288-q9x7-2f5v`는 Spring Boot 4.1.1 빌드 플러그인의
  `spring-boot-buildpack-platform → commons-compress → commons-lang3` 경로에 남아 있다.
  실행 JAR에는 없고 이미지는 Dockerfile로 빌드하므로, 관련 플러그인 의존성 갱신 시 재검토한다.
  기존 `main` 전용 Actions 캐시 쓰기 정책을 유지한다. 2026-09-12 확인한 나머지 경고 4건은
  `fbea014`의 Kotlin 2.4.20·Tomcat 11.0.25로 수정 버전을 적용했다. 원격 경고 해소는 병합 후 확인한다.

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
