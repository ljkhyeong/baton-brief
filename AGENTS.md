# BATON BRIEF Agent Guide

## 시작 순서

- 작업 전에 `HANDOFF.md`, `README.md`와 영향받는 PRD/ADR을 읽는다.
- 문서와 코드가 다르면 실행 가능한 코드와 테스트를 확인하고 같은 변경에서 문서를
  바로잡는다.
- 구현되지 않은 기능, route, event와 배포 방식을 완료된 것처럼 기록하지 않는다.

## 서비스 경계

- BATON은 조직 운영 원본과 최종 권한, continuity signal의 권위 있는 판정을 소유한다.
- WATCH는 URL 점검과 health 원본을 소유한다. WATCH 데이터베이스를 직접 읽지 않는다.
- RELAY는 구독과 provider 전달 생명주기를 소유한다. BRIEF에서 메일이나 메시지를 직접
  보내지 않는다.
- GO는 링크 코드의 만료·폐기·redirect를 소유한다. BRIEF에서 링크 생명주기를 복제하지
  않는다.
- BRIEF는 멱등 inbox, 관심 항목 projection, 생성 cursor와 immutable edition만 소유한다.

## 구현 원칙

- source 서비스의 transaction과 외부 호출을 결합하지 않는다. after-commit event와
  at-least-once delivery를 전제로 한다.
- event identity와 version을 검증하고 exact replay는 멱등하게 처리한다. 같은 identity의
  다른 payload는 충돌로 다룬다.
- 도착 순서를 source truth로 해석하지 않는다. 계약에 있는 aggregate revision과
  watermark를 사용한다.
- edition은 immutable이다. 기존 edition을 덮어쓰지 않는다.
- 시간 의존 코드는 시스템 현재 시간을 직접 읽지 않고 `Clock`을 주입한다.
- 첫 MVP의 선정 규칙은 결정적이고 설명 가능해야 한다. AI 생성 결과를 권위 있는 판정으로
  사용하지 않는다.
- 다른 서비스의 entity, migration, credential과 데이터베이스를 공유하지 않는다.
- PII, secret, 원문 URL과 unbounded identifier를 로그나 metric label에 넣지 않는다.

## 문서와 검증

- 제품 동작은 `docs/PRD/`, 장기 구조 결정은 `docs/ADR/`에 기록한다.
- 계약 변경에는 producer/consumer compatibility와 replay 검증을 포함한다.
- projection 변경에는 duplicate, out-of-order, rebuild와 fixed-clock 테스트를 포함한다.
- 사용 가능한 실행 명령이 생기기 전에는 존재하지 않는 build나 test 명령을 문서화하지
  않는다.

## Git

- 커밋 제목은 `종류: 한글 요약` 형식을 사용한다.
- 종류는 `기능`, `수정`, `문서`, `테스트`, `설정`, `리팩터` 중에서 고른다.
- 기능 작업 브랜치는 기본적으로 `codex/` prefix를 사용한다.
- 관련 검증이 성공한 변경만 커밋하고, 실행하지 못한 검증은 최종 보고에 명시한다.
