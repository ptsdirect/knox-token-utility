#!/usr/bin/env bash
# Refresh an access token using a stored refresh token (JSON file or arg).
# Usage: ./scripts/refresh-access-token.sh <CLIENT_ID> <REFRESH_TOKEN|token.json> [VALIDITY]
set -euo pipefail
CLIENT_ID=${1:-}
REFRESH_SPEC=${2:-}
VALIDITY=${3:-30}
if [ -z "$CLIENT_ID" ] || [ -z "$REFRESH_SPEC" ]; then
  echo "Usage: $0 <CLIENT_ID> <REFRESH_TOKEN|token.json> [VALIDITY]" >&2
  exit 1
fi
if [ -f "$REFRESH_SPEC" ]; then
  REFRESH_TOKEN=$(jq -r '.refreshToken // .refresh_token // empty' "$REFRESH_SPEC")
else
  REFRESH_TOKEN="$REFRESH_SPEC"
fi
if [ -z "${REFRESH_TOKEN:-}" ]; then
  echo "No refresh token resolved" >&2
  exit 2
fi
# Derive public key + jwt again (some backends may require matching key each time)
JSON=$(./scripts/prepare-vars.sh "$CLIENT_ID")
PUB=$(echo "$JSON" | jq -r '.publicKeyBase64')
# Region/base resolution (matches other scripts)
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
BODY=$(jq -n --arg pk "$PUB" --arg rt "$REFRESH_TOKEN" --argjson v $VALIDITY '{base64EncodedStringPublicKey:$pk,refreshToken:$rt,validityForAccessTokenInMinutes:$v}')
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/ses/token/refresh" \
  -H 'Content-Type: application/json' \
  -H "X-KNOX-API-VERSION: $API_VERSION" \
  -d "$BODY")
CODE=$(echo "$RESP" | tail -n1)
DATA=$(echo "$RESP" | sed '$d')
if [ "$CODE" != "200" ]; then
  echo "[ERROR] Refresh failed (HTTP $CODE)" >&2
  echo "$DATA" >&2
  exit 3
fi
echo "$DATA" | jq '.'
