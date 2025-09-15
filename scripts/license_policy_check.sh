#!/usr/bin/env bash
set -euo pipefail

SBOM_JSON=${1:-target/sbom.json}
BANNED_LIST=${BANNED_LICENSES:-"AGPL-3.0,SSPL-1.0"}
IFS=',' read -r -a BANNED <<< "$BANNED_LIST"

if [ ! -f "$SBOM_JSON" ]; then
  echo "[license-policy] SBOM not found at $SBOM_JSON" >&2
  exit 2
fi

FAILED=0
for lic in "${BANNED[@]}"; do
  if jq -e --arg L "$lic" '.components[]? | select(.licenses[]?.license.id == $L)' "$SBOM_JSON" >/dev/null; then
    echo "[license-policy] Forbidden license detected: $lic" >&2
    FAILED=1
  fi
  # SPDX style license expressions inside SPDX file if present
  if ls target/site/*.spdx.json >/dev/null 2>&1; then
    if jq -e --arg L "$lic" '.packages[]? | select(.licenseDeclared == $L or .licenseConcluded == $L)' target/site/*.spdx.json >/dev/null; then
      echo "[license-policy] Forbidden SPDX package license: $lic" >&2
      FAILED=1
    fi
  fi
done

if [ $FAILED -eq 1 ]; then
  echo "[license-policy] Policy violations detected." >&2
  exit 3
fi

echo "[license-policy] No forbidden licenses found."