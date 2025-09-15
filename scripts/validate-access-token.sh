#!/usr/bin/env bash
# Validate an access token using Knox Guard validate endpoint.
# Usage: ./scripts/validate-access-token.sh <CLIENT_ID> <ACCESS_TOKEN|token.json>
set -euo pipefail
CLIENT_ID=${1:-}
TOKEN_SPEC=${2:-}
if [ -z "$CLIENT_ID" ] || [ -z "$TOKEN_SPEC" ]; then
  echo "Usage: $0 <CLIENT_ID> <ACCESS_TOKEN|token.json>" >&2
  exit 1
fi
if [ -f "$TOKEN_SPEC" ]; then
  ACCESS_TOKEN=$(jq -r '.accessToken // .access_token // empty' "$TOKEN_SPEC")
else
  ACCESS_TOKEN="$TOKEN_SPEC"
fi
if [ -z "${ACCESS_TOKEN:-}" ]; then
  echo "No access token resolved" >&2
  exit 2
fi
# Region/base resolution (consistent with other scripts)
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
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/ses/token/validate" \
  -H 'Content-Type: application/json' \
  -H "X-KNOX-API-VERSION: $API_VERSION" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{}')
CODE=$(echo "$RESP" | tail -n1)
DATA=$(echo "$RESP" | sed '$d')
if [ "$CODE" != "200" ]; then
  echo "[ERROR] Validate failed (HTTP $CODE)" >&2
  echo "$DATA" >&2
  exit 3
fi
echo "$DATA" | jq '.'
