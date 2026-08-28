# 인수인계

## 현재 상태

BATON BRIEF의 로컬 MVP와 최소 스테이징 실행 경계를 구현했다.

- 이벤트 v1·v2 멱등 수신, 최초 충돌 증거, 집계 리비전과 공백 처리
- 현재 관심 항목 단건·상태별 목록·상태 전이 증거와 원자적 전체 재구축
- 불변 주간 에디션 생성·전역 최신·주간 최신·단건·이력·비교·조건부 조회
- 이벤트 수신 증거 단건·이상 이력, 표준 `ProblemDetail`과 aggregate health
- BATON 전용 Bearer, 비루트 BRIEF·내부 PostgreSQL·파일 기반 비밀의 스테이징 Compose
- 이벤트 수신 한 경로만 허용하는 Caddy HTTPS 앞단과 컨테이너 네트워크 격리
- 고정 loopback 게시 주소와 wrapper·외부 Action·Dockerfile·Caddy 설정을 확인하는 CI 경계

현재 데이터베이스 마이그레이션은 V7까지다. 이벤트 v2 계약 팩 버전은
`2.0.0-rc.2`이며 원격 스테이징 호환을 완료한 안정 버전이 아니다. 계약과 구조 결정의
전체 목록은 [문서 색인](docs/README.md)을 따른다.

## BATON 애플리케이션 연결 설계

ADR-0006과 PRD-0023·0024에서 다음 경계를 채택했다.

- BATON UI는 BRIEF를 직접 호출하지 않고 BATON 백엔드가 사용자 인증과 작업공간·시즌
  권한을 판정한 뒤 허용한 내부 조회만 중계한다.
- BATON이 권위 있는 생성 대상·시즌 시간대·실행 시점을 정하고 BRIEF의 기존 멱등 에디션
  생성 명령을 최소 한 번 호출한다.
- BRIEF는 사용자 계정·멤버십, 생성 대상 registry와 scheduler를 소유하지 않는다.
- 조회·생성은 이벤트 수신 Bearer와 다른 서비스 인증과 비공개 네트워크 경로를 전제로
  하며, 현재 Caddy 공개 허용 목록은 이벤트 수신 한 경로로 유지한다.

이 결정은 설계만 채택한 상태다. 서비스 인증·비공개 경로, BATON 조회 client와 생성 실행
기록·호출은 구현하거나 종단 간 검증하지 않았다.

## BATON 생산자 연동 상태

BATON 작업 브랜치에서 권위 있는 다섯 연속성 신호, 신호별 리비전, 설정형 시간 재조정,
불변 outbox와 최소 한 번 HTTP 전달을 구현했다. 실제 BATON·BRIEF 실행 JAR과 MySQL 8.4·
PostgreSQL 18.4를 함께 기동한 선택 실행 테스트에서 다음 흐름을 확인했다.

- 원본 API 변경과 초기 정합화 후 BRIEF 현재 투영 수렴
- BRIEF 미기동 네트워크 실패 뒤 재시도
- 최초 `202`, 응답 유실을 재현한 동일 이벤트의 `200`
- 원본 심각도 변경과 `RESOLVED` 수렴
- 현재·직전 Bearer 중첩 교체 구간

BATON 프로덕션 설정에 BRIEF HTTPS origin과 파일 기반 Bearer 주입을 연결했지만 실제 두
스테이징 호스트 사이의 공인 HTTPS 전달은 아직 검증하지 않았다. 로컬 Caddy 내부 CA
검증을 원격 전달 완료로 해석하지 않는다. 위 생산자·소비자 교차 검증은 계약 팩
`2.0.0-rc.1`까지의 근거다. 입력 계약을 엄격하게 한 현재 `2.0.0-rc.2`는 BATON 실제
serializer와 다시 검증해야 한다.

## 현재 검증 근거

### 저장소 전체

```shell
./gradlew --no-daemon test :bootstrap:bootJar contractsZip
```

