<!-- Production Readiness Consolidated README (2025-09-14) -->

# Knox Guard Token Utility (Java 21)

![CI](https://github.com/YOUR_ORG/knox-token-utility/actions/workflows/ci.yml/badge.svg)
![Coverage](https://codecov.io/gh/YOUR_ORG/knox-token-utility/branch/main/graph/badge.svg)
![Release](https://img.shields.io/github/v/release/YOUR_ORG/knox-token-utility?sort=semver)

Apache 2.0 licensed CLI + library for Samsung Knox Guard token flows: ES256 JWT generation, certificate-based signing, access token lifecycle, device enrollment scaffolding, and supporting crypto helpers. Production hardened with coverage, static analysis, and license enforcement.

## Feature Matrix

| Domain | Capability |
|--------|-----------|
| JWT | Client Identifier, Session, Access wrapper, Device Enrollment (legacy) |
| Keys | EC P-256 PKCS#8 private key parsing; public key export (Base64 DER) |
| Certificate | `certificate.json` flexible field parser + consolidated signing methods |
| HTTP | Access, refresh, validate, enroll (OkHttp + Jackson) |
| Crypto | RSA small-payload encryption utility |
| Versioning | `X-KNOX-API-VERSION` header + path normalization groundwork |
| Quality | JaCoCo thresholds, SpotBugs, Checkstyle, License plugin |
| Packaging | Fat JAR via maven-assembly-plugin |

## Build
```bash
mvn -q clean verify
```
Artifact: `target/knox-token-utility-1.0.0-jar-with-dependencies.jar`

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
java -jar target/knox-token-utility-1.0.0-jar-with-dependencies.jar --help
```
Representative modes: `sign-client-idp`, `sign-session`, `sign-access`, `export-public`, `encrypt`, `request-token`, `validate-token`, `enroll-device`.

## certificate.json Example
```json
{
  "clientId": "YOUR_CLIENT_ID",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"
}
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

---
Generated during production hardening phase (2025-09-14).
