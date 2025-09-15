#!/usr/bin/env bash
# Generate client identifier JWT and export variables for subsequent Knox API calls.
# Usage: ./scripts/prepare-vars.sh <CLIENT_ID> [IMEI]
set -euo pipefail
CLIENT_ID=${1:-}
IMEI=${2:-356544761873907}
if [ -z "$CLIENT_ID" ]; then
  echo "Usage: $0 <CLIENT_ID> [IMEI]" >&2
  exit 1
fi
if [ ! -f private_key.pem ] || [ ! -f public_key.pem ]; then
  echo "[INFO] Keys missing; generating with CLI" >&2
  mvn -q -ntp -Dgpg.skip=true package
  java -jar target/pts-*-jar-with-dependencies.jar --mode generate-keys --client-id "$CLIENT_ID" --device-imei "$IMEI" >/dev/null || true
fi
# Build fat jar if absent
if ! ls target/pts-*-jar-with-dependencies.jar >/dev/null 2>&1; then
  mvn -q -ntp -Dgpg.skip=true package
fi
JAR=$(ls target/pts-*-jar-with-dependencies.jar | head -n1)
PUB_DER=$(openssl pkey -in private_key.pem -pubout -outform DER 2>/dev/null | base64)
JWT=$(jshell --class-path "$JAR" <<'EOF'
import java.nio.file.*;import com.samsung.knoxwsm.token.KnoxTokenUtility;import java.io.*;
var clientId = System.getenv("K_CLIENT_ID");
var imei = System.getenv("K_DEVICE_IMEI");
var pub = KnoxTokenUtility.getPublicKeyBase64(Path.of("public_key.pem"));
var jwt = KnoxTokenUtility.createSignedJWT(clientId, imei, pub, Files.newInputStream(Path.of("private_key.pem")));
System.out.print(jwt);
EOF
)
cat <<JSON
{
  "clientId": "$CLIENT_ID",
  "deviceImei": "$IMEI",
  "publicKeyBase64": "$PUB_DER",
  "clientIdentifierJwt": "$JWT"
}
JSON
