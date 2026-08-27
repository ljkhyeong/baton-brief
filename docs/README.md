# BATON BRIEF 문서 색인

이 문서는 제품 계약과 구조 결정의 진입점이다. 현재 구현·검증·남은 작업은
[HANDOFF](../HANDOFF.md), 처음 실행하는 방법은 [README](../README.md), 개발 절차는
[AGENTS](../AGENTS.md)와 프로젝트 로컬 스킬을 따른다.

## 문서 역할

| 문서 | 소유 내용 |
| --- | --- |
| `README.md` | 제품 소개, 기능 지도, 첫 실행 방법과 주요 문서 진입점 |
| `docs/README.md` | PRD·ADR 전체 색인과 문서 탐색 경로 |
| `docs/PRD/**` | 제품 동작, HTTP·이벤트·저장 의미, 수용 기준과 비목표 |
| `docs/ADR/**` | 장기 구조·기술·보안·실행 결정과 대안·장단점 |
| `HANDOFF.md` | 현재 검증 근거, 미검증 범위와 다음 진입점 |
| `AGENTS.md`, `.agents/skills/**` | 개발 작업 절차와 변경 시 지켜야 할 불변식 |
| `contracts/**` | 이벤트 v2 언어 중립 JSON Schema와 예시 |

## 제품 기준과 공통 계약

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0001 제품 기준](PRD/0001_product-baseline/spec.md) | 초안 | 서비스 경계, 핵심 개념과 첫 MVP 범위 |
| [PRD-0002 MVP 계약](PRD/0002_mvp-contract/spec.md) | 채택됨 | 이벤트 수신, 규칙 v1 투영, 재구축과 주간 에디션 |
| [PRD-0004 표준 요청 오류](PRD/0004_problem-detail/spec.md) | 채택됨 | RFC 9457 `ProblemDetail` 적용 범위와 비노출 경계 |
| [PRD-0006 최소 상태 확인](PRD/0006_minimum-health/spec.md) | 채택됨 | Spring Boot aggregate health와 DB contributor |
| [PRD-0008 보존·재구축 경계](PRD/0008_retention-rebuild-boundary/spec.md) | 채택됨 | `retain-all`, 원자적 재구축과 동시성 경계 |

## 수신 이벤트와 증거

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0007 수신 증거 단건](PRD/0007_event-receipt-query/spec.md) | 채택됨 | 최초 수신 결과와 최초 충돌 탐지 시각 조회 |
| [PRD-0011 이상 수신 증거 이력](PRD/0011_event-receipt-anomalies/spec.md) | 채택됨 | 작업공간·시즌 범위 이상 기록 키셋 조회 |
| [PRD-0018 BATON 생산자 선행조건](PRD/0018_baton-producer-compatibility/spec.md) | 채택됨 | 생산 의미·정체성·리비전·outbox·종단 간 검증 게이트 |
| [PRD-0019 연속성 신호 이벤트 v2](PRD/0019_baton-continuity-event-v2/spec.md) | 채택됨 | 다섯 신호, 원본 심각도, v1 호환 소비와 계약 팩 |
| [PRD-0020 이벤트 수신 인증](PRD/0020_baton-event-authentication/spec.md) | 채택됨 | BATON 전용 Bearer와 현재·직전 token 교체 경계 |

## 현재 관심 항목

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0013 현재 단건 조회](PRD/0013_current-attention-item/spec.md) | 채택됨 | 복합 정체성으로 현재 투영 단건 조회 |
| [PRD-0014 현재 활성 목록](PRD/0014_active-attention-items/spec.md) | 채택됨 | `ACTIVE` 항목의 복합 키셋 조회 |
| [PRD-0015 현재 상태 필터](PRD/0015_attention-item-status-filter/spec.md) | 채택됨 | `ACTIVE` 기본값과 `RESOLVED` 선택 |
| [PRD-0016 상태 전이 증거](PRD/0016_attention-item-transitions/spec.md) | 채택됨 | 실제 적용 전이의 집계 리비전 키셋 조회 |
| [PRD-0017 현재 단건 조건부 조회](PRD/0017_current-attention-item-etag/spec.md) | 채택됨 | 현재 표현의 `ETag`와 `If-None-Match` |

## 불변 에디션

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0003 에디션 이력](PRD/0003_edition-history/spec.md) | 채택됨 | `generation` 배타 키셋 이력 조회 |
| [PRD-0005 에디션 비교](PRD/0005_edition-comparison/spec.md) | 채택됨 | 추가·제거·변경과 기준·대상 순서 |
| [PRD-0009 주간 범위 최신](PRD/0009_weekly-latest-edition/spec.md) | 채택됨 | 주간·시간대 정확 범위의 최신 불변 스냅샷 |
| [PRD-0010 에디션 리비전 근거](PRD/0010_edition-revision-evidence/spec.md) | 채택됨 | 집계 리비전·공백 고정과 이전 항목 `null` 호환성 |
| [PRD-0012 에디션 조건부 조회](PRD/0012_edition-etag/spec.md) | 채택됨 | 전체 에디션 응답의 `ETag`와 `If-None-Match` |

## 스테이징 실행

| 문서 | 상태 | 내용 |
| --- | --- | --- |
| [PRD-0021 스테이징 실행](PRD/0021_staging-runtime-boundary/spec.md) | 채택됨 | 비루트 BRIEF, 내부 PostgreSQL과 파일 기반 비밀 |
| [PRD-0022 HTTPS 이벤트 수신](PRD/0022_https-event-ingress/spec.md) | 채택됨 | Caddy 단일 허용 경로, 네트워크 격리와 로컬 CA 검증 |

## 아키텍처 결정

| 문서 | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0001 마이크로서비스 경계](ADR/0001_microservice-boundary/adr.md) | 채택됨 | BRIEF의 독립 읽기 모델 서비스 소유권 |
| [ADR-0002 기술 스택과 모듈](ADR/0002_technology-stack/adr.md) | 채택됨 | Kotlin/JDK 21, Spring Boot, PostgreSQL, JDBC와 다섯 모듈 |
| [ADR-0003 이벤트 Bearer](ADR/0003_baton-event-authentication/adr.md) | 채택됨 | Spring Security 표준 Bearer와 수동 교체 구간 |
| [ADR-0004 스테이징 컨테이너](ADR/0004_staging-container-runtime/adr.md) | 채택됨 | 비루트·읽기 전용 실행 이미지와 내부 데이터베이스 |
| [ADR-0005 Caddy HTTPS 앞단](ADR/0005_caddy-event-ingress/adr.md) | 채택됨 | 자동 HTTPS, 공개 경로 허용 목록과 최소 capability |

## 다른 기준 자료

- [이벤트 v2 계약 팩](../contracts/README.md)
- [현재 검증과 다음 작업](../HANDOFF.md)
- [개발 작업 규칙](../AGENTS.md)
