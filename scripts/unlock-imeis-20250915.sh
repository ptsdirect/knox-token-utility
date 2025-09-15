#!/usr/bin/env bash
set -euo pipefail

# Unlock each IMEI from a list (separate from upload process)
# Required:
#   ACCESS_TOKEN : Bearer token for Authorization header
# Optional:
#   KNOX_REGION (us|eu|ap) or KNOX_API_BASE_URL (explicit base like https://us-api.samsungknox.com/kcs/v1)
#   IMEI_FILE (default data/imeis-20250915.txt)
#   KNOX_GUARD_UNLOCK_PATH (default /kguard/devices/unlock)
#   DRY_RUN=1 (print actions only)
#   SLEEP_MS=200 (delay between calls)
#
# Exit codes:
#   0 success (all unlocked or dry run)
#   2 some failures occurred

if [[ -z "${ACCESS_TOKEN:-}" ]]; then
  echo "ERROR: ACCESS_TOKEN required" >&2
  exit 1
fi

IMEI_FILE=${IMEI_FILE:-data/imeis-20250915.txt}
if [[ ! -f "$IMEI_FILE" ]]; then
  echo "ERROR: IMEI file not found: $IMEI_FILE" >&2
  exit 1
fi

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
SLEEP_MS=${SLEEP_MS:-200}

echo "Unlock base: $API_BASE  path: $UNLOCK_PATH" >&2
FAIL=0
TOTAL=0
SUCCESS=0

while IFS= read -r raw; do
  IMEI=$(echo "$raw" | tr -d '\r' | xargs)
  [[ -z "$IMEI" ]] && continue
  ((TOTAL++)) || true
  if [[ ! "$IMEI" =~ ^[0-9]{15}$ ]]; then
    echo "SKIP invalid IMEI: $IMEI" >&2
    continue
  fi
  BODY="{\"deviceId\":\"$IMEI\",\"action\":\"unlock\"}"
  if [[ "${DRY_RUN:-}" == "1" ]]; then
    echo "[DRY_RUN] POST $API_BASE$UNLOCK_PATH $BODY" >&2
  else
    RESP=$(mktemp)
    CODE=$(curl -sS -w '%{http_code}' -o "$RESP" \
      -X POST \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "X-KNOX-API-VERSION: v1" \
      -H "Content-Type: application/json" \
      "$API_BASE$UNLOCK_PATH" \
      -d "$BODY" || echo '000')
    DATA=$(cat "$RESP"); rm -f "$RESP"
    if [[ "$CODE" =~ ^2 ]]; then
      echo "UNLOCK OK imei=$IMEI code=$CODE"
      ((SUCCESS++)) || true
    else
      echo "UNLOCK FAIL imei=$IMEI code=$CODE body=$DATA" >&2
      ((FAIL++)) || true
    fi
    # simple adaptive wait
    sleep $(awk -v ms=$SLEEP_MS 'BEGIN{printf("%.3f", ms/1000)}')
  fi
done < "$IMEI_FILE"

echo "Summary: total=$TOTAL success=$SUCCESS fail=$FAIL" >&2
if [[ $FAIL -gt 0 ]]; then
  exit 2
fi
exit 0
