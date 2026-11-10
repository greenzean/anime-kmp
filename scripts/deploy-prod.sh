#!/usr/bin/env bash
set -euo pipefail

host="${PROD_HOST:-minipc}"
dir="${PROD_DIR:-anime-kmp-prod}"

if [ -n "$(git status --porcelain)" ] && [ "${ALLOW_DIRTY:-0}" != "1" ]; then
  echo "working tree has uncommitted changes: commit them, or run with ALLOW_DIRTY=1" >&2
  exit 1
fi

env_values="$(ssh -o BatchMode=yes "$host" "cat $dir/.env" 2>/dev/null)" || {
  echo "missing $dir/.env on $host: see README, Deploy to production" >&2
  exit 1
}
for required in SERVER_BIND=127.0.0.1 SERVER_PORT=; do
  if ! printf '%s\n' "$env_values" | grep -q "^$required"; then
    echo "$dir/.env must set $required" >&2
    exit 1
  fi
done

ssh -o BatchMode=yes "$host" "cd $dir && rm -rf server gradle config"
git ls-files -z --cached --others --exclude-standard | while IFS= read -r -d '' f; do
  [ -e "$f" ] && printf '%s\0' "$f"
done | rsync -a --from0 --files-from=- ./ "$host:$dir/"
if ! ssh -o BatchMode=yes "$host" "cd $dir && docker compose -p anime-kmp-prod -f compose.yaml -f compose.prod.yaml up -d --build --wait"; then
  ssh -o BatchMode=yes "$host" "cd $dir && docker compose -p anime-kmp-prod logs --tail 60 server" >&2 || true
  exit 1
fi
ssh -o BatchMode=yes "$host" "cd $dir && docker compose -p anime-kmp-prod exec -T server wget -qO- http://localhost:8080/health" > /dev/null
echo "deploy-prod: healthy"
