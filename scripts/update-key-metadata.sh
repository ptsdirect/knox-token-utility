#!/usr/bin/env bash
set -euo pipefail

META="docs/key-metadata.json"
NEW_FPR=${1:-}
ALGO=${2:-ed25519}
USAGE=${3:-signing}
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

if [ -z "$NEW_FPR" ]; then
  echo "Usage: $0 <NEW_FINGERPRINT_HEX_NO_SPACES> [algorithm] [usage]" >&2
  exit 1
fi

if [ ! -f "$META" ]; then
  echo "Metadata file $META not found" >&2
  exit 2
fi

# Extract current fingerprint
CURRENT=$(jq -r '.current.fingerprint' "$META")
if [ "$CURRENT" = "$NEW_FPR" ]; then
  echo "[key-rotation] New fingerprint matches current; nothing to do." >&2
  exit 0
fi

# Append current to previous with retirement timestamp and overlap window
jq --arg OLD "$CURRENT" \
   --arg NEW "$NEW_FPR" \
   --arg NOW "$NOW" \
   --arg ALGO "$ALGO" \
   --arg USG "$USAGE" \
   '.previous += [{fingerprint:$OLD, retired:$NOW, supersededBy:$NEW, overlapUntil:null, revoked:false}] |
    .current = {fingerprint:$NEW, created:$NOW, expires:null, algorithm:$ALGO, usage:[$USG], revoked:false}' "$META" > "$META.tmp"

mv "$META.tmp" "$META"

echo "[key-rotation] Rotated current -> $NEW_FPR (old archived)."