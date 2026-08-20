# Controlled pilot release and installation

## Production ceremony

Perform this on the authorized administrator/release machines. Secret paths must be outside this repository. Do not overwrite existing material.

1. Generate the production P-256 authority with License Admin: `generate-keypair --private <external-private-path> --public <external-public-path>`.
2. Create an access-controlled primary private-key copy and one encrypted offline backup on independent storage. Restore-test signing capability without exposing the key; record only **BACKUP CONFIRMED**.
3. Run `key-info --public-key <external-public-path>` and record its public fingerprint and Base64 X.509 value.
4. Create or identify a dedicated Android signing keystore with JDK/Android tooling. Do not reuse the authority key. Back it up independently and restore-test access; record only **BACKUP CONFIRMED**.
5. Obtain the signing certificate SHA-256 with `keytool` or `apksigner`. Normalize it as 64 uppercase hexadecimal characters (colons are accepted as input and removed by the build).
6. Build from `SalesTerminal` with `PRODUCTION_PILOT=true` and these external environment variables: `LICENSE_AUTHORITY_PUBLIC_KEY`, `EXPECTED_RELEASE_CERT_SHA256`, `ANDROID_SIGNING_KEYSTORE` (absolute external path), `ANDROID_SIGNING_ALIAS`, `ANDROID_SIGNING_STORE_PASSWORD`, and `ANDROID_SIGNING_KEY_PASSWORD`. Run `./gradlew --no-configuration-cache clean assembleRelease`. Production mode rejects Gradle configuration caching so signing passwords are not persisted there.
7. The production-pilot build fails before compilation for missing/placeholder configuration, an in-repository keystore, an unusable alias/password, or a fingerprint that differs from the keystore certificate. Routine debug and explicitly non-production release verification do not require production secrets.
8. Verify the APK independently: `apksigner verify --verbose --print-certs <apk>`. Confirm the signer SHA-256 equals `EXPECTED_RELEASE_CERT_SHA256`, then calculate the APK SHA-256 and complete `PILOT_RELEASE_MANIFEST.md`.
9. Inspect the APK/archive and source scan results to confirm no private authority key, keystore, password file, debug certificate, or administrator audit is packaged. Release code keeps `developerAuthorization=false`; R8 and resource shrinking remain enabled.

## Clean-tablet activation and smoke test

1. Install the production APK on the clean pilot/test tablet.
2. Import/configure the restaurant MenuPackage and verify restaurant and terminal identity.
3. Generate `ActivationRequestV1` and transfer it to the administrator.
4. Inspect the exact request. Issue sequence `1`, a deliberately short PILOT lease, and an explicit grace deadline using the production authority. Record `expiresAtUtc` and `graceUntilUtc` in the secure operational record. Every terminal receives its own restaurant/terminal/device-bound lease.
5. Return only the signed license and import it. Confirm **Active** and that offline selling is enabled.
6. Open the application drawer and verify the compact tablet baseline: horizontal Open Orders with New Order first, dense Current Order, grouped History with persistent detail, compact Reports, and Settings master/detail. Create and complete a small order. Verify History, Reports, Android print entry, Save export, Share export (`content://`, `application/json`, standard chooser), restart, and English ↔ Español once.
7. Optionally discard/reset test operational data before restaurant use.

## Upgrade and data-preservation test

1. Keep build N installed with terminal identity, restaurant/menu configuration, an active license, an OPEN order, History, and export bookkeeping.
2. Build N+1 with the same application ID (`com.venkoi.terminal`), same Android signing key, and a higher `versionCode`; no feature change is required.
3. Install it as an update without clearing application data. Confirm Android accepts it and every item above remains.
4. Pilot version `1.0.0-pilot.1` / version code `1` / Room schema `5` is the **first distributed production baseline**. No distributed schema-4 production origin exists, so no migration 4 → 5 is required for this baseline. From the first distributed schema-5 build onward, every schema change requires an explicit, tested, non-destructive Room migration. Destructive release migration fallback remains disabled.
5. A normal signed application update preserves the installed license and device identity when application data and the Android Keystore identity remain intact. Never clear app data, uninstall the app, or replace the device as part of a routine update unless reprovisioning and relicensing are intended.

## Restricted operation and recovery guidance

An `EXPIRED` or `CLOCK_ROLLBACK_DETECTED` terminal remains available for selecting/viewing and cancelling existing OPEN orders, History and policy-permitted historical VOID, Reports, Print/Save PDF, sales export, Settings/language, activation-request generation, and license import/renewal. New Order, order-label edits, item/quantity/pricing/removal mutations, and Complete Sale are blocked.

Correcting Android date/time alone does not clear `CLOCK_ROLLBACK_DETECTED`. Use this procedure:

1. Correct the Android device date/time.
2. Open Settings and generate an Activation Request if the administrator needs one.
3. Have the administrator issue a newly signed, device-bound license with a higher sequence.
4. Import that license. The application validates it against the corrected wall time and securely re-anchors trusted time; normal monotonic anti-rollback operation then resumes.

If persistence of that signed license succeeded but trusted-time re-anchoring was interrupted, re-importing the exact same signed license may safely finish recovery when it still matches the installed verified license and passes recovery validation. Do not demand another higher sequence solely because of this interruption.

`LOCAL_SECURITY_STATE_INVALID` is a separate fail-closed security-state failure. It blocks selling, is not ordinary expiration, and is not repaired by changing the device clock. Do not attempt an unsupported manual reset or bypass; escalate through the controlled support/reprovisioning process.

## Restaurant package

Provide only the verified APK, the restaurant MenuPackage, these installation steps, and that terminal's signed license. Never provide the authority private key, Android signing keystore/passwords, or administrator audit files.
