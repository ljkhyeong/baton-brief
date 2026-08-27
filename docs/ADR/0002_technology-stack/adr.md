# ADR-0002: 기술 스택과 모듈 경계

- 상태: 채택됨
- 결정일: 2026-08-11
- 수정일: 2026-08-25

## 맥락

ADR-0001은 BATON BRIEF를 독립 읽기 모델 서비스로 분리했지만, 구현에 사용할 언어와
실행 환경, 저장 기술과 모듈 경계는 정하지 않았다. 첫 뼈대를 만들기 전에 버전과 의존 방향을
고정해 비즈니스 규칙이 전송 방식이나 영속성 프레임워크에 종속되는 것을 막고,
개발·테스트 기준을 일관되게 만들 필요가 있다.

최초 Java 21 뼈대를 만든 뒤에도 BRIEF에는 제품 코드와 저장 스키마가 없으므로 언어 전환
비용이 낮다. BRIEF가 다룰 불변 에디션, 버전이 있는 이벤트와 제한된 상태는 Kotlin의
데이터·값 모델, 널 안전성과 봉인 타입으로 명시적으로 표현하기에 적합하다. 이
도메인 적합성을 주된 이유로 Kotlin을 선택한다.

Java 25 툴체인·바이트코드·실행 환경 조합도 현재 개발 환경에서 빌드와 기동이 가능한 것을
확인했다. 그러나 BRIEF의 MVP 기능은 Java 25 기능을 요구하지 않고, BATON의 구현된 서비스와
CI·컨테이너 기준은 Java 21에 맞춰져 있다. BRIEF와 CAL을 위한 고정된 JDK 25 CI·실행
이미지도 아직 없다. 호환 가능성만으로 운영 기준을 올리지 않고 MVP는 Java 21 기준으로
맞춘다.

## 결정

### 플랫폼과 저장 기술

- Kotlin/JVM 2.3.21을 사용한다.
- Java 21 툴체인을 사용하고 Kotlin 바이트코드 대상을 JVM 21로 고정한다. BRIEF 실행 환경도
  JDK 21을 사용한다.
- Spring Boot와 Spring Boot BOM은 4.1.0을 사용한다.
- 빌드 스크립트는 Kotlin DSL을 사용한다. 빌드는 Gradle 다중 프로젝트로 구성하고 Gradle
  래퍼는 9.2.1로 고정한다.
- BRIEF 전용 데이터베이스는 PostgreSQL 18.4를 사용한다.
- 영속성 구현은 Spring JDBC의 `JdbcClient`와 Flyway 마이그레이션을 사용한다.
- JPA와 ORM 엔티티는 사용하지 않는다. 도메인 모델과 데이터베이스 행 매핑을 분리한다.
- 로컬 데이터베이스는 `postgres:18.4-alpine`을 사용하는 `compose.yml`로 제공한다. 애플리케이션은
  로컬 프로필 전용 별칭 대신 Spring Boot 표준 데이터 소스·Flyway 속성을 사용하고
  Flyway를 기본 활성화한다.
- 테스트는 필요한 모듈에만 두고 BOM이 관리하는 JUnit Jupiter, AssertJ와 PostgreSQL
  Testcontainers를 사용한다. 개별 라이브러리 버전은 별도로 고정하지 않는다.
- 이벤트 v2의 언어 중립 계약은 Draft 2020-12 JSON Schema와 JSON 예시로 제공한다.
  `contracts/VERSION`을 계약 팩 버전의 단일 기준으로 사용하고 Gradle 표준 `Zip` 작업으로
  `contracts/**`와 해당 PRD를 묶는다. 계약 스키마 검증기는 제품 런타임이 아닌
  `bootstrap` 테스트에만 두며 Spring Boot BOM이 관리하지 않으므로 버전을 명시한다.
- 실행 상태 확인에는 `spring-boot-starter-actuator`의 표준 aggregate health와 `DataSource`
  기반 DB health contributor 자동 구성을 사용한다. 제품 API와 분리된
  `/actuator/health`에서 aggregate 상태만 노출하며 별도 controller, DTO,
  `HealthIndicator`와 DB 확인 SQL을 만들지 않는다.

CAL의 미병합 MVP 작업에서 진행 중인 Kotlin/JVM과 Spring Boot 4.1.0 기준은 참고하되 JDK
25나 Gradle 9.6.1까지 복제하지 않는다. Kotlin 2.3.21이 공식적으로 지원하는 Gradle 범위는
9.3까지이므로 BRIEF는 호환 범위 안의 9.2.1을 의도적으로 유지한다.

### 모듈과 의존 방향

다음 다섯 Gradle 모듈을 둔다.

- `domain`: 프레임워크에 독립적인 도메인 모델과 불변식
- `application`: 유스케이스와 입력·출력 포트
- `adapter-in-web`: HTTP 입력을 애플리케이션 유스케이스로 변환하는 입력 어댑터
- `adapter-out-persistence`: `JdbcClient`, Flyway와 PostgreSQL을 사용하는 출력 어댑터
- `bootstrap`: Spring Boot 애플리케이션과 구성 루트

의존은 바깥쪽에서 안쪽으로만 향한다.

```text
bootstrap ─┬─> adapter-in-web ──────────> application ──> domain
           └─> adapter-out-persistence ─> application ──> domain
```

