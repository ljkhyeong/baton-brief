# PRD-0027: 활성 점검 항목 요약

- 상태: 채택됨
- 결정일: 2026-08-31
- 범위: 작업공간·시즌별 활성 항목 집계(심각도별 개수, 리비전 공백이 기록된 항목 수)

## 목적

BRIEF가 활성 항목 수를 집계해 제공한다. BATON은 목록 전체를 조회하지 않고 요약을 표시하며
사용자 권한 확인과 화면 구성을 담당한다.

## HTTP API

```text
GET /api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/summary
```

`workspaceId`와 `seasonId`는 UUID이며 질의 매개변수는 정의하지 않는다. 성공 시 `200 OK`와
다음 JSON 객체를 반환한다.

```json
{
  "highCount": 1,
  "mediumCount": 2,
  "revisionGapCount": 1
}
```

- 세 필드는 모두 음수가 아닌 64비트 정수이며 `null`이 아니다.
- `highCount`: 현재 `ACTIVE`이면서 표시 심각도가 `HIGH`인 항목 수
- `mediumCount`: 현재 `ACTIVE`이면서 표시 심각도가 `MEDIUM`인 항목 수
- `revisionGapCount`: 현재 `ACTIVE`이면서 누적 `revisionGap=true`인 항목 수

공백이 기록된 항목은 심각도별 개수에도 포함된다. 현재 활성 항목 수는
`highCount + mediumCount`이며, 여기에 `revisionGapCount`를 더하지 않는다.

해당 항목의 목록은 [PRD-0028](../0028_attention-item-filters/spec.md)의 기존 목록 필터로
조회한다. 요약에는 필터 매개변수를 추가하지 않는다.

활성 항목이 없는 범위는 세 값 모두 `0`으로 반환한다. BRIEF는 작업공간·시즌의 원본 존재나
사용자 권한을 판정하지 않으므로 빈 요약을 `404`로 바꾸지 않는다. 잘못된 UUID는 기존
Spring MVC 변환과 PRD-0004의 `400 Bad Request`·`ProblemDetail`을 따른다.

## 집계와 일관성

- 작업공간과 시즌이 모두 일치하는 현재 `attention_item`의 `ACTIVE` 행만 집계한다.
- `RESOLVED`, 수신 기록 행과 브리프 항목은 집계하지 않는다.
- 심각도가 바뀌거나 항목이 해소되면 다음 조회는 변경된 현재 상태를 반영한다.
- 중복·충돌·오래된 이벤트·미지원 이벤트의 수신 횟수를 항목 수에 더하지 않는다.
- 세 개수는 PostgreSQL의 단일 집계 쿼리에서 함께 읽는다. 목록과 요약은 별도 요청으로
  조회하므로 두 요청 사이에 상태가 바뀌면 개수가 달라질 수 있다.
- 원자적 전체 재구축 중에는 커밋된 이전 또는 이후 투영을 읽으며 중간 삭제 상태를
  공개하지 않는다. 재생 결과가 같으면 재구축 전후의 개수도 같다.
- `revisionGapCount`는 공백이 기록된 활성 항목 수다. 이 값으로 누락 이벤트 수나
  원본 이벤트의 전달 완료 여부를 판단하지 않는다. 해소된 항목은 과거 공백이 남아 있어도 제외한다.

## 인증과 호환성

- PRD-0023의 BATON 사용자 권한 판정 뒤 PRD-0025의 서비스 Bearer와 비공개 HTTPS로 조회한다.
- Spring Security와 서비스 Caddy의 정확한 `GET` 허용 경로에만 추가한다. 공개 Caddy의
  이벤트 수신 한 경로 계약은 바꾸지 않는다.
- 기존 목록의 응답·커서·정렬과 단건 `ETag`는 바꾸지 않는다. 요약에는 `ETag`를 추가하지 않는다.
- 기존 투영과 Spring JDBC 집계를 사용하며 새 테이블·열·인덱스·캐시를 만들지 않는다.
- 이벤트 계약 팩, 투영·브리프 `ruleVersion`과 기존 브리프는 바꾸지 않는다.

## 수용 기준

- 활성 `HIGH`·`MEDIUM`과 공백 항목이 섞인 범위에서 세 개수가 정확하다.
- 다른 작업공간이나 시즌의 항목이 섞이지 않으며 빈 범위는 세 값 모두 `0`이다.
- 심각도 변경과 해소를 반영하고 재구축으로 같은 요약을 재현한다.
- 이벤트 수신 결과가 항목 수로 중복 집계되지 않는다.
- 서비스 인증이 활성화된 환경에서 무인증·이벤트 Bearer는 거부하고 서비스 Bearer는 허용한다.
- 신뢰한 서비스 HTTPS에서 조회되며 공개 HTTPS에서는 같은 경로가 `404`다.

## 비목표

- BATON 저장소의 사용자 API·client·화면 구현과 교차 서비스 검증
- `RESOLVED` 요약, 이벤트 종류·기간·주간 필터와 브리프 통계
- 페이지 응답의 전체 개수, 자유 집계·검색과 새 정렬
- 수신 요청 지표·원본 이벤트의 전달 완료 판정·운영자 수신 기록 조회
- 집계 전용 저장소·scheduler·캐시·변경 알림과 외부 지표 수집

## 관련 문서

- [활성 점검 항목 목록](../0014_active-attention-items/spec.md)
- [현재 상태 필터](../0015_attention-item-status-filter/spec.md)
- [BATON 백엔드 경유 조회](../0023_baton-mediated-brief-query/spec.md)
- [BATON 서비스 API 인증](../0025_baton-service-api-security/spec.md)
