# BATON BRIEF 문서 색인

제품 요구사항과 설계 문서를 찾는 색인이다. 현재 구현·검증·남은 작업은
[HANDOFF](../HANDOFF.md), 처음 실행하는 방법은 [README](../README.md), 개발 절차는
[AGENTS](../AGENTS.md)와 프로젝트 로컬 스킬을 따른다.

## 문서 역할

| 문서 | 내용 |
| --- | --- |
| `README.md` | 제품 소개, 주요 기능, 첫 실행 방법과 관련 문서 |
| `docs/README.md` | PRD·ADR 전체 색인과 문서 탐색 경로 |
| `docs/PRD/**` | 제품 동작, API·이벤트·저장 규칙, 수용 기준과 제외 범위 |
| `docs/ADR/**` | 장기 구조·기술·보안·실행 결정과 대안·장단점 |
| `HANDOFF.md` | 검증 결과, 미검증 범위와 다음 작업 |
| `AGENTS.md`, `.agents/skills/**` | 개발 절차와 변경 시 지켜야 할 규칙 |
| `contracts/**` | 언어에 관계없이 사용할 이벤트 v2 JSON Schema와 예시 |

## 제품 기준과 공통 계약

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0001 제품 기준](PRD/0001_product-baseline/spec.md) | 채택됨 | 서비스 경계, 핵심 개념과 첫 MVP 범위 |
| [PRD-0002 MVP 계약](PRD/0002_mvp-contract/spec.md) | 채택됨 | 이벤트 수신, 규칙 v1 투영, 재구축과 주간 브리프 |
| [PRD-0004 표준 요청 오류](PRD/0004_problem-detail/spec.md) | 채택됨 | RFC 9457 `ProblemDetail` 적용 범위와 응답에서 제외할 정보 |
| [PRD-0006 최소 상태 확인](PRD/0006_minimum-health/spec.md) | 채택됨 | Spring Boot의 애플리케이션·DB 상태 확인 |
| [PRD-0008 보존·재구축 규칙](PRD/0008_retention-rebuild-boundary/spec.md) | 채택됨 | 수신 기록 전체 보존, 재구축 트랜잭션과 동시 실행 제어 |
| [PRD-0026 이벤트 수신 결과 지표](PRD/0026_event-ingestion-metrics/spec.md) | 채택됨 | 결과별 카운터와 지표 접근 제한 |

## 이벤트 수신과 기록

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0007 수신 기록 단건](PRD/0007_event-receipt-query/spec.md) | 채택됨 | 최초 수신 결과와 최초 충돌 탐지 시각 조회 |
| [PRD-0011 이상 수신 기록](PRD/0011_event-receipt-anomalies/spec.md) | 채택됨 | 작업공간·시즌별 이상 수신 기록의 커서 기반 조회 |
| [PRD-0018 BATON 이벤트 연동 조건](PRD/0018_baton-producer-compatibility/spec.md) | 채택됨 | 이벤트 의미·식별자·변경 번호·outbox·연동 검증 조건 |
| [PRD-0019 연속성 신호 이벤트 v2](PRD/0019_baton-continuity-event-v2/spec.md) | 채택됨 | 다섯 신호, 원본 심각도, v1 수신 호환성과 계약 팩 |
| [PRD-0020 이벤트 수신 인증](PRD/0020_baton-event-authentication/spec.md) | 채택됨 | BATON 전용 Bearer와 현재·직전 토큰 교체 방식 |

## 점검 항목

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0013 현재 단건 조회](PRD/0013_current-attention-item/spec.md) | 채택됨 | 복합 식별자로 현재 점검 항목 한 건 조회 |
| [PRD-0014 현재 활성 목록](PRD/0014_active-attention-items/spec.md) | 채택됨 | `ACTIVE` 항목을 복합 식별자 커서로 조회 |
| [PRD-0015 현재 상태 필터](PRD/0015_attention-item-status-filter/spec.md) | 채택됨 | `ACTIVE` 기본값과 `RESOLVED` 선택 |
| [PRD-0016 상태 변경 이력](PRD/0016_attention-item-transitions/spec.md) | 채택됨 | 실제 적용된 상태 변경을 리비전 순서로 조회 |
| [PRD-0017 현재 단건 조건부 조회](PRD/0017_current-attention-item-etag/spec.md) | 채택됨 | `ETag`가 같으면 `304`를 반환하는 조건부 조회 |
| [PRD-0027 현재 활성 항목 요약](PRD/0027_attention-item-summary/spec.md) | 채택됨 | 전체·업무 종류별 활성 심각도 개수와 리비전 공백 항목 수 |
| [PRD-0028 점검 항목 필터](PRD/0028_attention-item-filters/spec.md) | 채택됨 | 현재 목록의 업무 종류·심각도·리비전 공백 필터와 기존 커서 유지 |
| [PRD-0030 주간 해소 요약](PRD/0030_weekly-resolution-summary/spec.md) | 채택됨 | 연속 리비전으로 해소를 확인하고 현재도 해소 상태인 항목 집계 |

