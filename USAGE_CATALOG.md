# Usage Catalog & Web Map

A concise, high-signal index for everything you can do with the Knox Guard Token Utility. Use this to jump directly to a task.

---
## 0. Legend
- [CLI] = Run through fat JAR / scripts
- [SCRIPT] = Provided shell helper
- [LIB] = Programmatic Java API
- [HTTP] = Remote Knox endpoint
- [LOCAL] = Local demo server

---
## 1. Build & Artifacts
| Task | Command | Output |
|------|---------|--------|
| Full build & quality | `mvn -ntp clean verify` | Fat JAR + SBOM + reports |
| Fast build | `mvn -Pfast -Dfast -ntp package` | Fat JAR (no checks) |
| Low-space build | `mvn -Plowspace -Dlowspace clean verify` | Slim coverage reports |

Artifact: `target/pts-*-jar-with-dependencies.jar`

---
## 2. Environment Variables (Resolution Order)
| Purpose | Variable(s) | Notes |
|---------|-------------|-------|
| Region / Base URL | `KNOX_API_BASE_URL` > `KNOX_REGION` | Region values: `us`, `eu`, `ap` |
| API Version | `KNOX_API_VERSION` (default `v1`) | Header + path logic |
| Client Defaults | `KNOX_GUARD_CLIENT_ID`, `DEVICE_IMEI` | Used by CLI if flags omitted |
| Key Paths | `PRIVATE_KEY_PATH`, `PUBLIC_KEY_PATH` | Default `private_key.pem`, `public_key.pem` |
| Debug Stacktraces | `DEBUG` | Non-empty enables stack traces |

---
## 3. Key Management
| Action | Method |
|--------|--------|
| Generate EC key pair | `[CLI] --mode generate-keys` or script auto-run |
| Export Base64 public key | `[LIB] KnoxTokenUtility.getPublicKeyBase64(Path)` |
| Parse certificate JSON | `[LIB] KnoxCertificateParser.parse` (indirect in utility methods) |

---
## 4. JWT Creation
| JWT Type | Method / Mode | Headers | Body Claims |
|----------|---------------|---------|-------------|
| Client Identifier | `[LIB] generateSignedClientIdentifierJWT` / `[CLI] request-token/full-flow` | `typ`, ES256 | `scope`, `cdt`, subject=clientId |
| Session Token | `[LIB] generateSignedSessionTokenJWT` / `[CLI] sign-session` | `typ` | `st` |
| Access Token Wrapper | `[LIB] generateSignedAccessTokenJWT` / `[CLI] sign-access` | `typ` | `at` |
| Enrollment (quick demo) | `[LIB] createEnrollmentJwt` | `kid`, `x5c` placeholder | `imei` |

---
## 5. Access Token Lifecycle
| Stage | Call | Tool | Endpoint |
|-------|------|------|----------|
| Obtain | `requestAccessToken` | `[LIB]/[CLI]/[SCRIPT] request-access-token.sh` | `POST /ses/token` |
| Refresh | `refreshAccessToken` | `[LIB]/[SCRIPT] refresh-access-token.sh` | `POST /ses/token/refresh` |
| Validate | `validateAccessToken` | `[LIB]/[CLI] --mode validate-token / [SCRIPT] validate-access-token.sh` | `POST /ses/token/validate` |

---
## 6. Device Enrollment
| Action | Tool | Endpoint |
|--------|------|----------|
| Enroll device | `[CLI] --mode enroll-guard` / `[SCRIPT] enroll-device.sh` | `POST /kguard/devices` |

---
## 7. Scripts Index
| Script | Purpose |
|--------|---------|
| `scripts/prepare-vars.sh` | Generate client identifier JWT + Base64 public key JSON |
| `scripts/request-access-token.sh` | One-shot access token retrieval |
| `scripts/enroll-device.sh` | Access token + enrollment in one step |
| `scripts/refresh-access-token.sh` | Refresh an access token using refresh token or JSON file |
| `scripts/validate-access-token.sh` | Validate access token (HTTP status check + JSON) |
| `scripts/run-cli.sh` | Wrapper to execute fat JAR modes |
| `scripts/run-server.sh` | Launch local demo server |
| `scripts/release.sh` | Version bump & tag automation |
| `scripts/license_policy_check.sh` | License scan of SBOM |
| `scripts/update-key-metadata.sh` | Maintain key rotation metadata |
| `scripts/validate-key-metadata.sh` | Validate rotation metadata |
| `scripts/verify-release.sh` | GPG + checksum verification workflow |

---
## 8. Library API Quick Reference
| Class | Key Methods |
|-------|-------------|
| `KnoxTokenUtility` | `createSignedJWT`, `createEnrollmentJwt`, `generateSignedClientIdentifierJWT*`, `generateSignedSessionTokenJWT`, `generateSignedAccessTokenJWT`, `getPublicKeyBase64` |
| `KnoxAuthClient` | `requestAccessToken`, `refreshAccessToken`, `validateAccessToken`, `enrollDeviceInKnoxGuard` |
| `TokenClient` | CLI entry dispatch based on `--mode` |
| `Launcher` | Unified entrypoint (server vs CLI) |

