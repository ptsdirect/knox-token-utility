#!/usr/bin/env bash
set -euo pipefail

META="docs/key-metadata.json"
FPR_ENV=${GPG_FINGERPRINT:-}

if [ -z "$FPR_ENV" ]; then
  echo "[key-validate] GPG_FINGERPRINT env not set" >&2
  exit 1
fi

if [ ! -f "$META" ]; then
  echo "[key-validate] Metadata file missing: $META" >&2
  exit 2
fi

CUR=$(jq -r '.current.fingerprint' "$META")
if [ "$CUR" = "<CURRENT_FPR>" ]; then
  echo "[key-validate][warn] Metadata still uses placeholder fingerprint." >&2
  exit 3
fi

if [ "$CUR" != "$FPR_ENV" ]; then
  echo "[key-validate][error] Mismatch: metadata=$CUR env=$FPR_ENV" >&2
  exit 4
fi

echo "[key-validate] Fingerprint matches metadata current."