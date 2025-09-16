# Changelog

All notable changes to this project will be documented in this file. This project adheres to Semantic Versioning.

## [1.0.3] - 2025-09-16
### Fixed
- Publish workflow now passes the actual `GPG_PASSPHRASE` secret to setup-java and Maven, enabling non-interactive artifact signing in CI.
- Maven settings template switched to use `OSSRH_TOKEN` for token-based auth.
- Confirmed `maven-gpg-plugin` loopback pinentry and `useAgent=false` to avoid interactive pinentry.

### Changed
- POM version set to `1.0.3` for a clean re-tag and deploy to Maven Central after CI fixes.

---

## [1.0.1] - 2025-09-16
### Added
- Reseller identifier (`resellerId`) optional claim support across CLI & programmatic JWT creation.
- Cloudflare Worker edge implementation (ES256 JWT endpoint parity) in `worker/`.
- Maven Central release profile (`-Prelease`) with Sonatype staging (nexus-staging-maven-plugin) and tagging support.
- CONTRIBUTING.md, expanded SECURITY.md, fingerprint publication file (`docs/KEY_FINGERPRINT.txt`).
- Selective JaCoCo instrumentation (core crypto/parsing/config classes) ensuring stable >80% instruction coverage.
- Removal scripts & documentation references for supply-chain verification (SBOM & SPDX guidance).
- `--dry-run` flag for CLI to safely preview planned network operations without performing HTTP calls.
- Loopback pinentry GPG signing configuration (maven-gpg-plugin) enabling non-interactive release builds.

### Changed
- Restricted coverage scope via `<includes>` to avoid dilution by CLI/network classes.
- README: Added selective coverage rationale + full Maven Central release workflow.
- README: Added dry-run usage documentation and GPG signing troubleshooting.
- Roadmap updated (module split consideration; removed obsolete duplication cleanup item).

### Removed
- `KnoxTokenUtility2.java` (legacy duplicate) and `TestJjwtResolve.java` (debug harness) to reduce maintenance noise.

### Security
- Reinforced artifact verification guidance (GPG + cosign + multi-channel fingerprint strategy).

### Documentation
- New CONTRIBUTING guide outlining build profiles & coverage policy.
- Expanded SECURITY policy (rotation strategy, DNS & well-known fingerprint guidance).

### Internal / Build
- Ensured javadoc & source jars attach cleanly under release profile.
- Defensive JaCoCo excludes retained for de-scoped classes (future modularization path).

### Roadmap (Forward-Looking Adjustments)
- Potential module split (`pts-core` vs `pts-http/cli`).
- Incrementally raise coverage after adding resilient HTTP client tests.
- Add automated release verification workflow & script hardening.

---

## [1.0.0] - 2025-09-14
### Added
- Core ES256 JWT generation (client identifier, session, access wrapper, enrollment JWT).
- Certificate-based signing (`certificate.json` flexible parser & key resolution).
- Access token request, refresh, validate, and device enrollment flows (OkHttp + Jackson).
- RSA small-payload encryption utility.
- Public key Base64 export with validation.
- Configuration loader with `.env` support and lazy caching.
- Custom `KnoxApiException` with suggestion hints.
- Logging via SLF4J + Logback (bridge added for Log4j to SLF4J).
- Quality gates: JaCoCo coverage thresholds, SpotBugs, Checkstyle, License plugin.
- Fat JAR build (assembly plugin).

### Changed
- Deprecated `KnoxCertificateJwtUtility` (facade retained temporarily).
- Removed deprecated `getPublicKeyFromPrivateKey`; explicit public key path now required.

### Security
- Avoid logging secrets; only hashed or truncated values where necessary.

### Documentation
- Production-focused README.
- Apache 2.0 LICENSE file added.

### Pending / Roadmap
- Remove deprecated facade in >=1.1.0.
- Add integration tests for CLI (`TokenClient`) with MockWebServer.
- Structured JSON logging profile.
- Raise coverage thresholds progressively.

---
