# Security Policy

## Supported Versions
We currently support the latest released minor version. Patch releases should be used for any security-related fixes.

## Reporting a Vulnerability
Please **do not** open a public issue for sensitive security reports.

1. Email: security@example.org (replace with real contact)
2. Provide:
   - Affected version (tag / commit)
   - Reproduction steps or PoC
   - Impact assessment (confidentiality/integrity/availability)
3. You should receive acknowledgement within 3 business days.

If no response in 7 days, you may escalate by opening a minimal public issue referencing an unanswered security email.

## Coordinated Disclosure
We follow a 90-day coordinated disclosure window unless actively exploited in the wild, in which case we may accelerate release.

## Artifact Integrity & Verification
Each release provides multiple integrity layers:

| Mechanism | Purpose |
|-----------|---------|
| SHA256SUMS | Basic checksum of fat JAR |
| GPG Signatures (`*.asc`) | Authenticity of SBOMs and fat JAR |
| Cosign Signatures (`*.sig`) | Keyless OIDC-backed signatures for JAR & SBOMs |
| SLSA Build Provenance | Verifiable build metadata linking source commit to artifact |

### GPG Verification
```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys <MAINTAINER_KEYID>
gpg --verify sbom.json.asc sbom.json
gpg --verify knox-token-utility-<ver>-jar-with-dependencies.jar.asc \
  knox-token-utility-<ver>-jar-with-dependencies.jar
```

Fingerprint publication:
1. `KEY_FINGERPRINT` file attached to release.
2. Same fingerprint documented here (placeholder):
```
<MAINTAINER_FINGERPRINT_PLACEHOLDER>
```
3. (Recommended) Publish fingerprint via DNS TXT or project website for out-of-band trust.
4. (Optional) Serve `.well-known/openpgp-fingerprint` on project domain.

### DNS TXT Record Formats
OpenPGP standardized style:
```
_openpgpkey.example.org. 3600 IN TXT "v=OpenPGP; fpr=<REPLACE_WITH_ACTUAL_FPR>"
```

Generic label variant:
```
maintainer-fpr.example.org. 3600 IN TXT "gpg-fingerprint=<REPLACE_WITH_ACTUAL_FPR>"
```

Verification:
```bash
dig +short TXT _openpgpkey.example.org
curl -s https://example.org/.well-known/openpgp-fingerprint
```
Compare outputs to release `KEY_FINGERPRINT`.

### Cosign Verification (Keyless)
```bash
cosign verify-blob \
  --signature sbom.json.sig \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp ".*" sbom.json
```

## SBOM & License Policy
- CycloneDX + SPDX SBOMs generated during build.
- CI runs a license policy check blocking (or warning on) forbidden licenses configured via `BANNED_LICENSES` variable.

## Vulnerability Scanning
A Grype-based scan runs in CI (non-fatal). We may tighten policy over time (e.g., fail on critical severities).

## Cryptographic Practices
- EC P-256 (ES256) for JWT signatures.
- Detached signatures for SBOMs (GPG) and transparent keyless signatures (cosign) for higher supply chain trust.

## Future Enhancements
| Area | Planned Action |
|------|----------------|
| JAR GPG Sign | Add detached signature for fat JAR |
| Sigstore Bundle | Include certificate + transparency bundle uploads |
| VEX | Provide vulnerability status documents alongside SBOM |
| Dependency Attestations | Add attestations for dependency freshness and license compliance |

## Contact
security@example.org (placeholder — replace with project security contact)

## DNSSEC Considerations
Publishing the fingerprint via DNS gains additional authenticity when the domain is DNSSEC-signed and the resolver validates signatures.

### Verification Flow
1. Query TXT record with DNSSEC:
```bash
dig +dnssec TXT _openpgpkey.example.org
```
2. Ensure the flags line includes `ad` (Authenticated Data). Absence of `ad` may mean your resolver doesn’t validate DNSSEC.
3. (Optional) Use `delv` for end-to-end validation bypassing the stub resolver:
```bash
delv _openpgpkey.example.org TXT
```
4. Compare extracted `fpr=` value with release `KEY_FINGERPRINT` and repository copies.

### Automated Check
The helper script:
```bash
./scripts/verify-fingerprint-dns.sh _openpgpkey.example.org ABCDEF1234567890ABCDEF1234567890ABCDEF12
```
Returns non-zero exit if fingerprint mismatches or missing.

### CI Automation (Optional)
Set repository secrets to enable release workflow DNS verification step:
| Secret | Purpose |
|--------|---------|
| `DNS_FPR_FQDN` | Fully-qualified domain name of fingerprint TXT record (e.g. `_openpgpkey.example.org`) |
| `DNS_FPR_EXPECTED` | Expected uppercase hex fingerprint (no spaces) |

If both are present the release workflow runs `verify-fingerprint-dns.sh` and logs a warning (non-fatal) on mismatch.

### Threat Model Notes
| Risk | Mitigation |
|------|------------|
| Resolver tampering | DNSSEC AD flag + independent `delv` query |
| Single-channel compromise | Cross-validate (DNS + release asset + repo + well-known) |
| Key rotation confusion | Publish new + old for overlap, add `validFrom` in SECURITY.md |

## Key Rotation Policy
We maintain structured metadata in `docs/key-metadata.json` capturing current and previous signing key fingerprints.

### Policy Parameters
| Parameter | Value (default) | Rationale |
|-----------|-----------------|-----------|
| Rotation Interval | 365 days | Annual hygiene |
| Overlap Window | 30 days | Allow ecosystem to trust both keys |
| Compromise Response | Immediate revoke + emergency rotation | Minimize blast radius |

### Rotation Steps
1. Generate new key & export armored private/public.
2. Add new public key to publication channels (DNS TXT, docs, well-known) in pending state.
3. Run:
```bash
./scripts/update-key-metadata.sh NEWFINGERPRINT ed25519 signing
```
4. Commit updated `docs/key-metadata.json` and updated fingerprint files.
5. Release a new version; both old and new signatures appear during overlap.
6. After overlap period, update metadata: set `overlapUntil` for retired key and optionally mark `revoked` if decommissioned.
7. (If compromise) Immediately mark `revoked": true` on affected key and remove from publication channels.

### Validation
CI release workflow runs `scripts/validate-key-metadata.sh` (non-fatal). To enforce strictness, make the step fail on mismatch.

### Future Enhancements
| Idea | Benefit |
|------|--------|
| Signed key-metadata JSON | Tamper evidence |
| Rekor inclusion of metadata hash | Transparency & auditability |
| Automatic overlap expiry PR | Ensures timely cleanup |
