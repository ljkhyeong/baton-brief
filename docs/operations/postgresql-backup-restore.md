# PostgreSQL 백업과 격리 복원

## 범위

BRIEF 데이터베이스 전체를 PostgreSQL 표준 `pg_dump` custom 형식으로 저장하고, 별도의 빈
데이터베이스에 `pg_restore`로 복원한다. 정기 실행에는 Linux의 systemd 타이머를 사용한다.
애플리케이션에는 스케줄러나 제품 API를 추가하지 않는다.

현재 점검 항목을 재구축해도 백업을 대체할 수는 없다. 수신 기록을 잃으면 재구축할 수 없고,
현재 항목만으로 과거 브리프를 재현할 수도 없다. 수신 기록·최초 충돌·현재 점검 항목·브리프·항목·시퀀스와
Flyway 이력을 같은 데이터베이스 백업에 포함한다. 수신·충돌 기록을 삭제하지 않는
`retain-all` 기준은 유지한다.

이 절차는 운영 DB를 덮어쓰는 복구 전환을 허용하지 않는다. 보관 기간·암호화 보관소·
허용 데이터 손실 시간(RPO)·목표 복구 시간(RTO)과 운영 전환은 별도로 정한다.
현재 검증 범위는 [HANDOFF](../../HANDOFF.md)를 따른다.

## 준비

- 백업 대상 Compose 프로젝트·DB 이름·역할을 확인한다. 아래 백업 명령은 저장소 루트에서
  별도 Bash 세션으로 실행한다.
- DB와 같은 PostgreSQL 18 계열 도구를 사용한다. 스테이징의 고정 DB 이미지 안에 있는
  도구를 실행하면 로컬 클라이언트를 설치할 필요가 없다.
- `BRIEF_BACKUP_DIRECTORY`에는 운영자가 승인한 실제 절대 경로를 지정한다. 저장소·DB 볼륨과
  분리하고 다른 사용자의 접근을 제한한다. 예시 경로를 그대로 실행하지 않는다.
- 덤프는 운영 데이터를 포함한 민감한 파일이다. 저장소에 커밋하거나 공개 artifact·로그로
  올리지 않는다. 호스트 장애에 대비하려면 별도로 승인한 암호화 보관소에 복사해야 한다.
- DB 비밀번호를 명령 인수나 환경 변수로 복사하지 않는다. 기존 Compose secrets를 유지하고
  DB 컨테이너 안의 로컬 Unix socket으로 접속한다.

## 백업

```bash
BRIEF_BACKUP_DIRECTORY=/absolute/approved/backup-directory \
  bash ops/backup-postgresql.sh
```

스크립트는 기존 백업 디렉터리 아래에 권한 `0700`인 실행별 폴더를 만들고, 권한 `0600`으로
덤프를 저장한다. 성공한 `pg_dump`만 최종 파일명으로 옮기고 실패한 `.part` 파일은 삭제한다.
기존 백업은 삭제하지 않는다. 다른 환경 파일은 `BRIEF_STAGING_ENV_FILE`, 별도 Compose
프로젝트는 `COMPOSE_PROJECT_NAME`으로 지정한다.

덤프와 함께 백업 시작·완료 시각, DB 서버·도구 버전, 애플리케이션 commit 또는 이미지
digest, 적용한 Flyway 버전을 기록하되 비밀 값은 기록하지 않는다.

`pg_dump`는 실행 중인 DB의 일관된 스냅샷을 만든다. 백업 시작 이후 변경이 모두 포함됐다고
해석하지 않는다. 파일 생성 성공이나 `pg_restore --list`로 목차를 읽는 것만으로 복원 성공을
주장하지 않는다.

## Linux 정기 백업

[service](../../ops/systemd/baton-brief-backup.service)와
[timer](../../ops/systemd/baton-brief-backup.timer)는 매일 UTC 03:00(한국 시간 낮 12시)에 실행한다.
부하 분산을 위해 최대 15분의 무작위 지연과 systemd의 실행 오차가 더해진다.
꺼져 있는 동안 놓친 실행은 다음 기동 때 보충한다.
실제 서버의 저장소 경로·환경 파일·백업 위치에 맞춰 service를 수정한 뒤 설치한다.
기본 예시는 `/srv/baton-brief`와 rootful Docker를 사용하며 rootless 환경에는 맞지 않는다.

