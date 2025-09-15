# Knox Guard Token Utility – Unified Usage Guide

This file consolidates ALL practical instructions for building, running, scripting, containerizing, and verifying the service/library. It complements `README.md` by giving you a single reference you asked for.

---
## 1. Build Artifacts

Fat JAR (includes all dependencies):
```bash
mvn -ntp -Dgpg.skip=true package
```
Result: `target/pts-<version>-jar-with-dependencies.jar` (e.g. `pts-1.0.0-jar-with-dependencies.jar`).

Full quality gates (coverage, SpotBugs, Checkstyle, SBOM, SPDX):
```bash
mvn -ntp clean verify
```

Fast iteration (skip heavy checks):
```bash
mvn -Pfast -Dfast -ntp clean package
```

Low-space profile (keeps tests, trims reports):
```bash
mvn -Plowspace -Dlowspace clean verify
```

---
## 2. Core Files Produced
| Purpose | Path |
|---------|------|
| Fat executable JAR | `target/pts-*-jar-with-dependencies.jar` |
| CycloneDX SBOM | `target/sbom.json` / `target/sbom.xml` |
| SPDX SBOM | `target/site/*.spdx.json` |
| Coverage reports | `target/site/jacoco/` (full) or `target/jacoco-min-report` (lowspace) |

---
## 3. CLI (Launcher Delegates to `TokenClient`)
Show help:
```bash
java -jar target/pts-*-jar-with-dependencies.jar --help
```
Common modes:
```bash
# Generate key pair if missing
java -jar target/pts-*-jar-with-dependencies.jar --mode generate-keys --client-id YOUR_CLIENT_ID

# Request access token
java -jar target/pts-*-jar-with-dependencies.jar --mode request-token --client-id YOUR_CLIENT_ID --output-json

# Validate token
java -jar target/pts-*-jar-with-dependencies.jar --mode validate-token --client-id YOUR_CLIENT_ID

# Enroll device (token + enrollment)
java -jar target/pts-*-jar-with-dependencies.jar --mode enroll-guard --client-id YOUR_CLIENT_ID --device-imei 356544761873907

# Full flow (keys → jwt → token → enroll)
java -jar target/pts-*-jar-with-dependencies.jar --mode full-flow --client-id YOUR_CLIENT_ID --device-imei 356544761873907
```
Certificate-based signing:
```bash
java -jar target/pts-*-jar-with-dependencies.jar --mode sign-client-idp --certificate certificate.json --client-id YOUR_CLIENT_ID --idp-access-token TOKEN
java -jar target/pts-*-jar-with-dependencies.jar --mode sign-session --certificate certificate.json --session-token SESSION_TOKEN
java -jar target/pts-*-jar-with-dependencies.jar --mode sign-access --certificate certificate.json --access-token-raw ACCESS_TOKEN
```

Encryption helper (expects RSA public key in `public_key.pem`):
```bash
java -jar target/pts-*-jar-with-dependencies.jar --mode encrypt --public-key public_rsa.pem --plaintext "secret-value"
```

Options summary (see `--help` for full list):
- `--client-id <id>`
- `--device-imei <imei>`
- `--validity <minutes>` (15–60)
- `--private-key <path>` / `--public-key <path>`
- `--certificate <certificate.json>`
- `--output-json` / `--quiet`

Environment variable fallbacks: `KNOX_GUARD_CLIENT_ID`, `DEVICE_IMEI`, `PRIVATE_KEY_PATH`, `PUBLIC_KEY_PATH`.

---
## 4. Helper Scripts (Recommended)
Scripts auto-build the fat JAR if missing.

Generate client identifier JWT & derived values:
```bash
./scripts/prepare-vars.sh YOUR_CLIENT_ID [IMEI]
```
Example output:
```json
{
  "clientId": "YOUR_CLIENT_ID",
  "deviceImei": "356544761873907",
  "publicKeyBase64": "...",
  "clientIdentifierJwt": "eyJ..."
}
```

Request real Knox access token:
```bash
./scripts/request-access-token.sh YOUR_CLIENT_ID [IMEI] [VALIDITY]
```
Refresh an access token (input can be raw refresh token or JSON file containing it):
```bash
./scripts/refresh-access-token.sh YOUR_CLIENT_ID token.json
# or
./scripts/refresh-access-token.sh YOUR_CLIENT_ID REFRESH_TOKEN_STRING
```
Validate an access token (raw token or JSON file with accessToken field):
```bash
./scripts/validate-access-token.sh YOUR_CLIENT_ID token.json
```
Override base URL (regional):
```bash
KNOX_BASE_URL="https://eu-api.samsungknox.com/kcs/v1" ./scripts/request-access-token.sh YOUR_CLIENT_ID
```

Run CLI generically (passes args):
```bash
./scripts/run-cli.sh --mode request-token --client-id YOUR_CLIENT_ID
```