성공했다. 다섯 모듈의 전체 테스트, PostgreSQL Testcontainers 빈 데이터베이스 V1~V7
적용, 대표 V2 데이터의 V3~V7 업그레이드와
`bootstrap-0.1.0-SNAPSHOT.jar`, 재현 가능한 이벤트 계약 ZIP 생성을 확인했다.

주요 통합 증거는 다음 계약을 포함한다.

- 수신 중복·충돌·미지원·오래된 리비전·리비전 공백, 고정 v1 지문과 최초 충돌 탐지 시각
- 현재 관심 항목 단건·상태별 키셋·적용 전이와 `ETag` 조건부 조회
- 에디션 멱등성·불변성·`A → B → A` 새 세대, 이력·비교·주간 최신과 `ETag`
- 소수 정수·알 수 없는 필드·`24:00`·윤초를 거부하는 엄격한 이벤트 입력과 대표
  `400`·`404` `ProblemDetail`
- 32비트 양의 미래 `eventVersion`을 문법 오류가 아니라 `UNSUPPORTED`로 보존하는 경계
- 재구축 강제 실패의 원자적 롤백과 재구축·지원 이벤트 수신 잠금 직렬화
- V3 이전 에디션 근거의 `null` 유지, V4 복합 기본 키와 V5·V6 열 제거 업그레이드

### 이벤트 v2 계약 팩

저장소 전체 검증에서 모든 예시의 JSON Schema 일치, 동일 예시의 PostgreSQL 수신·재구축과
재현 가능한 계약 ZIP 생성을 확인했다. 이 근거는 계약 팩 게시나 원격 전달 성공을 뜻하지
않는다.

### 패키지와 스테이징 실행

- Java 21과 PostgreSQL 18.4에서 패키지 JAR의 Tomcat·Flyway·DataSource·aggregate health
  결합을 실제 로컬 소켓으로 확인했다.
- 기본 스테이징 Compose에서 digest 고정 Java 21 이미지, 비루트 UID/GID `10001`, 읽기
  전용 루트, 내부 PostgreSQL, 파일 기반 데이터베이스·Bearer와 loopback 호스트 게시를
  확인했다.
- BRIEF 호스트 게시 주소를 환경 변수에서 제거하고 `127.0.0.1`로 고정했다. 새 이미지를
  실제 빌드한 뒤 임시 Compose에서 `HostIp=127.0.0.1`, 비루트·읽기 전용 실행, 내부
  PostgreSQL aggregate health, 무인증 `401`과 현재 Bearer의 계약 v2 예시 `202`를 확인했다.
- Caddy `https` profile을 `BRIEF_STAGING_HOST=localhost`와 비기본 host port로 기동하고
  내부 CA 루트를 `curl --cacert`로 신뢰했다. 무인증 이벤트 `401`, 현재 Bearer의 계약 v2
  예시 `202 APPLIED`, 외부 `/actuator/health` `404`를 확인했다.
- Caddy 접근 로그에 Authorization 원문이 없고 읽기 전용 루트,
  `no-new-privileges`, `cap_drop=ALL`, `cap_add=NET_BIND_SERVICE`를 확인했다.
- 실제 Docker 연결은 내부 `data`에 PostgreSQL·BRIEF, 내부 `proxy`에 BRIEF·Caddy,
  외부 송신 가능한 `egress`에 Caddy만 존재했다.

네트워크·capability 축소는 Compose만 바꿨으므로 동일 이미지의 실제 기동과 HTTPS 요청으로
검증하고 제품 테스트와 `bootJar`를 반복하지 않았다. 모든 임시 컨테이너·네트워크·볼륨,
내부 CA와 임시 비밀 파일은 검증 뒤 제거했다.

### 자동 검증 구성