---
## 9. Local Demo Server
| Feature | Details |
|---------|---------|
| Launch | `./scripts/run-server.sh` or `java -jar ... server` |
| Port | `PORT` env or first arg (default 8080) |
| Endpoint | `POST /api/token` (LOCAL) |
| Purpose | Simple JWT demo (not production) |

---
## 10. HTTP Endpoint Map (External)
```
<base>/ses/token           POST  (access token)
<base>/ses/token/refresh   POST  (refresh access token)
<base>/ses/token/validate  GET   (validate access token)
<base>/kguard/devices      POST  (device enrollment)
```
Where `<base>` =
- Explicit `KNOX_API_BASE_URL` OR
- `https://<region>-api.samsungknox.com/kcs/v1` from `KNOX_REGION` (us/eu/ap)

---
## 11. Web Map (Components & Flows)
```
+----------------------+       +------------------------------+
|  TokenClient / CLI   |       |  Knox Cloud (Regional API)   |
|  (Launcher wrapper)  |       |  us/eu/ap -api.samsungknox   |
+----------+-----------+       +---------------+--------------+
           | generate keys                     ^
           v                                    \
+----------------------+   JWT (X-SES-JWT)      |  Access/Refresh
|  KnoxTokenUtility    |------------------------+  /ses/token*
|  (sign ES256)        |                        |
+----------+-----------+                        |
           | public key (Base64)                |
           v                                    |
+----------------------+   POST enrollment      |
|  KnoxAuthClient      |------------------------> /kguard/devices
|  (OkHttp)            |   GET validate         ^
+----------+-----------+------------------------+
           |                                        
           v
+----------------------+    Local Only
| TokenServiceServer   |--> POST /api/token (demo)
+----------------------+
```
Legend: solid arrows = HTTP calls, dashed (conceptual) = data passed locally.
```

---
## 12. Typical One-Liners
| Goal | Command |
|------|---------|
| Access token (us) | `./scripts/request-access-token.sh 5089824242` |
| Access token (eu) | `KNOX_REGION=eu ./scripts/request-access-token.sh 5089824242` |
| Enroll device | `./scripts/enroll-device.sh 5089824242 353677431308848` |
| Refresh token | `./scripts/refresh-access-token.sh 5089824242 token.json` |
| Validate token | `./scripts/validate-access-token.sh 5089824242 token.json` |
| Validate via CLI | `java -jar target/pts-*-jar-with-dependencies.jar --mode validate-token --client-id 5089824242` |
| Full flow | `java -jar target/pts-*-jar-with-dependencies.jar --mode full-flow --client-id 5089824242 --device-imei 353677431308848` |
| Demo server | `./scripts/run-server.sh` |

---
## 13. Security Checklist (Abbreviated)
| Item | Check |
|------|-------|
| Private key excluded from VCS | YES |
| Region explicit or default | Confirm `KNOX_REGION`/`KNOX_API_BASE_URL` |
| JWT long-lived only for client identifier | Review `generateSignedClientIdentifierJWT` expiry |
| Enrollment uses valid IMEI | Validate format (15 digits) |
| Keys rotated periodically | Use metadata scripts |

---
## 14. Extensibility Pointers
| Add Feature | Touch Points |
|-------------|-------------|
| New endpoint | `KnoxAuthClient` + CLI mode + Postman collection |
| Different signing algo | Extend `KnoxTokenUtility` (separate method) |
| Structured logging | Introduce SLF4J JSON encoder / Logback config |
| CI attestation | Add cosign step in release workflow |

---
## 15. Troubleshooting Fast Table
| Symptom | Likely Cause | Remedy |
|---------|--------------|--------|
| 401 token request | Wrong public key or signature | Recreate key pair; confirm registration |
| 404 endpoint | Wrong base URL / region | Set `KNOX_REGION=us|eu|ap` or full base |
| Enrollment 400 | Bad IMEI | Ensure exact 15 digits |
| Refresh fails | Expired/invalid refresh token | Re-request access token |
| JShell fails to load classes | Not built fat JAR | Run build first |

---
## 16. Minimal Flow Recap
1. `./scripts/prepare-vars.sh <CLIENT_ID>` → JWT + public key
2. `./scripts/request-access-token.sh <CLIENT_ID>` → access token JSON
3. `./scripts/enroll-device.sh <CLIENT_ID> <IMEI>` → enrollment result
4. (Optional) validate: CLI `--mode validate-token` or client library

---
## 17. Where To Go Next
- Deeper doc: `USAGE.md`
- High-level: `README.md`
- Postman: `postman/` directory
- Security & rotation: scripts + forthcoming `SECURITY.md`

---
Generated: 2025-09-14. Keep this catalog lean; remove stale entries when deprecating features.