Run server wrapper:
```bash
./scripts/run-server.sh         # PORT=8080 default
PORT=9090 ./scripts/run-server.sh
```

Other utility scripts (if present):
| Script | Purpose |
|--------|---------|
| `release.sh` | Version bump + tag + optional push/sign |
| `update-key-metadata.sh` | Maintain key rotation metadata |
| `validate-key-metadata.sh` | Check rotation metadata integrity |
| `verify-release.sh` | GPG / checksum validation routine |
| `license_policy_check.sh` | Scan SBOM for disallowed licenses |
| `disk_cleanup_mac.sh` | Free disk space for builds |
| `refresh-access-token.sh` | Refresh existing access using refresh token |
| `validate-access-token.sh` | Validate access token (HTTP 200 => valid) |

---
## 4.1 Enroll Device Script (New)
Automated one-shot: generate client identifier JWT → request access token → enroll device.

Script:
```bash
./scripts/enroll-device.sh <CLIENT_ID> <IMEI> [VALIDITY]
```
Example (your request):
```bash
./scripts/enroll-device.sh 5089824242 353677431308848 30
```
What it outputs (sample structure):
```json
{
  "clientId": "5089824242",
  "imei": "353677431308848",
  "status": "enrolled",
  "statusCode": "200",
  "accessTokenSample": "eyJhbGciOi...",
  "refreshToken": "<maybe-present>",
  "enrollment": { /* raw enrollment response or string if not JSON */ }
}
```
Exit codes:
- 0 success
- 2 access token failure
- 3 enrollment failure

Environment overrides honored: `KNOX_BASE_URL`, `KNOX_API_VERSION`.

---
## 5. Local HTTP Server
Start:
```bash
java -jar target/pts-*-jar-with-dependencies.jar server
```
Custom port:
```bash
java -jar target/pts-*-jar-with-dependencies.jar server 9090
# or
PORT=9090 java -jar target/pts-*-jar-with-dependencies.jar server
```
Request a demo JWT:
```bash
curl -s -X POST http://localhost:8080/api/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"client-123","deviceImei":"356544761873907"}'
```
Note: Demo endpoint is for experimentation—NOT production enrollment.

---
## 6. Docker
Build:
```bash
docker build -t knox-token-util .
```
Skip tests (ARG controlled):
```bash
docker build -t knox-token-util --build-arg SKIP_TESTS=true .
```
CLI help:
```bash
docker run --rm knox-token-util --help
```
Persist keys (mount host directory):
```bash
docker run --rm -v "$PWD/data:/data" -w /data \
  -e KNOX_GUARD_CLIENT_ID=YOUR_CLIENT_ID \
  knox-token-util --mode request-token --output-json
```
Server mode:
```bash
docker run --rm -p 8080:8080 knox-token-util server
```
Custom port:
```bash
docker run --rm -e PORT=9090 -p 9090:9090 knox-token-util server
```

---
## 7. Real Knox SES Token Flow (Manual cURL)
After generating values:
```bash
VARS=$(./scripts/prepare-vars.sh YOUR_CLIENT_ID)
PUB=$(echo "$VARS" | jq -r '.publicKeyBase64')
JWT=$(echo "$VARS" | jq -r '.clientIdentifierJwt')
ENDPOINT="https://api.samsungknox.com/kcs/v1/ses/token"

curl -s -X POST "$ENDPOINT" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "X-SES-JWT: $JWT" \
  -H 'X-KNOX-API-VERSION: v1' \
  -d "{\"base64EncodedStringPublicKey\":\"$PUB\",\"validityForAccessTokenInMinutes\":30}" | jq
```

---
## 8. JShell Quick Exploration
```bash
jshell --class-path target/pts-*-jar-with-dependencies.jar
jshell> import java.nio.file.*;import com.samsung.knoxwsm.token.*;import java.io.*;
jshell> var pem = Files.readString(Path.of("private_key.pem"));
jshell> var jwt = KnoxTokenUtility.createEnrollmentJwt("client-123","356544761873907", pem);
jshell> System.out.println(jwt);
```
Warning: `createEnrollmentJwt` inserts placeholder `x5c` header; not accepted by real Knox production flows.

---
## 9. Programmatic Integration
Maven dependency:
```xml
<dependency>
  <groupId>com.mdttee.knox</groupId>
  <artifactId>pts</artifactId>
  <version>1.0.0</version>
</dependency>
```
Java code:
```java
try (var pk = java.nio.file.Files.newInputStream(java.nio.file.Path.of("private_key.pem"))) {
    String pub = KnoxTokenUtility.getPublicKeyBase64(java.nio.file.Path.of("public_key.pem"));
    String jwt = KnoxTokenUtility.createSignedJWT("YOUR_CLIENT_ID", "356544761873907", pub, pk);
    KnoxAuthClient c = new KnoxAuthClient();
    var token = c.requestAccessToken(pub, jwt, 30);
    System.out.println(token.get("accessToken"));
}
```

