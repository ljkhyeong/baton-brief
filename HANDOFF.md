# 인수인계

## 현재 상태

BRIEF의 로컬 MVP와 스테이징 실행 구성을 구현했다. 기능은 [README](README.md),
계약·구조 결정은 [문서 색인](docs/README.md), 계약 버전은 [VERSION](contracts/VERSION)을 따른다.
현재 마이그레이션은 V9이며 계약 팩은 원격 호환 검증 전인 RC 상태다.

2026-09-08 조회 코드·수신 경보·인증 개선을 원격 `main`의 `5b7d880`에 병합했다(PR #14).
조회 매개변수·수신 경보·토큰 캐시 정리의 구현 기준은 `7e1a054`다.
BATON 연결 변경은 계정 권한 조회와 열람자 생성 제한을 포함해 `1916d8c8`에 병합했다.
이 값은 연동 병합 기준이며, 다른 작업에서 바뀔 수 있는 현재 BATON HEAD를 뜻하지 않는다.

공개 이벤트 수신 주소는 `brief.b4ton.com`으로 설정했지만 서버는 미구축이다.
DNS 연결·공인 인증서 발급·원격 배포는 실행하지 않았다.
배포에 필요한 정보는 서버 위치·접속 방법·실제 IP다. 추가 이용료 없는 자체 Prometheus와
PostgreSQL 백업·Linux 타이머 예시를 준비했다. 실제 서버 설치와 외부 경보 수신처는 미정이다.

## 재사용할 검증 근거

아래 결과는 각 행의 기준에만 적용한다.
재사용 전에는 기준 이후의 관련 소스·테스트·설정·환경 차이를 [검증 절차](docs/development/verification.md)로 확인한다.
기준 커밋이 없는 과거 실행은 현재 코드의 검증을 생략할 근거로 단독 사용하지 않는다.

### 최근 로컬 검증

| 대상·기준 | 실행·결과 | 적용 범위와 한계 |
| --- | --- | --- |
| BRIEF `9d6e9f2` 운영 안내·경보 문구, 2026-09-12 | `:bootstrap:test`에서 `BriefOperationsConfigurationTest` 1건과 `contractsZip` 성공. Prometheus 경보 시나리오 8개 통과. 계약 ZIP의 문서 5개·내부 링크 8개 확인 | 오류·경보 문자열만 변경. 검증 조건·경보 규칙·API·실행 예시는 유지. 문서 로컬 링크 177개 확인. 전체 테스트·JAR 생성·배포는 문구 수정 범위에서 제외 |
| BRIEF `7e1a054` 수신 지표·인증, 2026-09-08 | `:bootstrap:test`에서 `BriefEventMetricsTest`·`BriefSecurityConfigurationTest`·`BriefServiceApiSecurityIntegrationTest`와 `BriefMvpIntegrationTest`의 수신·Bearer·상태 확인 3건을 선택해 총 7건 통과(11초). JDK 21.0.10·PostgreSQL 18.6. Prometheus 3.14.0의 `promtool check config`·`test rules`로 규칙 3개·시나리오 8개 통과 | 카운터 초기 등록·첫 충돌/미지원 증가·수집 대상 누락과 복구·정상 결과 제외·카운터 초기화 확인. 토큰 검증·교체 유지. 실패·제외 없음. 단일 웹 어댑터와 경보 규칙 변경으로 전체 테스트·JAR 생성·컨테이너 재기동·계약 ZIP 재생성은 생략. 외부 알림은 미연결 |
| BRIEF `42b744f` 조회·업무 종류별 목록과 요약·주간 이력, 2026-09-12 | `./gradlew test :bootstrap:bootJar` 성공(24초). `bootstrap` 38건 통과, 변경 없는 도메인 6건은 기존 결과 재사용. JDK 21.0.10·PostgreSQL 18.6. 로그 `/tmp/brief-summary-filter-20260912.log` | 기존 조회·생성·수신·인증·주간 이력과 업무 종류별 요약의 v1·v2 집계, 범위 격리·빈 결과·알 수 없는 종류 거부·심각도 변경·해소·재구축 결과 확인. 실패·제외 없음. 이후 문서만 변경. BATON 중계·화면 연결과 원격 배포는 미실행. 계약 ZIP 입력은 변경 없음 |
| BRIEF `c1e68a1` 오류 문구, 2026-09-08 | `:bootstrap:test` 선택 4건과 `contractsZip` 성공. 수신 조회·이벤트 입력 형식·브리프 조회·비교 오류 확인. 백업 경로 안내·문서 링크 163개·ZIP 내 문서 5개 확인 | 코드 변경은 안내 문자열에 한정. API 필드·상태 코드·검증 조건 유지. 전체 테스트·JAR 생성·컨테이너 재기동은 문구 변경 범위에서 제외 |
| BRIEF `f2f34ab` 지표 수집, 2026-09-07 | `docker build --tag baton-brief:no-fee-verify .`, Compose 설정·격리 기동, Prometheus 3.14.0 `promtool check config`·`test rules` 4개 시나리오 성공. 실제 수집·경보 API·비루트·읽기 전용·비공개 네트워크·파일 Bearer·비밀 로그 비노출 확인 | Docker Compose 5.5.0·PostgreSQL 18.6. 원격 배포·외부 알림은 미실행. 제품 소스·의존성은 `5e7cd53`과 같아 아래 Gradle 결과 재사용 |
| BRIEF `f2f34ab` 백업, 2026-09-07 | `bash ops/backup-postgresql.sh`의 파일 권한·실패한 임시 파일 제거·기존 백업 보존 확인. 별도 빈 PostgreSQL에 복원해 Flyway 포함 6개 테이블 일치 | 계약 예시 1건 기준. Linux 타이머 설치·대용량·외부 보관·서버 장애 복구는 미실행 |
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
- 이전 검토에서 남은 Kotlin Gradle plugin의 `GHSA-r937-wjx7-w2jp`와 Spring Boot 빌드 classpath의
  Commons Lang `GHSA-j288-q9x7-2f5v` 후속 검토. 이번 작업에서 최신 패치 상태는 조회하지 않았다.
  기존 `main` 전용 Actions 캐시 쓰기 정책을 유지하며 상세 판단은 이전 HANDOFF와 ADR-0002를 참조한다.

## 다음 작업

1. 서버 위치·접속 정보·IP를 확인하고 [배포 준비](docs/operations/brief-b4ton-com-deployment.md)를 따른다.
   같은 서버면 공개 Caddy의 80·443 포트를 통합하고, 여러 서버면 Docker 공유 네트워크 대신 비공개 경로를 준비한다.
2. 주간 분류·해소 상세 응답을 제공하는 BRIEF를 먼저 배포하고 BATON을 연결한다.
   서비스 인증서·truststore와 이벤트/서비스별 현재 Bearer를 주입한다. 직전 token은 교체할 때만 사용한다.
3. 실제 BATON serializer의 현재 계약 본문으로 공인 HTTPS 이벤트 수신을 확인한다.
   정상 Bearer 성공·잘못된 Bearer `401`·동일 이벤트 재전달 `200`, 비밀 로그 비노출과 실패 시 outbox 재시도를 확인한다.
4. 로그인한 BATON 화면의 요약·필터·전이·해소 상세·조회·생성과 열람자 제한을 확인한다.
   장애 주입은 실행 기록을 보존하고 별도 진행한다. 원격 전달 검증 전에는 계약 팩을 안정 버전으로 올리지 않는다.

## 다음 세션의 환경 참고

스킬 검증은 공통 지침의 `~/.codex/venvs/skill-validation/bin/python`과 공식 `quick_validate.py`를
사용한다. 이전 작업의 임시 환경에 의존하지 않으며 메타데이터가 바뀌지 않으면 검증을 반복하지 않는다.

이전 기능·테스트 나열은 Git 이력에 보존했다. 이 문서에는 현재 재사용할 근거와 남은 작업만 갱신한다.
기존 세션에서 확인한 검증 범위 확대·환경 재시도 문제의 처리 기준은 [검증 절차](docs/development/verification.md)에 있다.
