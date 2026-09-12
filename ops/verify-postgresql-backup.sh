#!/usr/bin/env bash
set -euo pipefail
umask 077

brief_repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${BRIEF_BACKUP_FILE:?확인할 백업 파일의 절대 경로를 지정하세요.}"
case "$BRIEF_BACKUP_FILE" in
  /*) ;;
  *) printf '%s\n' 'BRIEF_BACKUP_FILE에는 절대 경로가 필요합니다.' >&2; exit 1 ;;
esac
if [[ ! -f "$BRIEF_BACKUP_FILE" || ! -r "$BRIEF_BACKUP_FILE" ]]; then
  printf '%s\n' '백업 파일이 없거나 읽을 수 없습니다.' >&2
  exit 1
fi

brief_postgres_image=$(docker compose --env-file "$brief_repository/.env.staging.example" \
  -f "$brief_repository/compose.staging.yml" config --images postgres)

docker run --rm --network none --user postgres --interactive --entrypoint /bin/sh \
  "$brief_postgres_image" -eu -c '
  brief_restore_data=$(mktemp -d)
  initdb -D "$brief_restore_data" --auth=trust </dev/null >/dev/null
  pg_ctl -D "$brief_restore_data" -o "-c listen_addresses= -c unix_socket_directories=/tmp" \
    -l "$brief_restore_data/server.log" -s -w start </dev/null || {
      cat "$brief_restore_data/server.log" >&2
      exit 1
    }
  createdb --host=/tmp --username=postgres --template=template0 brief_restore_check </dev/null
  pg_restore --host=/tmp --username=postgres --dbname=brief_restore_check \
    --no-owner --no-privileges --single-transaction
  ' < "$BRIEF_BACKUP_FILE"

printf '%s\n' '백업 복원 확인 완료: 격리된 임시 DB에 복원했으며 임시 DB는 삭제했습니다.'
