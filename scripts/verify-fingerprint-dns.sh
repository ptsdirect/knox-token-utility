#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "Usage: $0 <fqdn> <expected_fingerprint_hex_no_spaces>" >&2
  echo "Example: $0 _openpgpkey.example.org ABCDEF1234567890ABCDEF1234567890ABCDEF12" >&2
  exit 1
fi

FQDN=$1
EXPECT=$(echo "$2" | tr '[:lower:]' '[:upper:]')

# Use dig with DNSSEC (AD flag) and fallback to delv if available.
DIG_OUT=$(dig +dnssec +multi TXT "$FQDN" 2>/dev/null || true)
if echo "$DIG_OUT" | grep -q 'status: NOERROR'; then
  if echo "$DIG_OUT" | grep -q 'flags:.* ad'; then
    echo "[dnssec] AD (Authenticated Data) flag present for $FQDN"
  else
    echo "[dnssec][warn] AD flag not present; resolver may not validate DNSSEC" >&2
  fi
  TXT_LINE=$(echo "$DIG_OUT" | awk '/TXT/ {print}' | tr -d '"')
  echo "[dnssec] TXT record: $TXT_LINE"
else
  echo "[dnssec][error] Could not resolve $FQDN" >&2
fi

if command -v delv >/dev/null 2>&1; then
  echo "[dnssec] Running delv for independent validation..."
  if delv +vtrace "$FQDN" TXT >/dev/null 2>&1; then
    echo "[dnssec] delv validation succeeded"
  else
    echo "[dnssec][warn] delv validation failed or not fully secure" >&2
  fi
fi

# Extract fingerprint (heuristics for v=OpenPGP lines or generic key=value)
FPR=$(echo "$DIG_OUT" | grep -Eo 'fpr=[A-Fa-f0-9]+' | head -1 | cut -d= -f2 || true)
if [ -z "$FPR" ]; then
  FPR=$(echo "$DIG_OUT" | grep -Eo 'gpg-fingerprint=[A-Fa-f0-9]+' | head -1 | cut -d= -f2 || true)
fi
if [ -z "$FPR" ]; then
  echo "[dnssec][warn] Could not parse fingerprint from TXT record." >&2
  exit 2
fi
FPR_UP=$(echo "$FPR" | tr '[:lower:]' '[:upper:]')

if [ "$FPR_UP" = "$EXPECT" ]; then
  echo "[dnssec] Fingerprint matches expected."; exit 0
else
  echo "[dnssec][error] Fingerprint mismatch. Got $FPR_UP expected $EXPECT" >&2; exit 3
fi