```shell
sudo install -d -m 0700 /var/backups/baton-brief
sudo install -m 0644 ops/systemd/baton-brief-backup.{service,timer} /etc/systemd/system/
sudo systemd-analyze verify /etc/systemd/system/baton-brief-backup.{service,timer}
sudo systemctl daemon-reload
sudo systemctl start baton-brief-backup.service
sudo journalctl -u baton-brief-backup.service -n 20 --no-pager
```

첫 백업의 격리 복원을 아래 절차로 확인한 뒤 `sudo systemctl enable --now baton-brief-backup.timer`로
예약을 켠다. 실행 상태는 `systemctl list-timers baton-brief-backup.timer`와 위 journal 명령으로 확인한다.
타이머를 끄려면 `sudo systemctl disable --now baton-brief-backup.timer`를 사용한다.

유료 API나 관리형 DB를 사용하지 않는다. 백업 파일은 서버 디스크를 계속 사용하며 자동 삭제는
보관 정책을 정한 뒤 추가한다. 같은 서버의 백업은 서버·디스크 전체 손실을 복구하지 못하므로,
이미 보유한 별도 저장소가 있으면 복사 위치와 암호화를 정한다. 외부 보관소는 자동 생성하지 않는다.

## 격리된 빈 DB에 복원

복원용 PostgreSQL 컨테이너를 원본과 별도로 준비한다. 원본 볼륨을 연결하지 않고 호스트
포트를 외부에 공개하지 않는다. `brief_restore_dump`에는 백업 완료 메시지에 출력된
`database.dump`의 실제 절대 경로를 넣는다. 나머지 세 값은 복원 대상 컨테이너·역할·새 DB
이름으로 바꾼다. 역할은 미리 준비되어 있어야 한다.

```bash
brief_restore_dump=/absolute/backup-directory/brief-.../database.dump
brief_restore_container=brief-restore-rehearsal
brief_restore_role=brief_restore_owner
brief_restore_db=brief_restore

docker inspect --format '{{.Name}} {{json .Mounts}} {{json .NetworkSettings.Networks}}' \
  "$brief_restore_container"
```

출력을 확인해 원본 컨테이너·볼륨이 아닌 격리 대상임을 확인한 다음 실행한다.

```bash
docker exec --user postgres "$brief_restore_container" \
  createdb --username="$brief_restore_role" --template=template0 "$brief_restore_db"

docker exec -i --user postgres "$brief_restore_container" \
  pg_restore --username="$brief_restore_role" --dbname="$brief_restore_db" \
  --no-owner --no-privileges --single-transaction \
  < "$brief_restore_dump"
```

`createdb`는 같은 이름의 DB가 이미 있으면 실패한다. 실패를 무시하거나 `--clean`으로 기존
DB를 지우지 않는다. `--single-transaction`은 오류 시 종료하고 복원 전체를 롤백한다. 한
명령씩 실행해 실패 여부를 확인하며, 임의로 대상 DB를 비우고 재시도하지 않는다.

소유권과 접근 권한은 복원 대상 역할에 맞게 배포 설정에서 다시 적용한다. 이 덤프는 클러스터
전역 역할·비밀번호, Compose 비밀, TLS 인증서와 애플리케이션 이미지를 보관하지 않는다.
필요한 실행 환경과 비밀 복구는 별도 운영 절차로 준비한다.

## 검증 전용 애플리케이션 실행

서비스 API 인증을 켠 인스턴스에서는 수신 기록·재구축 경로가 차단된다. 복원 확인에는
백업 시점과 맞는 실행 JAR을 사용하는 별도 로컬 프로세스를 띄운다. 운영 프로세스·Compose
설정을 바꾸거나 Caddy·BATON 공유 네트워크에 검증 인스턴스를 연결하지 않는다.

- 접근이 통제된 검증 호스트에서 Java 21을 사용한다. 운영용 Spring 설정·프로필·JVM 옵션을
  주입하지 않은 별도 Bash 세션에서 실행한다.
- 복원 DB 컨테이너의 `5432`를 호스트의 `127.0.0.1`에만 게시한다. `docker port
  "$brief_restore_container" 5432/tcp` 출력의 주소와 포트를 확인하고 아래 값에 반영한다.
  원본 DB의 포트·역할·비밀번호는 재사용하지 않는다.
