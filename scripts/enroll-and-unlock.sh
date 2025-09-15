#!/usr/bin/env bash
set -euo pipefail

# Enroll a single device IMEI then attempt immediate unlock.
# Required env:
#   ACCESS_TOKEN  : Access token (Bearer) for Knox Guard APIs (enroll + unlock)
#   CLIENT_ID     : Knox Guard client identifier for enrollment
#   DEVICE_IMEI   : 15 digit IMEI to process
# Optional:
#   KNOX_REGION (us|eu|ap) or KNOX_API_BASE_URL (explicit base like https://us-api.samsungknox.com/kcs/v1)
#   KNOX_GUARD_UNLOCK_PATH (default /kguard/devices/unlock)
#   DRY_RUN=1 (print actions only, no HTTP)
#   SLEEP_AFTER_ENROLL_MS=1000 (wait before unlock attempt)
#   VERBOSE=1 for extra logging
#
# Exit codes:
#   0 success (or dry run)  1 usage/config error  2 HTTP failure(s)

err() { echo "ERROR: $*" >&2; }
log() { [[ "${VERBOSE:-}" == "1" ]] && echo "[INFO] $*" >&2 || true; }

[[ -z "${ACCESS_TOKEN:-}" ]] && { err "ACCESS_TOKEN required"; exit 1; }
[[ -z "${CLIENT_ID:-}" ]] && { err "CLIENT_ID required"; exit 1; }
[[ -z "${DEVICE_IMEI:-}" ]] && { err "DEVICE_IMEI required"; exit 1; }
if [[ ! "$DEVICE_IMEI" =~ ^[0-9]{15}$ ]]; then err "DEVICE_IMEI must be 15 digits"; exit 1; fi

REGION=${KNOX_REGION:-us}
if [[ -n "${KNOX_API_BASE_URL:-}" ]]; then
  API_BASE="${KNOX_API_BASE_URL%/}"
else
  case "$REGION" in
    us|US) API_BASE="https://us-api.samsungknox.com/kcs/v1" ;;
    eu|EU) API_BASE="https://eu-api.samsungknox.com/kcs/v1" ;;
    ap|AP) API_BASE="https://ap-api.samsungknox.com/kcs/v1" ;;
    *) API_BASE="https://${REGION}-api.samsungknox.com/kcs/v1" ;;
  esac
fi
UNLOCK_PATH=${KNOX_GUARD_UNLOCK_PATH:-/kguard/devices/unlock}

ENROLL_BODY="{\"deviceId\":\"$DEVICE_IMEI\",\"clientId\":\"$CLIENT_ID\",\"platform\":\"android\"}"
UNLOCK_BODY="{\"deviceId\":\"$DEVICE_IMEI\",\"action\":\"unlock\"}"

if [[ "${DRY_RUN:-}" == "1" ]]; then
  echo "[DRY_RUN] Would POST enroll $API_BASE/kguard/devices $ENROLL_BODY" >&2
  echo "[DRY_RUN] Would POST unlock $API_BASE$UNLOCK_PATH $UNLOCK_BODY" >&2
  exit 0
fi

# Enroll
ENROLL_RESP=$(mktemp)
ENROLL_CODE=$(curl -sS -w '%{http_code}' -o "$ENROLL_RESP" -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-KNOX-API-VERSION: v1" \
  -H 'Content-Type: application/json' \
  "$API_BASE/kguard/devices" \
  -d "$ENROLL_BODY" || echo '000')
ENROLL_DATA=$(cat "$ENROLL_RESP"); rm -f "$ENROLL_RESP"
if [[ ! "$ENROLL_CODE" =~ ^2 ]]; then
  err "Enrollment failed code=$ENROLL_CODE body=$ENROLL_DATA"
  exit 2
fi
log "Enrollment OK code=$ENROLL_CODE"

SLEEP_AFTER_ENROLL_MS=${SLEEP_AFTER_ENROLL_MS:-1000}
if [[ "$SLEEP_AFTER_ENROLL_MS" =~ ^[0-9]+$ && $SLEEP_AFTER_ENROLL_MS -gt 0 ]]; then
  sleep $(awk -v ms=$SLEEP_AFTER_ENROLL_MS 'BEGIN{printf("%.3f", ms/1000)}')
fi

# Unlock
UNLOCK_RESP=$(mktemp)
UNLOCK_CODE=$(curl -sS -w '%{http_code}' -o "$UNLOCK_RESP" -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-KNOX-API-VERSION: v1" \
  -H 'Content-Type: application/json' \
  "$API_BASE$UNLOCK_PATH" \
  -d "$UNLOCK_BODY" || echo '000')
UNLOCK_DATA=$(cat "$UNLOCK_RESP"); rm -f "$UNLOCK_RESP"
if [[ ! "$UNLOCK_CODE" =~ ^2 ]]; then
  err "Unlock failed code=$UNLOCK_CODE body=$UNLOCK_DATA"
  exit 2
fi
log "Unlock OK code=$UNLOCK_CODE"

echo "{\"enrollmentCode\":$ENROLL_CODE,\"unlockCode\":$UNLOCK_CODE}" # minimal combined result JSON
