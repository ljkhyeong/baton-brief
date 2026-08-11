# BATON BRIEF

BATON BRIEF는 BATON 생태계에서 발생한 운영 사실을 설명 가능한 관심 항목으로 투영하고,
일정 시점의 불변 운영 브리프를 만드는 독립 read-model 서비스다.

> 현재 상태: 저장소와 제품 경계만 만든 초기 스캐폴드다. 런타임, API, 메시지 계약과 기술
> 스택은 아직 채택하지 않았다.

## 왜 BRIEF인가

BATON, WATCH, RELAY와 GO는 각각 조직 운영 원본, 자료 상태 점검, 전달, 링크 생명주기를
소유한다. BRIEF는 그 책임을 복제하지 않고, 여러 시점의 사실을 사용자가 읽고 행동할 수
있는 하나의 운영 snapshot으로 고정한다.

```text
BATON domain events ──> BRIEF inbox ──> AttentionItem projection
                                             │
                                             └─> immutable BriefEdition
                                                        │
                                                        ├─> BATON UI
                                                        └─> BRIEF_READY -> RELAY (later)
```

WATCH의 health 사실은 WATCH 데이터베이스에서 직접 읽지 않는다. BATON이 수신하고
정규화한 권위 있는 도메인 이벤트를 통해서만 BRIEF로 전달한다.

## 서비스 경계

BRIEF가 소유한다.

- source event inbox와 멱등 처리 결과
- workspace/season별 `AttentionItem` read model
- 선정 규칙 버전, 이유 코드, 심각도와 원본 reference
- generation cursor, source watermark와 rebuild 상태
- 생성 시점의 항목을 고정한 immutable `BriefEdition`
- edition 생성 성공·실패와 재생 가능한 운영 증거

BRIEF가 소유하지 않는다.

- 팀, 시즌, 역할, 루틴, 결정, 인수인계와 최종 권한: BATON
- URL 점검 일정, 시도, 결과와 현재 health: BATON WATCH
- 구독, 채널, provider binding, 전송 retry와 결과: BATON RELAY
- 공개 코드, 만료, 폐기와 redirect: BATON GO
- 운영 사실의 최종 판정이나 원본 감사 사건

## 첫 MVP

1. BATON이 after-commit으로 발행한 소수의 versioned event만 수신한다.
2. `reasonCode`, `severity`, `sourceReference`, `observedAt`을 가진 설명 가능한
   `AttentionItem`을 만든다.
3. workspace/season 단위의 주간 `BriefEdition`을 결정적으로 생성한다.
4. 최신 edition과 특정 edition을 조회한다.
5. 정확한 event replay를 멱등 처리하고 projection 전체 rebuild를 지원한다.
6. 첫 소비자는 BATON UI로 제한한다. AI 요약과 외부 provider 전달은 포함하지 않는다.

## 설계 원칙

- 서비스별 데이터베이스를 유지하고 다른 서비스의 테이블이나 entity를 공유하지 않는다.
- 외부 I/O는 source transaction 안에서 수행하지 않는다.
- at-least-once 수신, 중복, 지연 도착과 재생을 정상 흐름으로 다룬다.
- 시간 의존 규칙에는 주입 가능한 `Clock`과 명시적인 timezone을 사용한다.
- edition은 생성 후 수정하지 않는다. 정정은 새 edition 또는 명시적인 supersession으로
  표현한다.
- 사람에게 보이는 문장만 저장하지 않고 안정적인 이유 코드와 source reference를 함께
  보존한다.
- PII, credential, provider address와 원문 secret을 event, 로그, metric label에 넣지 않는다.

## 착수 전에 채택할 계약

- BATON domain-event envelope, event version과 transactional outbox
- source ordering이 아니라 aggregate revision을 사용하는 projection 규칙
- replay, retention, rebuild와 poison-event 처리
- BATON membership 기반 조회 권한
- 향후 `BRIEF_READY`를 RELAY에 전달하는 작은 reference-only 계약
- 향후 GO deep link가 필요할 때의 허용 locator 계약

## 문서

- [제품 기준](docs/PRD/0001_product-baseline/spec.md)
- [마이크로서비스 경계](docs/ADR/0001_microservice-boundary/adr.md)
- [다음 작업](HANDOFF.md)

## 기술 스택

아직 결정하지 않았다. 첫 입력 이벤트 계약과 projection 불변식을 정한 뒤, BATON
생태계의 운영 비용과 독립 배포 필요성을 기준으로 선택한다.

## 라이선스

아직 라이선스를 채택하지 않았다. 공개 저장소이지만 라이선스가 추가되기 전에는 별도의
사용 허가가 부여되지 않는다.
