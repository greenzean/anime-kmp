#!/bin/sh
# Starts the local Postgres (Docker) and runs the server from Gradle against it.
# Usage: scripts/dev-server.sh [--reset]
#   --reset  deletes the database volume first (needed when a migration changed before the first deploy)
# Reads .env if present (GOOGLE_CLIENT_ID, JWT_SECRET, ...). Without those two, sign-in is off and sync is rejected.
set -eu
cd "$(git rev-parse --show-toplevel)"

if [ "${1:-}" = "--reset" ]; then
  docker compose down -v
fi

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

docker compose up -d --wait postgres

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/${POSTGRES_DB:-animekmp}}"
export DATABASE_USER="${DATABASE_USER:-${POSTGRES_USER:-animekmp}}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-${POSTGRES_PASSWORD:-animekmp}}"

if [ -z "${GOOGLE_CLIENT_ID:-}" ] || [ -z "${JWT_SECRET:-}" ]; then
  echo "Note: GOOGLE_CLIENT_ID / JWT_SECRET not set (see .env.example): sign-in is off and sync calls are rejected." >&2
fi

exec ./gradlew :server:run
