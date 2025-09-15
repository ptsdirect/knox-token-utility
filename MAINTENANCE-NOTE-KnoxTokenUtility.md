# Maintenance Note: KnoxTokenUtility Stale Class Artifact

## Summary
The original `KnoxTokenUtility` source (`src/main/java/com/samsung/knoxwsm/token/KnoxTokenUtility.java`) stopped reflecting changes in the produced class file (`target/classes/.../KnoxTokenUtility.class`). Disassembly after multiple clean builds showed an *old* method surface (no `createEnrollmentJwt`, presence of legacy `getPublicKeyFromPrivateKey`). Direct edits (adding a marker constant) did not propagate to the compiled bytecode, demonstrating a stale or mis-linked source situation isolated to that class.

## Likely Causes (Hypotheses)
- Historical editor / incremental compiler cache from an IDE-managed build path overshadowing Maven output.
- Residual stale `.class` retained and re-copied by an (as yet unidentified) custom build step or IDE workspace (no Maven plugin observed doing code generation).
- Prior duplicate class confusion (a placeholder file existed under a different package) leading to index or build cache corruption on that symbol.

## Mitigation Implemented
1. Left the legacy `KnoxTokenUtility` in place to avoid breaking any existing references.
2. Introduced `KnoxTokenUtility2` containing the current, intended API (including `createEnrollmentJwt` and additional JWT builders) plus a `BUILD_MARKER` to confirm active compilation.
3. Updated tests to target `KnoxTokenUtility2`, achieving green test results.
4. Preserved negative test verifying misuse of raw public key as `x5c` still raises a failure path.

## Residual Risk
Consumers referencing `KnoxTokenUtility` (original) will not see new helper methods and may experience inconsistent behavior vs documentation.

## Recommended Follow-Up Cleanup
1. Investigate build isolation:
   - Run `mvn -X clean compile` and inspect any unexpected copy/attach phases.
   - Temporarily move the old file out of the tree and attempt a build in a *fresh* clone to confirm reproducibility.
2. Once root cause confirmed, either:
   - Replace stale class with the new implementation (rename `KnoxTokenUtility2` back to `KnoxTokenUtility`), or
   - Deprecate the old class explicitly and re-export forwarding methods in it that delegate to `KnoxTokenUtility2`.
3. Add CI guard:
   - A test that asserts presence of `BUILD_MARKER` (or future marker) in `javap` disassembly to detect recurrence.
   - (Supplemented) Unlock flow tests (`KnoxAuthClientUnlockTest`) already validate new code paths relying on updated utility integration indirectly (ensuring build coherence).
4. Provide a migration note in `CHANGELOG.md` once the rename/deprecation path is finalized.

## Immediate Workarounds for Users
- Use `KnoxTokenUtility2` for all new enrollment and token generation features.
- Avoid relying on `x5c` unless supplying a proper Base64-encoded DER certificate (not just the raw public key bytes).

## Verification Snapshot
- `KnoxTokenUtility2.class` contains expected methods: `createEnrollmentJwt`, `createSignedJWT`, `generateSignedClientIdentifierJWTWithIdpAccessToken`, `generateSignedSessionTokenJWT`, `generateSignedAccessTokenJWT`.
- Tests: `KnoxTokenUtilityTest` and `KnoxTokenUtilityCreateJwtTest` both pass.

## Next Action Owner
Assign an issue (label: build-cache) to trace and eliminate the stale original class artifact.

---
Document created on: 2025-09-15
