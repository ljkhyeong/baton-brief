# brief.b4ton.com 배포 준비

## 주소와 역할

기본 도메인은 `b4ton.com`이며 BRIEF 공개 이벤트 수신 주소는 `https://brief.b4ton.com`을
사용한다. 사용자는 기존 BATON 화면에서 브리프를 읽는다.

| 용도 | 설정 | 값 |
| --- | --- | --- |
| BRIEF 공개 Caddy 호스트 | `BRIEF_STAGING_HOST` | `brief.b4ton.com` |
| BATON 이벤트 전달 origin | `BATON_BRIEF_BASE_URL` | `https://brief.b4ton.com` |
| BRIEF 내부 서비스 호스트 | `BRIEF_SERVICE_HOST` | `brief-service` |
| BATON 내부 서비스 대상 | `BATON_BRIEF_SERVICE_HOST` | `brief-service` |
| 내부 공유 네트워크 | `BATON_BRIEF_PRIVATE_NETWORK` | `baton-brief-private` |

BATON 이벤트 origin에는 `/api/v1/events`를 붙이지 않는다. 기존 송신기가 해당 경로를
추가한다. 공개 호스트에는 정확한 `POST /api/v1/events`만 허용하며 조회·생성·재구축과
health·지표는 공개하지 않는다. 브라우저에서 공개 주소의 `/`를 열었을 때 `404`인 것은
현재 계약에 맞는 동작이다.

내부 조회·생성은 기존 서비스 Caddy의 `8443` HTTPS, 별도 Bearer와 인증서 검증을 사용한다.
`brief-service`는 내부 Docker 이름이며 공인 DNS에 등록할 대상이 아니다. 공개 이벤트
인증서와 내부 서비스 인증서·BATON truststore를 구분한다.

## 서버와 DNS 준비

서버를 정한 뒤 DNS 관리 화면에 다음 레코드를 등록한다. 아래 대상 주소는 실제 서버에서
확정해야 하며 문서의 예시 IP로 대체하지 않는다.

| 이름 | 종류 | 대상 |
| --- | --- | --- |
| `brief` | `A` | 공개 이벤트 앞단 서버의 실제 IPv4 주소 |
| `brief` | `AAAA` | IPv6 연결·방화벽까지 준비한 경우에만 실제 IPv6 주소 |

단일 호스트에 BATON과 BRIEF를 함께 배포하면 내부 서비스 네트워크를 공유할 수 있다.
이때 두 저장소의 기본 공개 Caddy를 그대로 함께 실행하면 80·443 포트가 충돌한다.
공개 앞단 하나가 BATON과 `brief.b4ton.com`을 호스트별로 처리하도록 Compose·Caddy를
통합하고, BRIEF 이벤트 허용 경로·헤더 제거·네트워크 격리를 같은 조립에서 검증해야 한다.

여러 서버에 나누면 기존 Docker 내부 네트워크만으로 조회·생성을 연결할 수 없다. 비공개
라우팅과 서비스 인증서 배포 방법을 먼저 정한다. 호스트 포트를 임의로 열거나 공인
이벤트 주소에 내부 조회 경로를 추가해 대신하지 않는다.

공개 앞단을 정한 뒤 DNS가 그 서버를 가리키게 하고 80·443 접근 경로를 준비한다. 기존
Caddy 자동 HTTPS가 공인 인증서 발급·갱신을 담당한다. DNS 등록만으로 앱 배포나 인증서
발급이 완료된 것은 아니다.

## 구축 순서

1. 서버 위치·접속 방법·실제 IP와 공개 앞단의 소유자를 정한다. 같은 서버 배치는 공개
   Caddy 통합을, 여러 서버 배치는 비공개 서비스 라우팅을 먼저 검증한다.
2. BRIEF의 `.env.staging.example`을 실제 배포용 파일로 복사하고 저장소 밖 비밀 파일
   경로를 설정한다. DB·이벤트 Bearer·서비스 Bearer와 내부 서비스 인증서·truststore를
   준비하며 파일 권한은 [README의 스테이징 실행](../../README.md#스테이징-실행)을 따른다.
3. BATON의 소비자 변경과 BRIEF를 내부에서 기동하고 DB aggregate health를 확인한다.
   BATON 이벤트 전달과 사용자 조회·생성 기능은 연결 검증이 준비된 뒤 활성화한다.
4. `brief.b4ton.com` DNS를 연결하고 공인 인증서를 정상 검증한다. 무인증 이벤트 요청의
   `401`과 이벤트 외 경로의 `404`를 확인한다.
5. BATON 실제 원본 변경을 전달해 최초 `202`, 동일 이벤트 재전달 `200`, 심각도 변경과
   해소 수렴을 확인한다. 합성 이벤트를 실제 업무 작업공간에 임의로 넣지 않는다.
6. 내부 서비스 인증서·별도 Bearer를 연결해 로그인한 BATON 사용자 화면에서 조회·생성과
   권한 거부를 확인한다. 전달 실패 뒤 재시도와 token 교체 시나리오도 확인한다.
7. [운영 지표 절차](diagnostics-and-metrics.md)에 따라 기존 서버의 Prometheus를 켜고 BATON
   전달 진단을 연결한다. 무료 수신 채널을 정한 뒤 수집 실패와 전달 장애가 실제 담당자에게
   알려지는지 확인한다. [백업 절차](postgresql-backup-restore.md)의 격리 복원을 확인한 뒤 정기 실행을 켠다.

현재 구축·검증 상태와 남은 입력은 [HANDOFF.md](../../HANDOFF.md)를 기준으로 확인한다.

## 관련 계약

- [HTTPS 이벤트 수신](../PRD/0022_https-event-ingress/spec.md)
- [서비스 API 인증과 비공개 연결](../PRD/0025_baton-service-api-security/spec.md)
- [호스트 권한 운영](../ADR/0008_host-authorized-operations/adr.md)
