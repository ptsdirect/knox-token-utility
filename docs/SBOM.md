# SBOM & Build Provenance

This project generates a CycloneDX Software Bill of Materials (SBOM) and attaches build provenance (SLSA-style attestation) to tagged releases.

## 1. SBOM Generation
The CycloneDX Maven plugin runs during `verify` and produces both JSON and XML:

Generated files:
```
 target/sbom.json
 target/sbom.xml
```

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

## 6. Why CycloneDX?
CycloneDX is widely adopted, supports license, vulnerability, and dependency graph analysis, and is a CNCF/JTC1 recognized SBOM format. SPDX can be added later if needed for ecosystems requiring SPDX tag/value or JSON.

## 7. Next Hardening Steps (Optional)
| Area | Action |
|------|--------|
| SBOM Integrity | Sign SBOM file with GPG and attach signature (`sbom.json.asc`). |
| Multiple Formats | Add SPDX via `spdx-maven-plugin`. |
| VEX | Integrate vulnerability disclosure (CycloneDX VEX) once triage process defined. |
| SLSA Level | Adopt dedicated build environment or reusable workflow to claim higher SLSA level. |
| Dependency Scanning | Add `oss-review-toolkit` or `trivy fs` scanning job. |
| License Policy | Add automated license classification & deny list check. |

## 8. Manually Signing SBOM (Optional)
After build:
```bash
gpg --armor --detach-sign target/sbom.json
```
Attach `sbom.json.asc` to the release for consumers to verify:
```bash
gpg --verify sbom.json.asc sbom.json
```

## 9. Automation Hints
To auto sign in workflow (future):
```yaml
- name: Sign SBOM
  if: startsWith(github.ref, 'refs/tags/v')
  run: gpg --armor --batch --yes --detach-sign target/sbom.json
```
(Requires imported private key & passphrase in secrets.)

## 10. Consuming the SBOM in CI
Example: fail build if forbidden license appears:
```bash
jq -e '.components[] | select(.licenses[]?.license.id == "AGPL-3.0")' target/sbom.json && {
  echo "Forbidden license detected"; exit 1; } || echo "License policy OK"
```

---
Document created: 2025-09-14.