`domain`은 다른 프로젝트 모듈이나 Spring에 의존하지 않는다. `application`은 `domain`에만
의존한다. 두 어댑터는 서로 의존하지 않으며 `application`이 정의한 포트 경계를 넘지
않는다. `bootstrap`은 모듈을 조립하되 비즈니스 규칙을 소유하지 않는다. 안쪽 모듈은
어댑터나 `bootstrap`을 참조하지 않는다.

### 이번 결정에서 제외하는 항목

현재 브로커나 외부 시스템 연동 어댑터를 채택하지 않는다. 운영 배포 방식도
결정하지 않는다. 로컬 MVP의 내부 HTTP 엔드포인트는 PRD-0002를 따르고 BATON 이벤트
수신 전용 Bearer는 ADR-0003과 PRD-0020을 따른다. 다른 API의 운영 인증·인가는 별도
결정 전까지 만들지 않는다.

계약 팩을 게시할 저장소 릴리스·CI artifact와 장기 배포 위치는 아직 채택하지 않는다.
현재 RC ZIP 생성은 로컬 재현성과 BATON 생산자 검증 입력을 준비하는 범위이며, 게시나
생산자 버전 고정을 완료했다는 뜻이 아니다.

상세 health 정보, 탐색 페이지, 배포 probe, 별도 관리 포트와 커스텀 상태 코드는 채택하지
않는다. liveness·readiness와 외부 의존성 포함 정책은 컨테이너 또는 오케스트레이터 배포
계약을 정할 때 별도로 결정한다.

### 검증 기준

Kotlin/JDK 21 뼈대와 다섯 모듈에 다음 명령을 저장소 기준 검증으로 사용한다.

```shell
./gradlew test
./gradlew :bootstrap:bootJar
```

Java 25 뼈대에서의 과거 성공 결과를 Kotlin/JDK 21 산출물의 성공으로 간주하지
않는다. 현재 Kotlin/JDK 21 산출물로 대상 PostgreSQL 통합 테스트, Compose 구문,
전체 정리 후 테스트, 실행 JAR 생성과 Java 21.0.10 비웹 실행 환경/Flyway 기동 점검을 다시 검증했다.
세부 명령과 검증 경계는 `HANDOFF.md`를 따른다.

## 결과

장점:

- 도메인과 유스케이스를 웹, JDBC와 Spring Boot 수명주기에서 분리할 수 있다.
- SQL과 마이그레이션을 명시적으로 관리해 투영, 수신함과 에디션 저장 동작을 드러낼 수
  있다.
- 불변 에디션, 버전이 있는 이벤트와 제한된 상태를 Kotlin 타입으로 명시적으로 표현할
  수 있다.
- 기존 BATON 서비스의 Java 21 실행 환경 기준과 맞아 CI·컨테이너 운영 선택을 공유하기
  쉽다.
- 툴체인, 바이트코드 대상, 실행 환경, 프레임워크, 데이터베이스와 테스트 의존성 기준이
  고정되어 환경 차이를 줄인다.
- 어댑터를 추가할 때 애플리케이션 포트를 기준으로 의존 방향을 검토할 수 있다.

비용:

- JDBC 행 매핑, 애그리게이트 조립과 SQL을 직접 관리해야 한다.
- 작은 초기 서비스에도 다섯 모듈의 Kotlin 빌드 설정과 경계 검증이 필요하다.
- Spring Boot 4.1.0과 PostgreSQL 18.4에 맞춘 로컬·CI 환경을 준비해야 한다.
- Kotlin 플러그인과 Gradle의 공식 호환 범위를 함께 관리해야 한다.

## 보류한 대안

- Spring Data JPA: CRUD 구현량은 줄지만 투영 SQL, 쓰기 경계와 조회 비용을 숨길 수
  있어 채택하지 않았다.
- Java 언어 유지: 기존 최초 뼈대를 그대로 쓸 수 있지만 아직 제품 코드가 없고
  BRIEF의 불변 스냅샷, 버전이 있는 이벤트와 상태 모델을 Kotlin 타입으로 표현하는 이점이
  더 크다고 판단해 채택하지 않았다. Java 21 실행 환경 기준과 Kotlin 언어 선택은 별개의
  결정이다.
- Java/JVM 25: 공식 호환되고 개발 환경에서 빌드·기동을 확인했지만 MVP에 필요한 기능상
  이점과 CI·실행 이미지 기준이 없다. CAL을 포함한 운영선이 검증되면 별도 ADR 변경으로
  재검토한다.
- 단일 Spring Boot 모듈: 초기 파일 수는 적지만 도메인·애플리케이션 경계를 빌드 수준에서
  보호하지 못해 채택하지 않았다.
- Gradle 9.6.1: CAL의 미병합 MVP 작업과 래퍼 버전까지 같아지는 장점은 있지만 Kotlin
  2.3.21의 공식 지원 범위인 Gradle 9.3을 넘으므로 채택하지 않았다.
- 메시지 브로커와 외부 연동 어댑터를 함께 선택: 이벤트 봉투, 전달과 운영 계약이
  아직 없으므로 기술 스택 결정에 포함하지 않았다.

특정 JDK 공급자, 실행 컨테이너 이미지와 운영 배포 방식은 이번 결정에 포함하지
않는다. 로컬 PostgreSQL Compose 이미지도 애플리케이션의 운영 실행 이미지나
운영 데이터베이스 배포를 채택했다는 의미가 아니다. Java 21 실행 환경 기준을 운영
이미지가 이미 존재한다는 의미로 해석하지 않는다.
