<!-- Production Readiness Consolidated README (2025-09-14) -->

# Knox Guard Token Utility (Java 21) – Coordinates: `com.mdttee.knox:pts`

![CI](https://github.com/ptsdirect/knox-token-utility/actions/workflows/ci.yml/badge.svg)
![Coverage](https://codecov.io/gh/ptsdirect/knox-token-utility/branch/main/graph/badge.svg)
![Release](https://img.shields.io/maven-central/v/com.mdttee.knox/pts?label=Maven%20Central)

Apache 2.0 licensed CLI + library for Samsung Knox Guard token flows: ES256 JWT generation, certificate-based signing, access token lifecycle, device enrollment scaffolding, and supporting crypto helpers. Production hardened with coverage, static analysis, and license enforcement.

> For a compact task-focused index, see `USAGE_CATALOG.md` (flow map, endpoint table, one-liners).

## Quick Start (Unified Launcher)

Build (fat jar):
```bash
mvn -q -ntp clean package -Dgpg.skip=true
```

Run CLI help (delegates to `TokenClient`):
```bash
java -jar target/pts-1.0.0-jar-with-dependencies.jar --help
```

Generate keys (creates `private_key.pem` + `public_key.pem` if missing):
```bash
java -jar target/pts-1.0.0-jar-with-dependencies.jar --mode generate-keys
```

Request access token (needs real registered client ID + matching public key at Knox):
```bash
export KNOX_GUARD_CLIENT_ID="YOUR_CLIENT_ID"
java -jar target/pts-1.0.0-jar-with-dependencies.jar --mode request-token --output-json
```

Start lightweight HTTP server (provides `/api/token` -> returns a client identifier JWT for experimentation):
```bash
java -jar target/pts-1.0.0-jar-with-dependencies.jar server
```

Then in another terminal:
```bash
curl -s -X POST http://localhost:8080/api/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"client-123","deviceImei":"356544761873907"}'
```

Wrapper scripts:
```bash
./scripts/run-cli.sh --mode request-token
./scripts/run-server.sh                # starts on PORT=8080
PORT=9090 ./scripts/run-server.sh      # custom port
```

JShell quick usage (after build):
```bash
jshell --class-path target/pts-1.0.0-jar-with-dependencies.jar
jshell> import java.nio.file.*;import com.samsung.knoxwsm.token.*;
jshell> var pem = Files.readString(Path.of("private_key.pem"));
jshell> var util = new KnoxTokenUtility();
jshell> var jwt = util.createEnrollmentJwt("client-123","356544761873907", pem);
jshell> System.out.println(jwt);
```

NOTE: The quick helper `createEnrollmentJwt` uses a placeholder `x5c` header and is not suitable for production token issuance; use the standard flow via `TokenClient` for real Knox interactions.

## Docker Usage

Build image (runs full verify):
```bash
docker build -t knox-token-util .
```

Fast build (skip tests):
```bash
docker build -t knox-token-util --build-arg SKIP_TESTS=true .
```

Show CLI help:
```bash
docker run --rm knox-token-util --help
```

Generate keys & request token (mount a volume to persist keys):
```bash
docker run --rm -v "$PWD/data:/data" -w /data \
  -e KNOX_GUARD_CLIENT_ID=YOUR_CLIENT_ID \
  knox-token-util --mode generate-keys

docker run --rm -v "$PWD/data:/data" -w /data \
  -e KNOX_GUARD_CLIENT_ID=YOUR_CLIENT_ID \
  knox-token-util --mode request-token --output-json
```

Run HTTP server (port 8080):
```bash
docker run --rm -p 8080:8080 -v "$PWD/data:/data" -w /data knox-token-util server
```

Test endpoint:
```bash
curl -s -X POST http://localhost:8080/api/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"client-123","deviceImei":"356544761873907"}'
```

Custom port:
```bash
docker run --rm -e PORT=9090 -p 9090:9090 knox-token-util server
```

Security note: Mount secrets read-only, prefer injecting keys via secret management in real deployments.

## Feature Matrix

| Domain | Capability |
|--------|-----------|
| JWT | Client Identifier, Session, Access wrapper, Device Enrollment (legacy) |
| Keys | EC P-256 PKCS#8 private key parsing; public key export (Base64 DER) |
| Certificate | `certificate.json` flexible field parser + consolidated signing methods |
| HTTP | Access, refresh, validate, enroll (OkHttp + Jackson) |
| Guard Ops | Batch upload, unlock, combined enroll→unlock helper (scripts + API) |
| Crypto | RSA small-payload encryption utility |
| Versioning | `X-KNOX-API-VERSION` header + path normalization groundwork |
| Quality | JaCoCo thresholds, SpotBugs, Checkstyle, License plugin |
| Packaging | Fat JAR via maven-assembly-plugin |

## Build
```bash
mvn -q clean verify
```
Artifact (fat JAR): `target/pts-1.0.0-jar-with-dependencies.jar`

### Fast Build (Low Disk / Iteration)
Skips tests, coverage, SpotBugs, and Checkstyle:
```bash
mvn -Pfast -Dfast -q clean package
```
Only use for rapid local iteration or when disk pressure prevents full verify. Always run the full `clean verify` before releasing.

### Low-Space Build (Keep Tests + Minimal Coverage)
Runs tests and enforces coverage, but trims report size (XML only) and skips site/report plugins:
```bash
mvn -Plowspace -Dlowspace clean verify
```
Produces minimal JaCoCo output under `target/jacoco-min*`.

### Disk Cleanup Helper (macOS)
Interactive script (prompts before each action) to reclaim space commonly blocking coverage report generation:
```bash
scripts/disk_cleanup_mac.sh
```
Review the script before running; it targets DerivedData, simulator caches, npm cache, brew cleanup, stale Maven artifacts, user caches, optional Docker prune.

## CLI Usage
```bash
java -jar target/pts-1.0.0-jar-with-dependencies.jar --help
```
Representative modes: `sign-client-idp`, `sign-session`, `sign-access`, `export-public`, `encrypt`, `request-token`, `validate-token`, `enroll-device`.

## Device Operations (Scripts Overview)

Three helper scripts are provided for Knox Guard device lifecycle flows. All require a valid access token (export `ACCESS_TOKEN`). Region auto-resolves from `KNOX_REGION` (`us` default) but can be overridden with `KNOX_API_BASE_URL`.

| Script | Purpose | Required Env | Optional Env | Notes |
|--------|---------|--------------|--------------|-------|
| `scripts/upload-imeis-20250915.sh` | Batch upload IMEIs via Upload API (`/devices/uploads`) | `ACCESS_TOKEN` | `IMEI_FILE` (default `data/imeis-20250915.txt`), `POLICY_FLAGS_JSON`, `KNOX_REGION`, `KNOX_API_BASE_URL`, `DRY_RUN` | Uses functional base host (`*-kcs-api`). Prints payload in DRY_RUN. |
| `scripts/unlock-imeis-20250915.sh` | Sequential unlock of previously enrolled devices | `ACCESS_TOKEN` | `IMEI_FILE`, `KNOX_REGION`, `KNOX_API_BASE_URL`, `KNOX_GUARD_UNLOCK_PATH`, `DRY_RUN`, `SLEEP_MS` | Unlock body: `{ "deviceId":"IMEI","action":"unlock" }`. Aggregates failures. |
| `scripts/enroll-and-unlock.sh` | Enroll a single device then attempt immediate unlock | `ACCESS_TOKEN`, `CLIENT_ID`, `DEVICE_IMEI` | `KNOX_REGION`, `KNOX_API_BASE_URL`, `KNOX_GUARD_UNLOCK_PATH`, `DRY_RUN`, `SLEEP_AFTER_ENROLL_MS`, `VERBOSE` | Returns minimal JSON `{enrollmentCode,unlockCode}`. |
| `scripts/poll-upload.sh` | Poll a batch upload until terminal state | `ACCESS_TOKEN`, `UPLOAD_ID` | `KNOX_REGION`, `KNOX_API_BASE_URL`, `INTERVAL_SEC`, `TIMEOUT_SEC`, `DRY_RUN`, `VERBOSE` | Exits 0 on terminal state, 2 on timeout; prints final JSON body. |
| `scripts/orchestrate-batch.sh` | One-shot upload → poll → unlock pipeline | `ACCESS_TOKEN` | `IMEI_FILE`, `POLL_INTERVAL_SEC`, `POLL_TIMEOUT_SEC`, `POLICY_FLAGS_JSON`, `NO_UNLOCK`, `DRY_RUN`, `VERBOSE`, region/base overrides | Emits `{uploadId,status}` JSON on success; halts on first failed phase. |

### Examples
Dry run batch upload of 5 test IMEIs:
```bash
IMEI_FILE=data/imeis-5-test-20250915.txt ACCESS_TOKEN=dummy DRY_RUN=1 ./scripts/upload-imeis-20250915.sh
```

Real upload + capture response:
```bash
export ACCESS_TOKEN="REAL_ACCESS_TOKEN"
IMEI_FILE=data/imeis-5-test-20250915.txt ./scripts/upload-imeis-20250915.sh > upload-response.json
```

Batch unlock after processing delay:
```bash
sleep 90
IMEI_FILE=data/imeis-5-test-20250915.txt ./scripts/unlock-imeis-20250915.sh | tee unlock-log.txt
```

Poll an upload until completion:
```bash
export ACCESS_TOKEN="REAL_ACCESS_TOKEN"
export UPLOAD_ID="<uploadId-from-upload-response>"
./scripts/poll-upload.sh
```

End‑to‑end (single command) upload → poll → unlock:
```bash
export ACCESS_TOKEN="REAL_ACCESS_TOKEN"
IMEI_FILE=data/imeis-5-test-20250915.txt ./scripts/orchestrate-batch.sh
```

Dry run orchestrator (no network calls):
```bash
ACCESS_TOKEN=dummy IMEI_FILE=data/imeis-5-test-20250915.txt DRY_RUN=1 ./scripts/orchestrate-batch.sh
```

Skip unlock phase (only upload + poll):
```bash
export ACCESS_TOKEN="REAL_ACCESS_TOKEN"
NO_UNLOCK=1 IMEI_FILE=data/imeis-5-test-20250915.txt ./scripts/orchestrate-batch.sh
```

Single enroll→unlock flow:
```bash
export ACCESS_TOKEN="REAL_ACCESS_TOKEN"
export CLIENT_ID="YOUR_CLIENT_ID"
DEVICE_IMEI=359881234567890 ./scripts/enroll-and-unlock.sh
```

Dry run enroll→unlock:
```bash
DEVICE_IMEI=359881234567890 CLIENT_ID=client-123 ACCESS_TOKEN=dummy DRY_RUN=1 ./scripts/enroll-and-unlock.sh
```

### When to Use Which
| Scenario | Use |
|----------|-----|
| Initialize large fleet | `upload-imeis-...` |
| Periodic operational unlock wave | `unlock-imeis-...` |
| Test a single device lifecycle or debug timing | `enroll-and-unlock.sh` |

### Notes
1. Upload API uses a different host pattern (`us-kcs-api`) vs auth/enroll/unlock (`us-api`). Scripts encapsulate this.
2. If unlock returns 404 or 403 immediately after enroll, introduce or increase `SLEEP_AFTER_ENROLL_MS`.
3. Provide `POLICY_FLAGS_JSON` like `'{"autoAccept":true}'` to influence server handling (if supported by your account).
4. `DRY_RUN=1` avoids network calls—safe for previewing payloads.

### Test Coverage Additions
`KnoxAuthClientUnlockTest` exercises: standalone unlock success + error, combined enroll+unlock success, and unlock failure path. Total test suite now includes unlock path assertions ensuring regression detection.

## certificate.json Example
```json
{
  "clientId": "YOUR_CLIENT_ID",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"
}
```

## Maven / Gradle Dependency
Add to `pom.xml`:
```xml
<dependency>
  <groupId>com.mdttee.knox</groupId>
  <artifactId>pts</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle (Groovy DSL):
```gradle
implementation 'com.mdttee.knox:pts:1.0.0'
```

Gradle (Kotlin DSL):
```kotlin
implementation("com.mdttee.knox:pts:1.0.0")
```

## Programmatic Snippet
```java
try (InputStream pk = Files.newInputStream(Path.of("private_key.pem"))) {
    String jwt = KnoxTokenUtility.generateSignedClientIdentifierJWT(pk, "YOUR_CLIENT_ID", null);
    String pub = KnoxTokenUtility.getPublicKeyBase64(Path.of("public_key.pem"));
    KnoxAuthClient client = new KnoxAuthClient();
    Map<String,Object> token = client.requestAccessToken(pub, jwt, 30);
    System.out.println(token.get("accessToken"));
}
```

## Environment (.env Supported)
`KNOX_BASE_URL`, `KNOX_CLIENT_ID`, `KNOX_CLIENT_PASSWORD`, `KNOX_API_VERSION`, etc. Quotes are stripped; values cached after first read.

## Security
- Keep private keys & certificate JSON out of VCS.
- Long-lived JWT (10y) only for client identifier flows; shorten if policy requires.
- RSA helper is for very small secrets only.

## Quality Gates (Current)
- Instruction Coverage: > 80%
- Branch Coverage: > 60%
- License headers verified
- SpotBugs: non-fatal reporting

## Deprecations
`KnoxCertificateJwtUtility` is deprecated and delegates to `KnoxTokenUtility`; remove after migration (planned for >=1.1.0). The legacy helper `getPublicKeyFromPrivateKey` has been removed—always supply an explicit public key path and use `KnoxTokenUtility.getPublicKeyBase64(Path)`.

## Roadmap
- Remove deprecated facade
- Add stable TokenClient integration tests (MockWebServer)
- Elevate coverage thresholds
- Structured JSON logging profile
- Polling script for upload status (planned)
- Consolidate `KnoxTokenUtility2` back into `KnoxTokenUtility` after stale class root cause resolved
- Formal CHANGELOG & release automation

## License
Apache License 2.0. See headers and forthcoming `LICENSE` file if distributing externally.

## Continuous Integration & Coverage
| Aspect | Details |
|--------|---------|
| CI Workflow | `.github/workflows/ci.yml` triggers on PRs, pushes, and `v*.*.*` tags |
| JDK | Temurin 21 |
| Quality Gates | `mvn clean verify` (JaCoCo thresholds, SpotBugs, Checkstyle, license) |
| Artifacts | Fat JAR (tag builds), Surefire & JaCoCo reports uploaded |
| Coverage Upload | Optional Codecov (set `CODECOV_TOKEN` secret) |

### Enabling Codecov
1. Create project in Codecov (GitHub app or token).
2. Add repo secret `CODECOV_TOKEN`.
3. Merge a change to main; badge will update.

### Automated Release (Tag-Based)
Push an annotated tag `vX.Y.Z` and CI will build & attach the fat JAR as artifact. Optional extended release automation can be added if needed.

Reference: See `docs/GPG_SIGNING.md` to enable GPG-signed commits and tags.

### Domain Verification (Maven Central)
If using the new Maven Central Portal, add the provided TXT record (example):
```
_central-portal.mdttee.com  TXT  central-portal-verification=XXXXXXXXXXXXXXXX
```
Verify:
```bash
dig +short TXT _central-portal.mdttee.com
```
Legacy OSSRH style (if requested):
```
_sonatype.mdttee.com  TXT  sonatype-site-verification=YYYYYYYYYYYYYYYY
```

## SBOM, SPDX & Build Provenance
During `mvn verify` the build emits:
| Format | Files |
|--------|-------|
| CycloneDX | `target/sbom.json`, `target/sbom.xml` |
| SPDX | `target/site/<group_artifact_version>.spdx.json` |

On tagged releases these SBOMs are attached along with:
| Integrity Artifact | Description |
|--------------------|-------------|
| SHA256SUMS | Digest of the fat JAR |
| Build Provenance | GitHub SLSA-style attestation for the fat JAR |
| *.asc (conditional) | GPG detached signatures for each SBOM (if GPG secrets configured) |

Quick local check:
```bash
mvn -q clean verify
jq '.components | length' target/sbom.json
jq '.packages | length' target/site/*.spdx.json 2>/dev/null || echo 'SPDX file not found'
```

Signature verification (example):
```bash
gpg --verify sbom.json.asc sbom.json
```

See `docs/SBOM.md` for details on dual-format rationale, signing, and future hardening (VEX, license policy, Sigstore).

## Security Scanning & License Policy
| Aspect | Implementation |
|--------|---------------|
| Vulnerability Scan | Grype (GitHub Action) generates SARIF (non-fatal) |
| License Policy | `scripts/license_policy_check.sh` inspects CycloneDX + SPDX for banned IDs |
| Forbidden Licenses (default) | `AGPL-3.0`, `SSPL-1.0` (override via `BANNED_LICENSES` env) |

Run locally after build:
```bash
./scripts/license_policy_check.sh target/sbom.json
```

## Artifact Integrity & Verification
| Layer | Tools |
|-------|-------|
| Checksums | `SHA256SUMS` |
| GPG Signatures | `.asc` for SBOMs (conditional) |
| Cosign Keyless | `.sig` for JAR + SBOMs (OIDC-backed) |
| SLSA Provenance | GitHub provenance attestation |
| GPG JAR Signature | `<fat-jar>.asc` detached signature (new) |

Quick cosign verification (after downloading artifacts):
```bash
cosign verify-blob \
  --signature sbom.json.sig \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp ".*" sbom.json
```

### GPG JAR Verification
Download the JAR and its `.asc` plus `KEY_FINGERPRINT` file:
```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys <MAINTAINER_KEY_ID>
cat KEY_FINGERPRINT
gpg --verify pts-<version>-jar-with-dependencies.jar.asc \
  pts-<version>-jar-with-dependencies.jar
```
Match the printed fingerprint to `KEY_FINGERPRINT` (and optionally a value published out-of-band in SECURITY.md).

See `SECURITY.md` and `scripts/verify-release.sh` for end-to-end verification workflow.

### Public Fingerprint Publication
To enable out-of-band trust the maintainer fingerprint is published via multiple channels:
| Channel | Location |
|---------|----------|
| Release Asset | `KEY_FINGERPRINT` |
| Repository | `docs/KEY_FINGERPRINT.txt` |
| Well-Known Path | `docs/.well-known/openpgp-fingerprint` (served if docs site hosted) |
| (Recommended) DNS TXT | `_openpgpkey.<domain>` or custom TXT record |

#### DNS TXT Example
```
_openpgpkey.example.org. 3600 IN TXT "v=OpenPGP; fpr=<REPLACE_WITH_ACTUAL_FPR>"
```
Alternative generic TXT:
```
maintainer-fpr.example.org. 3600 IN TXT "gpg-fingerprint=<REPLACE_WITH_ACTUAL_FPR>"
```

#### Fetch Well-Known (if hosted)
```bash
curl -s https://example.org/.well-known/openpgp-fingerprint
```

Always compare at least two independent sources (e.g., DNS + release asset).

DNSSEC (if domain signed):
```bash
dig +dnssec TXT _openpgpkey.example.org | grep -i ad
./scripts/verify-fingerprint-dns.sh _openpgpkey.example.org <FPR>
```

CI optional validation: set secrets `DNS_FPR_FQDN` and `DNS_FPR_EXPECTED` to have the release workflow perform a non-fatal DNS fingerprint check.

### Key Rotation Overview
Structured metadata file: `docs/key-metadata.json`
Scripts:
```bash
# Rotate to new key
./scripts/update-key-metadata.sh NEWFPR ed25519 signing

# Validate metadata vs signing key (run in CI)
./scripts/validate-key-metadata.sh
```
Policy: annual rotation + 30-day overlap (details in `SECURITY.md`).

## Publishing / Releasing
Two approaches:

### 1. Manual (First Time)
```bash
git remote add origin git@github.com:YOUR_ORG/knox-token-utility.git
git push -u origin main
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```
This triggers the tag workflow and produces a GitHub Release with artifacts.

### 2. Automated Version Bump Script
Use the helper script to bump version, tag, and push:
```bash
./scripts/release.sh 1.0.1
```
Flags:
- `--dry-run` show actions only
- `--no-push` create commit+tag locally without pushing
- `--sign` GPG-sign annotated tag (requires configured GPG)
- `--main-branch=branchName` if not `main`

Example signed release:
```bash
./scripts/release.sh 1.1.0 --sign
```

### After Tag Push
- CI builds & uploads fat JAR
- Release workflow attaches artifacts & checksums
- (Optional) Coverage re-upload updates badge

### Preparing Next Development Iteration
After releasing 1.0.1 you may set a snapshot:
```bash
mvn versions:set -DnewVersion=1.0.2-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "chore: start 1.0.2-SNAPSHOT"
git push origin main
```

## Maintenance: Low Disk Advanced Strategies

If coverage reports still fail due to space:
1. Remove stale Xcode simulator runtimes (Xcode > Settings > Platforms).
2. Clear DerivedData: `rm -rf ~/Library/Developer/Xcode/DerivedData/*`.
3. Prune Homebrew: `brew cleanup -s`.
4. Trim Maven repo (last-access > 30 days):
  ```bash
  find ~/.m2/repository -type f -atime +30 -delete
  ```
5. Relocate Maven repository to external volume:
  ```bash
  mv ~/.m2 /Volumes/ExternalDisk/.m2
  ln -s /Volumes/ExternalDisk/.m2 ~/.m2
  ```
  Or set a custom path in `~/.mavenrc`:
  ```bash
  export MAVEN_OPTS="-Dmaven.repo.local=/Volumes/ExternalDisk/m2repo"
  ```
6. Use low-space profile first: `mvn -Plowspace -Dlowspace clean verify` before a full run.

Never publish a release built only with `-Pfast`.

## Unified Detailed Usage & Postman

A fully consolidated operational, scripting, Docker, security, troubleshooting and release guide now lives in `USAGE.md` (single file reference you requested).

### Postman Collection
Import these two files from the `postman/` directory:
- `postman/KnoxTokenUtility.postman_collection.json`
- `postman/KnoxTokenUtilityEnvironment.postman_environment.json`

Steps:
1. Run `./scripts/prepare-vars.sh YOUR_CLIENT_ID` to generate `publicKeyBase64` + `clientIdentifierJwt`.
2. Copy the values into the Postman environment (`publicKeyBase64`, `clientIdentifierJwt`).
3. Use "Request Access Token" → copy `accessToken` + `refreshToken` back into environment.
4. Call "Validate Access Token", "Enroll Device", or "Refresh Access Token".
5. Run local demo server (`./scripts/run-server.sh`) and use the "Local Demo: /api/token" request (environment var `localPort`).

Environment Variables (Postman):
| Key | Purpose |
|-----|---------|
| `baseUrl` | API base (`https://api.samsungknox.com/kcs/v1`) |
| `apiVersion` | API version header (default `v1`) |
| `clientId` | Knox Guard client ID |
| `deviceImei` | Target device IMEI |
| `publicKeyBase64` | Derived from `public_key.pem` (DER Base64) |
| `clientIdentifierJwt` | JWT created via script or CLI |
| `accessToken` | Filled after first token request |
| `refreshToken` | Filled after first token request (for refresh) |
| `accessTokenValidity` | Minutes (15–60) |
| `localPort` | Port for local demo server (`8080` default) |

For full end-to-end command examples (including release verification, SBOM checks, key rotation, Docker usage) see `USAGE.md`.

---
Generated during production hardening phase (2025-09-14).
