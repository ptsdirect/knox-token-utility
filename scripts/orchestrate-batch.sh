#!/usr/bin/env bash
set -euo pipefail
# High-level batch orchestration: upload IMEIs -> poll upload -> unlock devices.
# Required:
#   ACCESS_TOKEN : Access token for both upload (x-knox-apitoken) and unlock (Bearer) flows.
# Optional:
#   IMEI_FILE (default data/imeis-20250915.txt)
#   KNOX_REGION or KNOX_API_BASE_URL (functional base override) and KNOX_GUARD_FUNCTION_BASE_URL
#   POLICY_FLAGS_JSON (merged into upload payload)
#   POLL_INTERVAL_SEC=5 POLL_TIMEOUT_SEC=300
#   NO_UNLOCK=1 (skip unlock phase)
#   DRY_RUN=1 (dry run inner scripts)
#   VERBOSE=1
# Uses existing scripts: upload-imeis-20250915.sh, poll-upload.sh, unlock-imeis-20250915.sh

log(){ [[ "${VERBOSE:-}" == "1" ]] && echo "[INFO] $*" >&2 || true; }
err(){ echo "ERROR: $*" >&2; }

[[ -z "${ACCESS_TOKEN:-}" ]] && { err "ACCESS_TOKEN required"; exit 1; }

IMEI_FILE=${IMEI_FILE:-data/imeis-20250915.txt}
if [[ ! -f "$IMEI_FILE" ]]; then err "IMEI file not found: $IMEI_FILE"; exit 1; fi

DRY=${DRY_RUN:-}

log "Starting batch upload (file=$IMEI_FILE)"
UPLOAD_OUT=$(mktemp)
if ! IMEI_FILE="$IMEI_FILE" DRY_RUN="$DRY" POLICY_FLAGS_JSON="${POLICY_FLAGS_JSON:-}" ACCESS_TOKEN="$ACCESS_TOKEN" \
  ./scripts/upload-imeis-20250915.sh > "$UPLOAD_OUT"; then
  err "Upload script failed"; cat "$UPLOAD_OUT" >&2; rm -f "$UPLOAD_OUT"; exit 2
fi
if command -v jq >/dev/null 2>&1; then
  UPLOAD_ID=$(jq -r '(.id // .uploadId // .data.id // .data.uploadId) // empty' "$UPLOAD_OUT" | head -n1)
else
  UPLOAD_ID=$(grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' "$UPLOAD_OUT" | head -n1 | sed 's/.*:"\([^"]*\)".*/\1/')
fi
log "Raw upload response captured: $(wc -c < "$UPLOAD_OUT") bytes"
if [[ -z "$UPLOAD_ID" ]]; then
  if [[ "$DRY" == "1" ]]; then
    log "DRY_RUN: skipping poll/unlock (no uploadId in dry output)"
    cat "$UPLOAD_OUT"; rm -f "$UPLOAD_OUT"; exit 0
  fi
  err "Could not extract uploadId from upload response"; cat "$UPLOAD_OUT" >&2; rm -f "$UPLOAD_OUT"; exit 3
fi
log "Detected uploadId=$UPLOAD_ID"

# Poll
POLL_INTERVAL_SEC=${POLL_INTERVAL_SEC:-5}
POLL_TIMEOUT_SEC=${POLL_TIMEOUT_SEC:-300}
UPLOAD_FINAL=$(mktemp)
if [[ "$DRY" == "1" ]]; then
  log "DRY_RUN: skipping real poll"
else
  log "Polling upload status (interval=${POLL_INTERVAL_SEC}s timeout=${POLL_TIMEOUT_SEC}s)"
  if ! ACCESS_TOKEN="$ACCESS_TOKEN" UPLOAD_ID="$UPLOAD_ID" INTERVAL_SEC="$POLL_INTERVAL_SEC" TIMEOUT_SEC="$POLL_TIMEOUT_SEC" \
       ./scripts/poll-upload.sh > "$UPLOAD_FINAL"; then
    err "Polling ended without success (timeout or error)"
    cat "$UPLOAD_FINAL" >&2
    rm -f "$UPLOAD_OUT" "$UPLOAD_FINAL"
    exit 4
  fi
fi

# Unlock
if [[ "${NO_UNLOCK:-}" == "1" ]]; then
  log "NO_UNLOCK set: skipping unlock phase"
  cat "$UPLOAD_OUT" # surface initial upload for caller
  rm -f "$UPLOAD_OUT" "$UPLOAD_FINAL"
  exit 0
fi

if [[ "$DRY" == "1" ]]; then
  log "DRY_RUN: skipping actual unlock"
  cat "$UPLOAD_OUT"
  rm -f "$UPLOAD_OUT" "$UPLOAD_FINAL"
  exit 0
fi

log "Starting unlock pass for devices in $IMEI_FILE"
if ! IMEI_FILE="$IMEI_FILE" ACCESS_TOKEN="$ACCESS_TOKEN" ./scripts/unlock-imeis-20250915.sh; then
  err "Unlock script reported failures (exit=$?)"; rm -f "$UPLOAD_OUT" "$UPLOAD_FINAL"; exit 5
fi

log "Batch orchestration complete (uploadId=$UPLOAD_ID)"
# Output combined minimal JSON summary referencing uploadId
printf '{"uploadId":"%s","status":"completed"}' "$UPLOAD_ID"
rm -f "$UPLOAD_OUT" "$UPLOAD_FINAL"
