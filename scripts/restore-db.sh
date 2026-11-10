#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: restore-db.sh <backup.sql.gz> [scratch-database]" >&2
  exit 2
fi

file="$1"
scratch="${2:-animekmp_restore_check}"
compose=(docker compose -p anime-kmp-prod -f compose.yaml -f compose.prod.yaml)
live_db="$("${compose[@]}" exec -T postgres sh -c 'printf %s "$POSTGRES_DB"')"
if [ "$scratch" = "$live_db" ]; then
  echo "scratch database must not be the live database ($live_db)" >&2
  exit 2
fi
counts="select 'anime', count(*) from anime union all select 'app_user', count(*) from app_user union all select 'list_entry', count(*) from list_entry"

"${compose[@]}" exec -T postgres sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists \"$scratch\" && createdb -U \"\$POSTGRES_USER\" \"$scratch\""
gunzip -c "$file" | "${compose[@]}" exec -T postgres sh -c "psql -v ON_ERROR_STOP=1 -q -U \"\$POSTGRES_USER\" -d \"$scratch\"" > /dev/null

echo "== live database"
"${compose[@]}" exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -At -c \"$counts\""
echo "== $scratch"
"${compose[@]}" exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"$scratch\" -At -c \"$counts\""
"${compose[@]}" exec -T postgres sh -c "dropdb -U \"\$POSTGRES_USER\" \"$scratch\""
echo "scratch database dropped"
