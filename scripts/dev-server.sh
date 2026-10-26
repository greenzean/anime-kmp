#!/bin/sh
set -eu
if ! command -v server-deploy >/dev/null 2>&1; then
  echo "server-deploy is not installed (script outside the repo, see ADR 0014)" >&2
  exit 1
fi
exec server-deploy "$@"
