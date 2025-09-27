# Knox Token Utility - AI Assistant Guidelines

## Architecture Overview

This is a **dual-language** (Java 21 + Node.js) Knox Guard token lifecycle utility combining:
- **Java Core**: Production-hardened JWT crypto, HTTP clients (`KnoxAuthClient`, `KnoxGuardClient`), CLI tools
- **Node.js Scripts**: Legacy JWT generation helpers with different crypto libraries
- **Shell Automation**: Production device management workflows for bulk operations

### Key Service Boundaries

1. **Core Crypto Layer** (`KnoxTokenUtility`, `KnoxEncryptionUtility`): JWT signing with ES256, RSA encryption helpers
2. **HTTP Client Layer** (`KnoxAuthClient`, `KnoxGuardClient`): Regional Knox API communication (us/eu/ap endpoints) 
3. **CLI Layer** (`TokenClient`, `Launcher`): Unified command-line interface with 15+ modes
4. **Script Layer** (`scripts/`): Production batch workflows for device enrollment/unlock operations

## Critical Build & Environment Patterns

### Maven Multi-Profile System
```bash
# Production build (full quality gates)
mvn clean verify

# Fast iteration (skip coverage/spotbugs) 
mvn -Pfast -Dfast package

# Low-disk environments (minimal coverage reports)
mvn -Plowspace -Dlowspace verify
```

**Never ship artifacts built with `-Pfast`** - this skips security scans and coverage.

### Environment Configuration Pattern
Uses `Config.java` with cascading resolution: CLI args → env vars → defaults
```java
String clientId = firstNonBlank(cli.get("client-id"), Config.get("KNOX_GUARD_CLIENT_ID", "default"));
```
Key env vars: `KNOX_REGION` (us/eu/ap), `KNOX_GUARD_CLIENT_ID`, `KNOX_API_BASE_URL`

### Fat JAR Execution Pattern
Single entry point delegates based on first argument:
```bash
java -jar pts-*-jar-with-dependencies.jar         # → TokenClient (CLI modes)
java -jar pts-*-jar-with-dependencies.jar server  # → TokenServiceServer (HTTP)
```

## Essential Workflows

### Device Operations Script Chain
Production device management follows this pattern:
```bash
# 1. Upload IMEIs in batch
ACCESS_TOKEN=xxx ./scripts/upload-imeis-20250915.sh  

# 2. Poll upload status to completion  
UPLOAD_ID=xxx ./scripts/poll-upload.sh

# 3. Unlock enrolled devices
./scripts/unlock-imeis-20250915.sh
```

All scripts support `DRY_RUN=1` for safe preview, use different Knox API host patterns (`*-kcs-api` vs `*-api`).

### Key Generation & JWT Flow
```bash
# Auto-generates EC P-256 key pair if missing
./scripts/prepare-vars.sh CLIENT_ID

# Produces: clientId, deviceImei, publicKeyBase64, clientIdentifierJwt
# Then: request-access-token.sh → enroll-device.sh
```

### Regional API Configuration
Knox APIs use region-specific hosts. The utility handles this via:
- `KNOX_REGION=us|eu|ap` → resolves to `us-api.samsungknox.com` etc.
- Upload API uses different pattern: `us-kcs-api.samsungknox.com`
- Override with `KNOX_API_BASE_URL` for custom endpoints

## Testing & Quality Patterns

### JaCoCo Coverage Targeting
Coverage enforcement focuses **only on core crypto/config classes**, excluding CLI/network layers:
```xml
<includes>
  <include>com/samsung/knoxwsm/token/KnoxTokenUtility*</include>
  <include>com/samsung/knoxwsm/token/KnoxEncryptionUtility*</include>
  <!-- CLI/HTTP clients excluded from coverage to avoid dilution -->
</includes>
```

### Test Structure  
- `*Test.java`: Core unit tests with Mockito
- `*UnlockTest.java`: Device operation workflow tests
- `MockWebServer` usage: Tests HTTP clients without external dependencies

## Security & Compliance Patterns

### Dual SBOM Generation
Build produces both CycloneDX and SPDX formats:
```bash
mvn verify  # → target/sbom.json + target/site/*.spdx.json
./scripts/license_policy_check.sh  # Scans for forbidden licenses (AGPL, SSPL)
```

### GPG Signing & Verification
Production releases include detached signatures:
```bash
./scripts/verify-release.sh  # End-to-end artifact verification
gpg --verify pts-*-jar-with-dependencies.jar.asc pts-*-jar-with-dependencies.jar
```

### Key Rotation Metadata
Structured key management in `docs/key-metadata.json`:
```bash
./scripts/update-key-metadata.sh NEWFPR ed25519 signing  # Rotate keys
./scripts/validate-key-metadata.sh  # CI validation
```

## Development Anti-Patterns

### Avoid
- **Direct Knox API calls** without regional host logic - use `KnoxAuthClient`/`KnoxGuardClient`
- **Hardcoded "us" region** - always respect `KNOX_REGION` env var
- **Mixing Java crypto with Node.js scripts** - prefer Java `KnoxTokenUtility` for production
- **CLI mode testing without scripts** - use `./scripts/run-cli.sh` wrapper
- **Coverage failures on large files** - use `-Plowspace` for disk-constrained environments

### Directory Conventions
- `src/main/java/com/samsung/knoxwsm/token/`: Core library classes
- `scripts/`: Production-ready bash automation (env-aware, dry-run capable)
- `postman/`: API collection with environment template
- `docs/`: Security metadata, key fingerprints, SBOM documentation

## Integration Points

Knox Guard API requires certificate chain (`x5c`) headers for production JWTs. Development uses placeholder certificates via `createEnrollmentJwt()` - replace with proper certificate parsing for production.

Regional endpoints auto-resolve, but upload operations use distinct host patterns. The HTTP clients encapsulate this complexity - don't bypass them for direct HTTP calls.