## 브리프

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0003 브리프 이력](PRD/0003_edition-history/spec.md) | 채택됨 | 전체·주간 이력 필터와 생성 번호 커서 조회 |
| [PRD-0005 브리프 비교](PRD/0005_edition-comparison/spec.md) | 채택됨 | 추가·제거·변경과 기준·대상 순서 |
| [PRD-0009 주간 범위 최신](PRD/0009_weekly-latest-edition/spec.md) | 채택됨 | 주간·시간대가 일치하는 최신 저장 브리프 |
| [PRD-0010 브리프 리비전 근거](PRD/0010_edition-revision-evidence/spec.md) | 채택됨 | 집계 리비전·공백 고정과 이전 항목 `null` 호환성 |
| [PRD-0012 브리프 조건부 조회](PRD/0012_edition-etag/spec.md) | 채택됨 | 브리프 본문·비교 결과의 `ETag`와 `If-None-Match` |
| [PRD-0029 주간 변경·이전 미해소 구분](PRD/0029_edition-carry-over/spec.md) | 채택됨 | 브리프 선정 규칙 v2와 생성 당시 항목 분류 |

## 스테이징 실행

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0021 스테이징 실행](PRD/0021_staging-runtime-boundary/spec.md) | 채택됨 | 비루트 BRIEF, 내부 PostgreSQL과 파일 기반 비밀 |
| [PRD-0022 HTTPS 이벤트 수신](PRD/0022_https-event-ingress/spec.md) | 채택됨 | Caddy 단일 허용 경로, 네트워크 격리와 로컬 CA 검증 |

## BATON 애플리케이션 연결

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0023 BATON 백엔드 경유 조회](PRD/0023_baton-mediated-brief-query/spec.md) | 채택됨 | BATON 사용자 권한 판정 뒤 허용한 BRIEF 내부 조회 중계 |
| [PRD-0024 BATON 주도 브리프 생성](PRD/0024_baton-driven-edition-generation/spec.md) | 채택됨 | BATON 대상·시점 결정과 기존 BRIEF 멱등 생성 명령 호출 |
| [PRD-0025 BATON 서비스 API 인증](PRD/0025_baton-service-api-security/spec.md) | 채택됨 | 별도 Bearer, 허용 경로와 비공개 HTTPS 연결 |

## 아키텍처 결정

| 문서 | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0001 마이크로서비스 경계](ADR/0001_microservice-boundary/adr.md) | 채택됨 | 조회용 데이터를 별도로 관리하는 BRIEF의 담당 범위 |
| [ADR-0002 기술 스택과 모듈](ADR/0002_technology-stack/adr.md) | 채택됨 | Kotlin/JDK 21, Spring Boot, PostgreSQL, JDBC와 다섯 모듈 |
| [ADR-0003 이벤트 Bearer](ADR/0003_baton-event-authentication/adr.md) | 채택됨 | Spring Security 표준 Bearer 인증과 수동 토큰 교체 |
| [ADR-0004 스테이징 컨테이너](ADR/0004_staging-container-runtime/adr.md) | 채택됨 | 비루트·읽기 전용 실행 이미지와 내부 데이터베이스 |
| [ADR-0005 Caddy HTTPS 앞단](ADR/0005_caddy-event-ingress/adr.md) | 채택됨 | 자동 HTTPS, 공개 경로 허용 목록과 최소 capability |
| [ADR-0006 BATON·BRIEF 책임 분리](ADR/0006_baton-brief-application-boundary/adr.md) | 채택됨 | BATON의 권한 확인·생성 요청, BRIEF의 조회 데이터·브리프 관리 |
| [ADR-0007 BATON 서비스 API 인증](ADR/0007_baton-service-api-security/adr.md) | 채택됨 | 이벤트와 분리한 Bearer와 서비스 전용 HTTPS 앞단 |
| [ADR-0008 호스트 권한 운영](ADR/0008_host-authorized-operations/adr.md) | 채택됨 | 한 번 실행하고 종료하는 진단·재구축 명령과 컨테이너 내부 지표 조회 |

## 다른 기준 자료

- [변경 범위별 검증과 결과 재사용](development/verification.md)
- [brief.b4ton.com 배포 준비](operations/brief-b4ton-com-deployment.md)
- [PostgreSQL 백업·정기 실행·격리 복원](operations/postgresql-backup-restore.md)
- [수신 진단·재구축과 지표 조회](operations/diagnostics-and-metrics.md)
- [이벤트 v2 계약 팩](../contracts/README.md)
- [현재 검증과 다음 작업](../HANDOFF.md)
- [개발 작업 규칙](../AGENTS.md)
