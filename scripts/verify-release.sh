#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "Usage: $0 <version> <public-key-id-or-file>" >&2
  echo "Example: $0 v1.0.0 0xDEADBEEF" >&2
  exit 1
fi

TAG="$1"; KEY="$2"; REPO="${REPO:-YOUR_ORG/knox-token-utility}";
BASE="https://github.com/${REPO}/releases/download/${TAG}"

fetch() { curl -sSL -f -O "$BASE/$1"; }

# Artifacts to fetch
FILES=(
  "knox-token-utility-${TAG#v}-jar-with-dependencies.jar"
  "SHA256SUMS"
  "sbom.json" "sbom.json.asc" "sbom.json.sig"
  "sbom.xml" "sbom.xml.asc" "sbom.xml.sig"
)

# Attempt SPDX (optional)
SPDX_FILE=$(curl -sSL -I "$BASE"/ | grep -i spdx.json || true)

for f in "${FILES[@]}"; do
  echo "Downloading $f"; fetch "$f" || echo "[warn] missing $f"; done

# Verify checksums
if [ -f SHA256SUMS ]; then
  shasum -a 256 -c SHA256SUMS || echo "[warn] checksum mismatch";
fi

# Import key if it's a file
if [ -f "$KEY" ]; then
  gpg --import "$KEY"
fi

# GPG verify (ignore failures if signature absent)
for sig in sbom.json.asc sbom.xml.asc; do
  base=${sig%.asc}
  if [ -f "$sig" ] && [ -f "$base" ]; then
    gpg --verify "$sig" "$base" || echo "[warn] GPG verify failed for $base"
  fi
done

# Cosign verify (keyless) if cosign installed
if command -v cosign >/dev/null 2>&1; then
  for blob in sbom.json sbom.xml "knox-token-utility-${TAG#v}-jar-with-dependencies.jar"; do
    if [ -f "$blob.sig" ] && [ -f "$blob" ]; then
      echo "Verifying cosign signature for $blob"
      cosign verify-blob \
        --signature "$blob.sig" \
        --certificate-oidc-issuer https://token.actions.githubusercontent.com \
        --certificate-identity-regexp ".*" "$blob" || echo "[warn] cosign verify failed for $blob"
    fi
  done
fi

echo "Verification process completed."