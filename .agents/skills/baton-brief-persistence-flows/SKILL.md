---
name: baton-brief-persistence-flows
description: BATON BRIEF의 Flyway 마이그레이션·테이블 제약·인덱스, JdbcClient 쿼리·행 매핑과 advisory lock·트랜잭션 경계를 변경할 때 사용한다.
---

# BATON BRIEF 저장소와 마이그레이션

## 관련 위치

변경 대상의 문서만 읽는다.

- 마이그레이션: `adapter-out-persistence/src/main/resources/db/migration/V*.sql`
- 저장 구현: `JdbcBriefPersistenceAdapter.kt`, 포트: `BriefUseCases.kt`의 `BriefPersistencePort`
- 기술·마이그레이션 요약: [ADR-0002](../../../docs/ADR/0002_technology-stack/adr.md)
- 보존·재구축 잠금: [PRD-0008](../../../docs/PRD/0008_retention-rebuild-boundary/spec.md)
- 배포 중 잠금 영향: [배포 준비](../../../docs/operations/brief-b4ton-com-deployment.md)의 스키마 적용 절

## 유지할 규칙

- 보존할 데이터나 운영 DB가 생긴 뒤에는 적용된 `V*.sql`을 고치지 않고 최신 번호 다음 버전을 추가한다.
  운영 전인 지금은 `V1`을 직접 고친다. 기존 볼륨은 Flyway 체크섬 검증에서 기동이 거부되므로
  HANDOFF에 로컬·스테이징 볼륨 재생성을 적는다. SQL 마이그레이션만 사용한다.
- 여러 테이블이 쓰는 이벤트 종류·상태·심각도 값 목록은 `source_event_type`·`source_event_state`·
  `brief_severity` DOMAIN의 `*_known` CHECK에 한 번만 둔다. 값을 바꾸면 열이 아니라 DOMAIN 제약을 고친다.
- 저장 무결성은 DB 제약으로 보장한다. 수신 중복·충돌은 `event:<eventId>` 잠금 아래 저장된 수신 필드를
  읽어 판정해 수신 순번을 소비하지 않는다. 최초 충돌 기록은 `ON CONFLICT DO NOTHING` 결과로,
  참조 관계는 FK로 보장한다.
- 수신 기록과 투영 효과, 재구축의 투영 교체, 에디션과 항목 저장은 각각 한 `@Transactional`
  안에서 처리한다. 잠금은 `pg_advisory_xact_lock` 계열이며 트랜잭션 종료와 함께 풀린다.
- 잠금 키는 이벤트 `event:<eventId>`, 현재 항목 `attention:<작업공간>:<시즌>:<종류>:<참조>`와
  투영 전체 `brief:projection`이다. 지원 이벤트 수신은 투영 키를 공유 모드로, 재구축·에디션 생성은
  배타 모드로 잡는다. 잠금 순서를 바꾸면 교착과 직렬화를 함께 검토한다.
- 저장할 시각은 `BriefService`가 마이크로초로 맞춰 넘기고 저장소는 UTC 오프셋으로 쓴다. 단순 행 매핑은
  `JdbcClient.query(Class)` 또는 `DataClassRowMapper`에 맡기고, `Instant` 열이 있는 행은
  `DataClassRowMapper`를 확장한 매퍼로 `OffsetDateTime`을 거쳐 읽는다.
- 기존 행의 의미를 바꾸는 열·제약은 이전 버전 값의 처리 규칙을 정한다. 이전에 없던 근거는
  `null`로 두고 현재 투영에서 추정해 채우지 않는다. 수신 기록과 최초 충돌은 삭제·압축하지 않는다.
- 인덱스 생성·제약 추가처럼 쓰기를 막는 DDL은 수신 대기 시간을 배포 문서에 남기고 ADR-0002의
  마이그레이션 요약을 갱신한다. 운영 명령은 Flyway를 실행하지 않으므로 앱 배포로 먼저 적용한다.

## 변경에 맞는 검증

- 보존 데이터가 생긴 뒤 기존 행에 영향을 주는 변경은 대상 이전 버전까지 migrate하고 대표 이전 행을
  넣은 뒤 최신으로 올려 행 보존과 새 제약을 확인한다. 빈 DB 성공으로 대신하지 않는다.
- 쿼리 변경은 해당 통합 시나리오의 범위 격리·정렬·페이지 경계를 확인한다. 인덱스·성능 변경은 격리 DB의
  합성 데이터에서 기존·변경 SQL의 결과 일치와 실행 계획을 비교하고 운영 성능 보장으로 보고하지 않는다.
- 통합 테스트는 Testcontainers PostgreSQL 18.6을 사용한다. Docker 미기동으로 건너뛰거나 실패한
  테스트를 성공으로 보고하지 않는다. 잠금 변경은 [투영 스킬](../baton-brief-projection-flows/SKILL.md)의
  재구축·잠금 검증을 따른다.
