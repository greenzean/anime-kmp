#!/usr/bin/env bash
set -euo pipefail

backup_dir="${BACKUP_DIR:-$HOME/animekmp-backups}"
keep_days="${BACKUP_KEEP_DAYS:-14}"
compose=(docker compose -p anime-kmp-prod -f compose.yaml -f compose.prod.yaml)

file="$backup_dir/animekmp-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
trap 'rm -f "$file.partial"' EXIT
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"

"${compose[@]}" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$file.partial"
mv "$file.partial" "$file"

find "$backup_dir" -name 'animekmp-*.sql.gz' -mtime "+$keep_days" -delete
echo "$file"
