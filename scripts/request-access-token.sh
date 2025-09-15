#!/usr/bin/env bash
# Use output from prepare-vars.sh to request an access token from Knox.
# Usage: ./scripts/request-access-token.sh <CLIENT_ID> [IMEI] [VALIDITY]
set -euo pipefail
CLIENT_ID=${1:-}
IMEI=${2:-356544761873907}
VALIDITY=${3:-30}
if [ -z "$CLIENT_ID" ]; then
  echo "Usage: $0 <CLIENT_ID> [IMEI] [VALIDITY]" >&2
  exit 1
fi
JSON=$(K_CLIENT_ID="$CLIENT_ID" K_DEVICE_IMEI="$IMEI" ./scripts/prepare-vars.sh "$CLIENT_ID" "$IMEI")
PUB=$(echo "$JSON" | jq -r '.publicKeyBase64')
JWT=$(echo "$JSON" | jq -r '.clientIdentifierJwt')
if [ -z "${KNOX_BASE_URL:-}" ]; then
  region_lc=$(echo "${KNOX_REGION:-us}" | tr 'A-Z' 'a-z')
  case "$region_lc" in
    eu) base_url="https://eu-api.samsungknox.com/kcs/v1";;
    ap) base_url="https://ap-api.samsungknox.com/kcs/v1";;
    us|*) base_url="https://us-api.samsungknox.com/kcs/v1";;
  esac
else
  base_url="$KNOX_BASE_URL"
fi
ENDPOINT="$base_url/ses/token"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ENDPOINT" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "X-SES-JWT: $JWT" \
  -H 'X-KNOX-API-VERSION: v1' \
  -d "{\"base64EncodedStringPublicKey\":\"$PUB\",\"validityForAccessTokenInMinutes\":$VALIDITY}")
CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$CODE" != "200" ]; then
  echo "Request failed (HTTP $CODE)" >&2
  echo "$BODY" >&2
  exit 1
fi
echo "$BODY" | jq '.'
