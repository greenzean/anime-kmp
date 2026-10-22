#!/bin/sh
# Installs the Android debug build on the running emulator or device, pointed at the local server.
# Usage: scripts/dev-app.sh [graphql-url]   (default: http://10.0.2.2:8080/graphql, the emulator's host machine)
# Plain HTTP also needs the debug cleartext files described in the README ("Android debug build").
set -eu
cd "$(git rev-parse --show-toplevel)"

exec ./gradlew :androidApp:installDebug "-Panimekmp.serverUrl=${1:-http://10.0.2.2:8080/graphql}"
