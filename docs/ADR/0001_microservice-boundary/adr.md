# ADR-0001: BATON BRIEF 마이크로서비스 경계

- 상태: Accepted
- 결정일: 2026-08-11

## 맥락

BATON은 팀·시즌·역할·루틴·결정·인수인계와 최종 권한을 소유한다. WATCH는 자료 URL의
health, RELAY는 provider 전달, GO는 공개 링크 생명주기를 소유한다. 여러 운영 사실을 한
기간의 읽을 수 있는 결과로 고정하는 책임은 어느 서비스에도 속하지 않는다.

## 결정

BATON BRIEF를 source-domain 서비스가 아니라 독립적인 읽기 모델과 불변 다이제스트
생성기로 둔다.

BRIEF는 versioned event를 멱등하게 수신하고 `AttentionItem`을 투영하며, workspace/season과
시간 window별 `BriefEdition`을 생성한다. BRIEF의 결과는 설명 가능한 이유 코드와 원본
reference를 보존한다.

BRIEF는 원본 도메인 판정, 권한, URL health, provider delivery와 링크 생명주기를 소유하지
않는다. 특히 WATCH 저장소를 직접 읽거나 RELAY의 provider adapter를 포함하지 않는다.

## 결과

장점:

- source mutation과 digest 생성 실패를 분리할 수 있다.
- projection을 독립적으로 replay, rebuild, scale할 수 있다.
- 생성 당시의 운영 상황을 immutable edition으로 보존할 수 있다.
- RELAY, WATCH와 GO의 기존 경계를 유지한다.

비용:

- event versioning, at-least-once delivery와 replay 운영이 필요하다.
- eventual consistency를 사용자 경험과 운영 지표에 드러내야 한다.
- source 권한을 복제하지 않으면서 안전한 조회 계약을 추가해야 한다.

## 보류한 대안

- BATON 내부 기능: 제품 가치를 가장 빨리 검증할 수 있지만 독립적인 generation/rebuild
  수명주기가 커질 경우 본체 운영 부담이 된다.
- RELAY template 기능: 단순 알림 묶음에는 적합하지만 불변 edition과 교차 이벤트
  projection까지 소유하면 전달 서비스 경계를 넘어선다.
- 자유 형식 AI 요약 서비스: 설명 가능성, 재현성과 원본 판정 경계를 첫 MVP에서 약화한다.
