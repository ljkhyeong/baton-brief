# PRD-0032: 범위 지정 브리프 비교

- 상태: 채택됨
- 결정일: 2026-10-05
- 범위: 작업공간·시즌 경로 안에서 PRD-0005 비교를 한 번에 조회

## 목적

[PRD-0005](../0005_edition-comparison/spec.md) 비교 경로는 작업공간·시즌을 받지 않는다. BATON은 사용자가 고른
두 브리프가 같은 팀·시즌인지 확인하려고 두 브리프 본문을 먼저 조회한 뒤 비교를 호출한다. 다른 조회처럼
범위를 경로에 두어 범위 확인과 비교를 한 번의 요청으로 끝낸다.

## 조회 계약

`GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/{targetEditionId}/changes?fromEditionId={baseEditionId}`

- 성공 응답 본문, ETag와 `If-None-Match`의 `304`는 PRD-0005·[PRD-0012](../0012_edition-etag/spec.md)와 같다.
- 두 브리프 중 하나라도 없거나 경로 범위에 속하지 않으면 `404 Not Found`와 PRD-0004의 `ProblemDetail`을
  반환한다. 다른 범위의 브리프 존재를 드러내지 않으므로 PRD-0005의 범위 불일치 `400`은 이 경로에서 나오지 않는다.
- 식별자 형식 오류와 `fromEditionId` 누락은 `400 Bad Request`다.
- 기준 브리프를 찾지 못하면 대상 브리프를 조회하지 않는다.

## 호환성

- PRD-0005 경로와 응답은 그대로 유지한다. BATON은 이 경로로 옮기면 두 브리프의 사전 조회를 생략할 수 있다.
- 사용자 권한은 BATON이 판정하고, 서비스 API 인증과 허용 경로는
  [PRD-0025](../0025_baton-service-api-security/spec.md)를 따른다.

## 수용 기준

- 같은 범위의 두 브리프는 PRD-0005 경로와 같은 본문과 ETag를 반환한다.
- 경로 범위 밖의 브리프가 하나라도 있으면 `404`다.