---
## 10. Security Guidelines
| Item | Guidance |
|------|----------|
| Private Keys | Never commit; `chmod 600 private_key.pem` |
| Long-lived JWT | Client identifier JWT uses long expiry; scope minimal |
| Logs | Avoid printing raw access/enrollment tokens in production logs |
| Rotation | Use key metadata scripts for yearly rotation + overlap |
| Transport | Always HTTPS to Knox | 

---
## 11. Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| Exit code 1 running `TokenClient` | Ran class directly w/o deps | Use fat JAR or `run-cli.sh` |
| `jshell: command not found` | JDK not installed / PATH | Install JDK 21, ensure `jshell` present |
| `jq: command not found` | jq missing | `brew install jq` |
| HTTP 401/403 from SES | Wrong clientId/pub key mismatch | Re-register or supply correct key pair |
| `Unsupported mode` | Typo in `--mode` | Run with `--help` to list modes |
| GPG signing failure | No local key / passphrase prompt in CI | Add `-Dgpg.skip=true` locally or configure keys |
| Build disk errors | Low space | Run `scripts/disk_cleanup_mac.sh` or use `-Plowspace` |

---
## 12. SBOM & SPDX Quick Checks
```bash
jq '.components | length' target/sbom.json
jq '.packages | length' target/site/*.spdx.json 2>/dev/null || echo 'SPDX not generated yet'
```

License policy scan:
```bash
./scripts/license_policy_check.sh target/sbom.json
```

---
## 13. Release & Verification (Outline)
1. Bump version & tag:
   ```bash
   ./scripts/release.sh 1.0.1 --sign
   ```
2. Push tag → CI builds artifacts.
3. Download artifacts + `SHA256SUMS` + signatures.
4. Verify:
   ```bash
   shasum -a 256 -c SHA256SUMS
   gpg --verify pts-1.0.1-jar-with-dependencies.jar.asc pts-1.0.1-jar-with-dependencies.jar
   ```
5. (Optional) Cosign verify SBOM if provided.

---
## 14. Key Fingerprint Publication (Strategy Summary)
| Channel | Suggested Content |
|---------|-------------------|
| DNS TXT | `gpg-fpr=<FPR>` |
| Release Asset | `KEY_FINGERPRINT` file |
| Docs | `docs/KEY_FINGERPRINT.txt` |
| Well-Known | `.well-known/openpgp-fingerprint` |

Cross-check at least two sources + DNSSEC if available.

---
## 15. Future Enhancements (Optional)
- Refresh/access token renewal script
- Device enrollment standalone script
- Integration tests (MockWebServer)
- Cosign attestations in CI
- Structured JSON logging profile

---
## 16. One-Command Quick Demos
Get token (builds if needed):
```bash
./scripts/request-access-token.sh YOUR_CLIENT_ID
```
Refresh token (assuming JSON saved from request step):
```bash
./scripts/refresh-access-token.sh YOUR_CLIENT_ID token.json
```
Validate token:
```bash
./scripts/validate-access-token.sh YOUR_CLIENT_ID token.json
```
Run server then query:
```bash
./scripts/run-server.sh &
curl -s -X POST http://localhost:8080/api/token -H 'Content-Type: application/json' -d '{"clientId":"client-123","deviceImei":"356544761873907"}'
```

---
## 17. Clean & Reset
```bash
mvn clean
rm -f private_key.pem public_key.pem
rm -rf target/
```
Regenerate keys & token:
```bash
./scripts/request-access-token.sh YOUR_CLIENT_ID
```

---
## 18. Minimal Checklist Before Production Release
- [ ] `mvn clean verify` passes with coverage thresholds
- [ ] SBOM files reviewed (no unexpected licenses)
- [ ] Keys rotated / fingerprint published
- [ ] Tag signed and pushed
- [ ] Artifacts verified (checksum + GPG)
- [ ] README & USAGE updated for version

---
## 4.0 Region Selection (New)
You can now set a region instead of manually typing the full base URL.
Precedence:
1. `KNOX_API_BASE_URL` (explicit full URL)
2. `KNOX_REGION` (mapped to https://<region>-api.samsungknox.com/kcs/v1)
3. Default: `us` (https://us-api.samsungknox.com/kcs/v1)

Supported short codes: `us`, `eu`, `ap` (others attempt dynamic substitution).

Examples:
```bash
KNOX_REGION=eu ./scripts/request-access-token.sh 5089824242
KNOX_REGION=ap ./scripts/enroll-device.sh 5089824242 353677431308848
# Explicit override beats region
env KNOX_REGION=eu KNOX_API_BASE_URL="https://custom-edge.example.com/kcs/v1" \
  ./scripts/request-access-token.sh 5089824242
```
Postman: set `baseUrl` to the regional endpoint directly (default updated to US). A `regionHint` variable has been added to remind formats.

---
Generated: 2025-09-14.

If you need any section added (Postman collection embedding, enrollment flow script, refresh script), ask and it will be appended here.
