# Changelog

All notable changes to this project will be documented in this file. This project adheres to Semantic Versioning.

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
