#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true)
if [ -z "${JAR}" ]; then
  echo "[INFO] Building project (fat jar not found)" >&2
  mvn -q -ntp package -Dgpg.skip=true
  JAR=$(ls -1 target/pts-*-jar-with-dependencies.jar | head -n1)
fi
PORT=${PORT:-8080}
exec java -jar "${JAR}" server "$PORT"
