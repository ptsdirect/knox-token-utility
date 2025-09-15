#!/usr/bin/env bash
set -euo pipefail

# Upload a batch of IMEIs to Knox Guard using the Upload API.
# Requirements:
#   ACCESS_TOKEN   : Access token for x-knox-apitoken header
#   CLIENT_ID      : (optional) only logged; not required for upload call
# Optional:
#   KNOX_REGION (us|eu|ap) or KNOX_API_BASE_URL to override base (kcs functional base auto-derived)
#   POLICY_FLAGS_JSON : e.g. '{"autoAccept":true}' merged into root payload
#   IMEI_FILE : path to list (default data/imeis-20250915.txt)
#   DRY_RUN=1 : print payload only
#
# Endpoint derived (region base): https://<region>-kcs-api.samsungknox.com/kcs/v1.1/kg/devices/uploads

if [[ "${ACCESS_TOKEN:-}" == "" ]]; then
  echo "ERROR: ACCESS_TOKEN environment variable required" >&2
  exit 1
fi

IMEI_FILE=${IMEI_FILE:-data/imeis-20250915.txt}
if [[ ! -f "$IMEI_FILE" ]]; then
  echo "ERROR: IMEI file not found: $IMEI_FILE" >&2
  exit 1
fi

REGION=${KNOX_REGION:-us}
if [[ -n "${KNOX_API_BASE_URL:-}" ]]; then
  # If explicit base supplied assume it is already like https://us-kcs-api.samsungknox.com/kcs/v1.1/kg
  BASE="${KNOX_API_BASE_URL%/}"
else
  case "$REGION" in
    us|US) BASE="https://us-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    eu|EU) BASE="https://eu-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    ap|AP) BASE="https://ap-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    *) BASE="https://${REGION}-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
  esac
fi

# Build deviceList JSON array
TMP_DEVICES=$(mktemp)
# Validate IMEI lines (15 digits) and build JSON objects
INDEX=0
{
  echo '['
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    IMEI=$(echo "$line" | tr -d '\r' | xargs)
    if [[ ! "$IMEI" =~ ^[0-9]{15}$ ]]; then
      echo "ERROR: Invalid IMEI (must be 15 digits): $IMEI" >&2
      rm -f "$TMP_DEVICES"
      exit 1
    fi
    SEP=','
    if [[ $INDEX -eq 0 ]]; then SEP=''; fi
    printf '%s{"deviceId":"%s","platform":"android"}' "$SEP" "$IMEI" >> "$TMP_DEVICES"
    INDEX=$((INDEX+1))
  done < "$IMEI_FILE"
  echo ']'
} >> "$TMP_DEVICES"

DEVICE_LIST_JSON=$(tr -d '\n' < "$TMP_DEVICES")
rm -f "$TMP_DEVICES"

if [[ -n "${POLICY_FLAGS_JSON:-}" ]]; then
  # Merge simple top-level flags (expects small JSON object) using jq if available
  if command -v jq >/dev/null 2>&1; then
    PAYLOAD=$(jq -c --argjson dl "$DEVICE_LIST_JSON" '.deviceList=$dl | . += $ENV.POLICY_FLAGS_JSON | .deviceList' <<<"{}" 2>/dev/null || true)
  fi
fi

# Fallback manual merge (no jq). POLICY_FLAGS_JSON only appended if provided and simple.
if [[ -z "${PAYLOAD:-}" ]]; then
  if [[ -n "${POLICY_FLAGS_JSON:-}" ]]; then
    # naive merge; assumes POLICY_FLAGS_JSON is like {"autoAccept":true}
    STRIPPED=${POLICY_FLAGS_JSON#\{}; STRIPPED=${STRIPPED%\}}
    PAYLOAD="{\"deviceList\":$DEVICE_LIST_JSON,${STRIPPED}}"
  else
    PAYLOAD="{\"deviceList\":$DEVICE_LIST_JSON}"
  fi
fi

if [[ "${DRY_RUN:-}" == "1" ]]; then
  echo "[DRY_RUN] Would POST to $BASE/devices/uploads" >&2
  echo "$PAYLOAD"
  exit 0
fi

RESP=$(mktemp)
HTTP_CODE=$(curl -sS -w '%{http_code}' -o "$RESP" -X POST \
  -H "x-knox-apitoken: $ACCESS_TOKEN" \
  -H "X-KNOX-API-VERSION: v1" \
  -H 'Content-Type: application/json' \
  "$BASE/devices/uploads" \
  -d "$PAYLOAD")

BODY=$(cat "$RESP")
rm -f "$RESP"

if [[ "$HTTP_CODE" =~ ^2 ]]; then
  echo "Upload accepted (HTTP $HTTP_CODE)" >&2
  echo "$BODY"
else
  echo "ERROR: Upload failed HTTP $HTTP_CODE" >&2
  echo "$BODY" >&2
  exit 1
fi
