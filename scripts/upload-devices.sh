#!/usr/bin/env bash
set -euo pipefail

# Batch upload devices to Knox Guard Upload API via TokenClient.
# Requires: Java build jar present (target/*-jar-with-dependencies.jar) or compiled classes runnable via 'Run: TokenClient'.
# Environment (optional): KNOX_REGION or KNOX_API_BASE_URL, KNOX_GUARD_FUNCTION_BASE_URL
# Arguments:
#   -f|--file payload.json (contains deviceList etc.)
#   --client-id override client id (else env KNOX_GUARD_CLIENT_ID)
#   --validity minutes (optional, default 30)
#   --quiet reduce output
#   --json machine JSON only output

usage() {
  echo "Usage: $0 --file devices.json [--client-id <id>] [--validity 30] [--quiet] [--json]" >&2
}

PAYLOAD_FILE=""
CLIENT_ID="${KNOX_GUARD_CLIENT_ID:-}"
VALIDITY="30"
QUIET="false"
JSON="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -f|--file) PAYLOAD_FILE="$2"; shift 2;;
    --client-id) CLIENT_ID="$2"; shift 2;;
    --validity) VALIDITY="$2"; shift 2;;
    --quiet) QUIET="true"; shift;;
    --json) JSON="true"; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Unknown arg: $1" >&2; usage; exit 1;;
  esac
done

[[ -z "$PAYLOAD_FILE" ]] && { echo "--file required" >&2; usage; exit 1; }
[[ ! -f "$PAYLOAD_FILE" ]] && { echo "Payload file not found: $PAYLOAD_FILE" >&2; exit 1; }

JAR=$(ls target/*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true)
if [[ -z "$JAR" ]]; then
  echo "Fat jar not found. Building..." >&2
  mvn -q -DskipTests package
  JAR=$(ls target/*-jar-with-dependencies.jar 2>/dev/null | head -n1 || true)
fi
[[ -z "$JAR" ]] && { echo "Unable to locate or build jar" >&2; exit 2; }

ARGS=(--mode upload-devices --upload-file "$PAYLOAD_FILE" --validity "$VALIDITY")
[[ -n "$CLIENT_ID" ]] && ARGS+=(--client-id "$CLIENT_ID")
[[ "$QUIET" == "true" ]] && ARGS+=(--quiet)
[[ "$JSON" == "true" ]] && ARGS+=(--output-json)

exec java -jar "$JAR" "${ARGS[@]}"
