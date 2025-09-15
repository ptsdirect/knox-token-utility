# SBOM & Build Provenance

This project generates a CycloneDX Software Bill of Materials (SBOM) and attaches build provenance (SLSA-style attestation) to tagged releases.

## 1. SBOM Generation (CycloneDX + SPDX)
During `mvn verify` two SBOM formats are now produced:

CycloneDX (JSON + XML):
```
target/sbom.json
target/sbom.xml
```
SPDX (JSON):
```
target/site/<artifact>.spdx.json
```
The SPDX file name includes the Maven coordinates (e.g. `com.samsung.knoxwsm_knox-token-utility-1.0.0.spdx.json`).

Command (manual regeneration):
```bash
mvn -q clean verify
```

## 2. Viewing the SBOM
Use the CycloneDX CLI (optional) or any viewer:
```bash
brew install cyclonedx/cyclonedx/cyclonedx-cli
cyclonedx validate --input-file target/sbom.json
cyclonedx diff --input-files old-sbom.json new-sbom.json
```

## 3. Release Integration
On a tagged push (`vX.Y.Z`):
- SBOM artifacts (`sbom.json`, `sbom.xml`) are uploaded and attached to the GitHub Release.
- A build provenance attestation is published (GitHub native attestation), referencing the fat JAR as the subject.

## 4. Provenance (Attestation)
The workflow step:
```yaml
- name: Generate build provenance (SLSA Attestation)
  uses: actions/attest-build-provenance@v1
  with:
    subject-path: ${{ env.JAR_PATH }}
```
Produces a DSSE-wrapped in-toto statement stored under the repo's security tab (or accessible via the API). This provides cryptographically verifiable metadata including:
- Builder (GitHub workflow)
- Source repo and commit
- Invocation parameters (limited)
- Materials (dependency digests) where available

## 5. Verifying Attestations (Preview)
Install GitHub provenance tools (when GA) or fetch via API:
```bash
gh attestation download --repo YOUR_ORG/knox-token-utility --subject "sha256:<digest>" --predicate-type slsa-provenance > provenance.intoto.jsonl
```
(Requires `gh` CLI with the attestation extension.)

## 6. Why CycloneDX & SPDX?
CycloneDX offers rich component, service, and dependency graph semantics and rapid innovation (VEX, attestation extensions). SPDX is an ISO standard broadly required in some compliance and regulatory pipelines. Emitting both maximizes interoperability with scanners, risk platforms, and policy engines.

## 7. Automated SBOM Signing
If repository secrets `GPG_PRIVATE_KEY` (ASCII-armored or base64 of exported key) and `GPG_PASSPHRASE` are configured, the release workflow will:
1. Import the GPG private key.
2. Generate detached ASCII signatures for:
  - `sbom.json` -> `sbom.json.asc`
  - `sbom.xml` -> `sbom.xml.asc`
  - `<artifact>.spdx.json` -> `<artifact>.spdx.json.asc`
3. Attach signatures to the GitHub Release.

### Verifying Signatures Locally
```bash
curl -LO https://github.com/YOUR_ORG/knox-token-utility/releases/download/v1.0.0/sbom.json
curl -LO https://github.com/YOUR_ORG/knox-token-utility/releases/download/v1.0.0/sbom.json.asc
gpg --keyserver hkps://keys.openpgp.org --recv-keys <KEYID>
gpg --verify sbom.json.asc sbom.json
```
Repeat for `sbom.xml` and the SPDX file.

### Key Distribution Recommendation
Publish the maintainer public key fingerprint in `README.md` and optionally in a DNS TXT record or Sigstore Rekor transparency log for discoverability.

## 8. Next Hardening Steps (Optional)
| Area | Action |
|------|--------|
| SBOM Integrity | Add Sigstore (cosign) signing for SBOM and JAR. |
| Multiple Formats | (DONE) SPDX via `spdx-maven-plugin`. |
| VEX | Integrate vulnerability disclosure (CycloneDX VEX) once triage process defined. |
| SLSA Level | Adopt dedicated build environment or reusable workflow to claim higher SLSA level. |
| Dependency Scanning | Add `oss-review-toolkit` or `trivy fs` scanning job. |
| License Policy | Add automated license classification & deny list check. |

## 9. Manually Signing SBOM (Optional)
After build:
```bash
gpg --armor --detach-sign target/sbom.json
```
Attach `sbom.json.asc` to the release for consumers to verify:
```bash
gpg --verify sbom.json.asc sbom.json
```

## 10. Automation Hints
To auto sign in workflow (future):
```yaml
- name: Sign SBOM
  if: startsWith(github.ref, 'refs/tags/v')
  run: gpg --armor --batch --yes --detach-sign target/sbom.json
```
(Requires imported private key & passphrase in secrets.)

## 11. Consuming the SBOM in CI
### License Policy Check
The CI workflow runs `scripts/license_policy_check.sh` against CycloneDX and (if present) SPDX outputs. Override default banned list:
```bash
BANNED_LICENSES="AGPL-3.0,SSPL-1.0,GPL-3.0-only" ./scripts/license_policy_check.sh target/sbom.json
```

### Cosign Keyless Signatures
Release workflow uses GitHub OIDC to produce `.sig` files for JAR & SBOMs without managing long-lived keys.

Verify (example for `sbom.json`):
```bash
cosign verify-blob \
  --signature sbom.json.sig \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp ".*" sbom.json
```

### Combined Verification Script
Run after downloading release artifacts:
```bash
./scripts/verify-release.sh v1.0.0 0xMAINTAINERKEY
```
Example: fail build if forbidden license appears:
```bash
jq -e '.components[] | select(.licenses[]?.license.id == "AGPL-3.0")' target/sbom.json && {
  echo "Forbidden license detected"; exit 1; } || echo "License policy OK"
```

---
Document created: 2025-09-14.
