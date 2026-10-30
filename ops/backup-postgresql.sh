#!/usr/bin/env bash
set -euo pipefail
umask 077

brief_repository=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${BRIEF_BACKUP_DIRECTORY:?존재하는 백업 디렉터리의 절대 경로를 지정하세요.}"
case "$BRIEF_BACKUP_DIRECTORY" in
  /*) ;;
  *) printf '%s\n' 'BRIEF_BACKUP_DIRECTORY에는 절대 경로가 필요합니다.' >&2; exit 1 ;;
esac
brief_backup_dir=$(mktemp -d "$BRIEF_BACKUP_DIRECTORY/brief-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
trap 'rm -f -- "$brief_backup_dir/database.dump.part"; rmdir -- "$brief_backup_dir" 2>/dev/null || true' EXIT

brief_compose() {
  docker compose --env-file "${BRIEF_STAGING_ENV_FILE:-$brief_repository/.env.staging}" \
    -f "$brief_repository/compose.staging.yml" "$@"
}

brief_started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
brief_compose exec -T --user postgres postgres sh -eu -c \
  'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' \
  > "$brief_backup_dir/database.dump.part"
mv -- "$brief_backup_dir/database.dump.part" "$brief_backup_dir/database.dump"

# 복원 때 백업 시점과 맞는 실행 이미지를 찾을 수 있게 남긴다. 비밀 값은 기록하지 않는다.
{
  printf 'started_at=%s\nfinished_at=%s\n' "$brief_started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  brief_compose exec -T --user postgres postgres sh -eu -c '
    printf "pg_dump_version=%s\n" "$(pg_dump --version)"
    printf "flyway_version=%s\n" "$(psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
      --tuples-only --no-align \
      --command="SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
  '
  brief_compose images
} > "$brief_backup_dir/manifest.txt"
printf '백업 완료: %s\n' "$brief_backup_dir/database.dump"