- `brief_restore_secrets`는 복원 검증 전용 비밀 디렉터리의 실제 절대 경로다. 복원 역할의
  비밀번호를 `spring.datasource.password` 파일로 준비하고 디렉터리는 `0700`, 파일은
  `0600`으로 제한한다. 운영 비밀 파일이나 Bearer를 복사하지 않고 DB 비밀번호 인증을
  유지한다.

아래 값은 실제 검증 대상 값으로 바꾼다. DB 이름과 역할은 앞 단계에서 복원한 대상과
일치해야 한다.

```bash
brief_restore_jar=/absolute/backup-matched/bootstrap.jar
brief_restore_secrets=/absolute/restore-only/secrets
brief_restore_db_port=15432
brief_restore_db=brief_restore
brief_restore_role=brief_restore_owner

java -jar "$brief_restore_jar" \
  --spring.config.location=classpath:/application.yml \
  "--spring.config.import=configtree:$brief_restore_secrets/" \
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$brief_restore_db_port/$brief_restore_db" \
  "--spring.datasource.username=$brief_restore_role" \
  --server.address=127.0.0.1 \
  --server.port=0 \
  --brief.event-receiver.authentication-required=false \
  --brief.service-api.authentication-required=false
```

설정 파일은 JAR 내부 기본값과 지정한 비밀 디렉터리만 읽는다. 인증 비활성은 이 검증
프로세스에만 적용하며 운영 인증은 유지한다. 기동 로그에서 자동 할당된 HTTP 포트를 확인해
같은 호스트의 별도 터미널에서 `http://127.0.0.1:<할당된 포트>`로만 호출한다.

## 복원 확인

앞 단계의 검증 전용 애플리케이션에서 다음 내용을 한 번 확인한다.

1. Flyway 이력과 스키마가 백업 시점과 일치하고 애플리케이션·DB의 통합 상태가 정상이다.
2. 대표 수신 기록·최초 충돌·기존 브리프와 브리프에 저장된 항목이 백업 전과 같다. `UNSUPPORTED`도
   보존됐는지 확인한다.
3. 백업에 있던 지원 이벤트의 동일 재전달은 `DUPLICATE`이며 새 수신 기록을 만들지 않는다.
4. 격리된 검증 인스턴스에서 재구축한 현재 투영이 같고 기존 브리프와 `ETag`는 변하지 않는다.
5. 새 이벤트의 `ingestionSequence`와 새 브리프의 `generation`이 이전 값 뒤로 이어진다.

재구축과 동일 이벤트 재전달은 검증용 복원 DB에서만 수행한다. 운영 인증을 끄거나
수신 기록·재구축 경로를 Caddy 허용 목록에 추가하지 않는다.

실제 복원 소요 시간과 결과를 기록한다. 한 번의 로컬 복원 시간을 운영 RTO나 대용량 처리
보장으로 사용하지 않는다. 검증이 끝나면 정확히 식별한 임시 인스턴스와 데이터만 제거한다.
원본·보관 백업은 이 정리 대상이 아니다.

## 운영 복구 전 추가 결정

- 승인된 보관 위치·암호화·접근 권한과 백업 주기·보관 정책
- 복원 지점 이후 BATON outbox 이벤트의 재전달 범위와 전달 상태 복구 절차
- 장애 뒤 기존에 발급한 브리프 ID와 사용자 참조의 유실 처리
- 새 DB로 연결을 전환할 권한, 원본 쓰기 중단·재개와 실패 시 복귀 절차
- 실제 데이터 규모에서 확인한 RPO·RTO

백업 이후 생성된 기록까지 현재 백업으로 복구할 수 있다고 가정하지 않는다. BRIEF 복원만으로
BATON이 이미 전달 완료로 기록한 이벤트가 자동 재전송되는 것도 아니다.

## 참고

- [PostgreSQL SQL dump](https://www.postgresql.org/docs/18/backup-dump.html)
- [pg_dump](https://www.postgresql.org/docs/18/app-pgdump.html)
- [pg_restore](https://www.postgresql.org/docs/18/app-pgrestore.html)
- [수신 기록 보존·재구축 계약](../PRD/0008_retention-rebuild-boundary/spec.md)
