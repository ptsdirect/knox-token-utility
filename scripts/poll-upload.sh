#!/usr/bin/env bash
set -euo pipefail
# Poll a device upload status until success/terminal or timeout.
# Required:
#   ACCESS_TOKEN : x-knox-apitoken header value
#   UPLOAD_ID    : The uploadId returned from batch upload
# Optional:
#   KNOX_REGION (us|eu|ap) or KNOX_API_BASE_URL (explicit functional base like https://us-kcs-api.samsungknox.com/kcs/v1.1/kg)
#   INTERVAL_SEC (default 5)
#   TIMEOUT_SEC  (default 300)
#   DRY_RUN=1    (show URL and exit)
#   VERBOSE=1    (extra logs)
# Output: prints last JSON body to stdout; exits 0 on success, 2 on timeout.

log(){ [[ "${VERBOSE:-}" == "1" ]] && echo "[INFO] $*" >&2 || true; }
err(){ echo "ERROR: $*" >&2; }

[[ -z "${ACCESS_TOKEN:-}" ]] && { err "ACCESS_TOKEN required"; exit 1; }
[[ -z "${UPLOAD_ID:-}" ]] && { err "UPLOAD_ID required"; exit 1; }

REGION=${KNOX_REGION:-us}
if [[ -n "${KNOX_API_BASE_URL:-}" ]]; then
  BASE="${KNOX_API_BASE_URL%/}"
else
  case "$REGION" in
    us|US) BASE="https://us-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    eu|EU) BASE="https://eu-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    ap|AP) BASE="https://ap-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
    *) BASE="https://${REGION}-kcs-api.samsungknox.com/kcs/v1.1/kg" ;;
  esac
fi
URL="$BASE/devices/uploads/$UPLOAD_ID"

INTERVAL_SEC=${INTERVAL_SEC:-5}
TIMEOUT_SEC=${TIMEOUT_SEC:-300}

if [[ "${DRY_RUN:-}" == "1" ]]; then
  echo "[DRY_RUN] Would poll $URL every ${INTERVAL_SEC}s for up to ${TIMEOUT_SEC}s" >&2
  exit 0
fi

start=$(date +%s)
lastBody=""
while true; do
  now=$(date +%s)
  elapsed=$(( now - start ))
  if (( elapsed > TIMEOUT_SEC )); then
    err "Timeout (${TIMEOUT_SEC}s) waiting for upload $UPLOAD_ID"
    if [[ -n "$lastBody" ]]; then echo "$lastBody"; fi
    exit 2
  fi
  RESP=$(mktemp)
  CODE=$(curl -sS -w '%{http_code}' -o "$RESP" \
    -H "x-knox-apitoken: $ACCESS_TOKEN" \
    -H "X-KNOX-API-VERSION: v1" \
    "$URL" || echo '000')
  BODY=$(cat "$RESP"); rm -f "$RESP"
  if [[ "$CODE" =~ ^2 ]]; then
    lastBody="$BODY"
    # naive detection: if status field present and not 'processing' / 'pending'
    STATUS=$(echo "$BODY" | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
    COUNT_TOTAL=$(echo "$BODY" | sed -n 's/.*"total"[[:space:]]*:[[:space:]]*\([0-9]\+\).*/\1/p' | head -n1)
    log "poll code=$CODE status=${STATUS:-unknown} elapsed=${elapsed}s total=${COUNT_TOTAL:-?}"
    if [[ -n "$STATUS" ]]; then
      case "$STATUS" in
        processing|pending|in_progress) : ;; # continue
        *) echo "$BODY"; exit 0 ;;          # terminal state
      esac
    fi
  else
    err "HTTP $CODE while polling (will retry)"
  fi
  sleep "$INTERVAL_SEC"
done