`.github/workflows/verify.yml`은 pull request와 `main` push에서 Java 21을 사용한다.
외부 Action은 전체 commit SHA로 고정했고 `gradle/actions/setup-gradle`이 Gradle 실행 전에
wrapper JAR을 검증하고 캐시를 구성한다. 이어 `test :bootstrap:bootJar contractsZip`, 로컬·
HTTPS profile의 Compose 구문, 실제 Dockerfile 빌드와 고정 Caddy 이미지의 설정 유효성을
검증한다. 2026-08-28 PR #1의 최초 GitHub Actions 원격 실행에서 이 검증 구성이 통과했다.

## 미검증·미결정 범위

- 공인 DNS·ACME 인증서와 실제 BATON 스테이징 호스트→BRIEF Caddy 원격 전달
- BATON 백엔드→BRIEF 조회·에디션 생성용 서비스 인증과 비공개 네트워크 경로
- BATON 조회 client, 생성 대상·실행 기록·재시도와 실제 사용자·생성 종단 간 검증
- WATCH·RELAY·GO 생산자 연동과 브로커
- 수신 기록·충돌 증거·에디션의 삭제·압축·외부 보관과 숫자 보존 기간
- 재구축 SLO·잠금 제한 시간, 체크포인트, 백업·복구와 RPO·RTO
- Caddy 인증서 볼륨 소유권을 포함한 비루트 전환과 다중 인스턴스 고가용성
- 이미지 registry와 릴리스 정책
- Gradle dependency verification metadata의 신뢰 가능한 최초 checksum 검토와
  플랫폼 간 유지 절차. 현재 wrapper 배포본 checksum과 최소 CI를 우선하고, 실제 작업
  의존성에서 생성된 대규모 metadata는 검토 없이 추가하지 않는다.
- 라이선스

## 다음 진입점

운영 우선순위는 실제 BATON 스테이징 호스트에서 BRIEF의 공인 HTTPS origin으로 이벤트를
보내는 종단 간 검증이다. 다음 조건을 모두 확인하기 전에는 계약 팩을 안정 버전으로
승격하지 않는다.

1. 공인 DNS와 ACME 인증서를 사용하고 인증서 검증을 끄지 않는다.
2. BATON 실제 serializer의 `2.0.0-rc.2` 본문이 Caddy를 거쳐 BRIEF에 저장된다.
3. 현재 Bearer 성공, 잘못된 Bearer `401`, 동일 이벤트 재전달 `200`을 확인한다.
4. Caddy 로그와 BRIEF 로그에 Authorization·token·원문 비밀이 남지 않는다.
5. 실패 시 BATON 원본 트랜잭션은 유지되고 outbox가 재시도 가능한 상태를 보존한다.

이 작업은 실제 스테이징 DNS·비밀·방화벽 변경 권한이 필요하다. 그 권한이 없는 로컬
작업에서는 숫자 SLO나 별도 배포 자동화를 추측해 추가하지 않는다.

사용자 조회·생성 구현은 별도 서비스 인증과 비공개 네트워크 경로를 먼저 채택한 뒤 다음
순서로 진행한다.

1. BATON이 권한 확인 뒤 호출할 BRIEF 조회·생성 경로의 최소 허용 목록과 자격 증명 교체
   경계를 고정한다.
2. BATON 백엔드에 조회 client를 연결하고 작업공간·시즌 범위 격리와 사용자 거부를 종단
   간으로 확인한다.
3. BATON의 내구성 있는 생성 실행 기록이 해당 범위의 이벤트 전달 완료를 확인한 뒤 기존
   BRIEF 생성 명령을 호출하게 한다.
4. 정상 생성, 응답 유실 뒤 재시도와 BATON 경유 조회까지 실제 두 서비스로 검증한다.

## 문서 진입점

- [제품 소개와 실행 방법](README.md)
- [PRD·ADR 전체 색인](docs/README.md)
- [이벤트 v2 계약 팩](contracts/README.md)
- [개발 작업 규칙](AGENTS.md)
