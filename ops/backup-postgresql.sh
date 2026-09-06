#!/usr/bin/env bash
set -euo pipefail
umask 077

brief_repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${BRIEF_BACKUP_DIRECTORY:?백업을 보관할 기존 절대 경로를 지정하세요.}"
case "$BRIEF_BACKUP_DIRECTORY" in
  /*) ;;
  *) printf '%s\n' 'BRIEF_BACKUP_DIRECTORY에는 절대 경로가 필요합니다.' >&2; exit 1 ;;
esac
brief_backup_dir=$(mktemp -d "$BRIEF_BACKUP_DIRECTORY/brief-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
trap 'rm -f -- "$brief_backup_dir/database.dump.part"' EXIT

docker compose --env-file "${BRIEF_STAGING_ENV_FILE:-$brief_repository/.env.staging}" \
  -f "$brief_repository/compose.staging.yml" \
  exec -T --user postgres postgres sh -eu -c \
  'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' \
  > "$brief_backup_dir/database.dump.part"

mv -- "$brief_backup_dir/database.dump.part" "$brief_backup_dir/database.dump"
printf '백업 완료: %s\n' "$brief_backup_dir/database.dump"
