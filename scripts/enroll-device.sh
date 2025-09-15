#!/usr/bin/env bash
# Enroll a device (IMEI) into Knox Guard for a given client ID using the local utility.
# Usage: ./scripts/enroll-device.sh <CLIENT_ID> <IMEI> [VALIDITY]
# Requires: jq, curl, openssl, mvn (first run builds jar automatically)
set -euo pipefail
CLIENT_ID=${1:-}
IMEI=${2:-}
VALIDITY=${3:-30}
if [ -z "$CLIENT_ID" ] || [ -z "$IMEI" ]; then
  echo "Usage: $0 <CLIENT_ID> <IMEI> [VALIDITY]" >&2
  exit 1
fi
# Prepare vars (generates keys + client identifier JWT)
JSON=$(K_CLIENT_ID="$CLIENT_ID" K_DEVICE_IMEI="$IMEI" ./scripts/prepare-vars.sh "$CLIENT_ID" "$IMEI")
PUB=$(echo "$JSON" | jq -r '.publicKeyBase64')
JWT=$(echo "$JSON" | jq -r '.clientIdentifierJwt')
if [ -z "${KNOX_BASE_URL:-}" ]; then
  region_lc=$(echo "${KNOX_REGION:-us}" | tr 'A-Z' 'a-z')
  case "$region_lc" in
    eu) BASE_URL="https://eu-api.samsungknox.com/kcs/v1";;
    ap) BASE_URL="https://ap-api.samsungknox.com/kcs/v1";;
    us|*) BASE_URL="https://us-api.samsungknox.com/kcs/v1";;
  esac
else
  BASE_URL="$KNOX_BASE_URL"
fi
API_VERSION=${KNOX_API_VERSION:-v1}
# 1. Access token
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/ses/token" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "X-SES-JWT: $JWT" \
  -H "X-KNOX-API-VERSION: $API_VERSION" \
  -d "{\"base64EncodedStringPublicKey\":\"$PUB\",\"validityForAccessTokenInMinutes\":$VALIDITY}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$CODE" != "200" ]; then
  echo "[ERROR] Access token request failed (HTTP $CODE)" >&2
  echo "$BODY" >&2
  exit 2
fi
ACCESS_TOKEN=$(echo "$BODY" | jq -r '.accessToken')
# Optional refresh if present
REFRESH_TOKEN=$(echo "$BODY" | jq -r '.refreshToken // empty')
# 2. Enrollment
ENR=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/kguard/devices" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-KNOX-API-VERSION: $API_VERSION" \
  -d "{\"deviceId\":\"$IMEI\",\"clientId\":\"$CLIENT_ID\",\"platform\":\"android\"}")
ENR_CODE=$(echo "$ENR" | tail -n1)
ENR_BODY=$(echo "$ENR" | sed '$d')
if [ "$ENR_CODE" != "200" ] && [ "$ENR_CODE" != "201" ]; then
  echo "[ERROR] Enrollment failed (HTTP $ENR_CODE)" >&2
  echo "$ENR_BODY" >&2
  exit 3
fi
jq -n --arg clientId "$CLIENT_ID" --arg imei "$IMEI" --arg accessToken "${ACCESS_TOKEN:0:12}..." \
      --arg refreshToken "$REFRESH_TOKEN" --arg statusCode "$ENR_CODE" --argraw enrollment "$ENR_BODY" '{clientId:$clientId, imei:$imei, status:"enrolled", statusCode:$statusCode, accessTokenSample:$accessToken, refreshToken:$refreshToken, enrollment:($enrollment|fromjson? // $enrollment)}'
