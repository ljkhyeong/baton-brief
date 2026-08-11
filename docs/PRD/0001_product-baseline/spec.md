# PRD-0001: BATON BRIEF 제품 기준

- 상태: Draft
- 범위: 첫 독립 서비스 경계와 MVP

## 문제

BATON 생태계에는 역할 인수인계, 반복 운영, 결정과 자료 상태처럼 사용자가 주기적으로
확인해야 할 사실이 축적된다. 원본 화면을 모두 순회하는 대신, 특정 기간에 주의가 필요한
사실과 그 이유를 하나의 재현 가능한 운영 snapshot으로 제공할 필요가 있다.

## 목표

- BATON이 발행한 운영 사실을 멱등하게 수신한다.
- 사람이 이해할 수 있는 이유와 원본 reference를 가진 관심 항목을 만든다.
- workspace/season과 시간 window별 immutable brief edition을 생성한다.
- 같은 입력과 규칙 버전으로 같은 결과를 재현하고 전체 projection을 rebuild할 수 있다.

## 비목표

- BATON의 continuity 판단, 권한 또는 원본 도메인 상태 재구현
- WATCH 데이터베이스 직접 조회 또는 URL health 재판정
- RELAY의 구독, provider 호출, retry와 결과 관리
- GO의 공개 링크 생성·만료·폐기
- 자유 형식 AI 요약을 source of truth로 사용
- 첫 MVP의 개인별 추천, 이메일 또는 메시지 발송

## 핵심 개념

### SourceEventReceipt

수신한 event identity, version, payload fingerprint, 수신·처리 상태와 실패 분류를 보존한다.
같은 identity와 같은 payload의 replay는 기존 결과를 반환하고, 같은 identity의 다른 payload는
충돌로 격리한다.

### AttentionItem

사용자가 주의를 기울여야 할 하나의 설명 가능한 projection이다. 최소한 다음 의미를 가진다.

- workspace와 season reference
- 안정적인 `reasonCode`
- 제한된 `severity`
- 원본 종류와 opaque source reference
- source가 관측한 시간과 BRIEF가 투영한 시간
- 적용한 rule version
- 활성, 해소 또는 superseded 상태

### BriefEdition

하나의 workspace/season, timezone과 `[windowStart, windowEnd)`에 대해 생성한 불변 snapshot이다.
생성 당시 포함한 관심 항목의 표시 필드를 고정하며 이후 원본 변경으로 기존 edition을
덮어쓰지 않는다.

## 불변식

1. 한 event identity는 하나의 payload fingerprint와만 결합한다.
2. 한 edition identity는 하나의 workspace/season, window와 rule version을 가리킨다.
3. 생성 완료된 edition의 항목과 window는 수정하지 않는다.
4. BRIEF는 source entity 존재를 외래 키나 공유 데이터베이스로 강제하지 않는다.
5. 권한과 멤버십의 최종 판단은 BATON이 수행한다.
6. 도착 순서만으로 최신 source 상태를 결정하지 않는다.

## 첫 사용자 흐름

1. BATON이 domain mutation과 같은 transaction에 outbox record를 저장한다.
2. commit 이후 publisher가 versioned event를 BRIEF에 at-least-once로 전달한다.
3. BRIEF가 receipt를 원자적으로 저장하고 관심 항목 projection을 갱신한다.
4. scheduler 또는 명시적 command가 season timezone 기준 주간 edition을 생성한다.
5. BATON UI가 권한을 확인한 뒤 최신 또는 특정 edition을 조회해 표시한다.

## MVP 수용 기준

- exact replay가 중복 관심 항목이나 edition을 만들지 않는다.
- 같은 event identity의 다른 payload가 정상 event를 덮어쓰지 않는다.
- out-of-order event와 projection rebuild를 테스트로 검증한다.
- edition window 경계와 daylight-saving 전환을 fixed-clock 테스트로 검증한다.
- 모든 표시 항목에 안정적인 이유 코드와 source reference가 있다.
- BRIEF 장애가 BATON의 원본 mutation을 rollback하지 않는다.
- provider, credential, 원문 secret과 불필요한 PII를 저장하거나 로그에 남기지 않는다.

## 미결정 사항

- transport와 exact event envelope
- 첫 source event 목록과 version compatibility 기간
- storage와 application 기술 스택
- retention, poison-event 운영과 rebuild SLO
- BATON 조회 인증 방식
- RELAY와 GO를 연결할 후속 reference contract
