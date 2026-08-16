# Offline License Operations

The `license-issuer` JVM application is the pilot subscription administration boundary. Payment approval remains external and manual. The tool stores licensing identity metadata only; it never receives sales, orders, menu, reporting, inventory, or customer data.

## Initial setup

Generate a P-256 development or production keypair with `./gradlew :license-issuer:run --args='generate-keypair --private /secure/authority.private.pem --public /secure/authority.public.pem'`. Keep production keys outside the repository and in protected backup storage. Losing the private key prevents normal future renewals; compromise permits forged licenses. A DEV key is only for `ENFORCE_LICENSE_IN_DEBUG=true` QA. A production key signs real release leases.

Use `key-info --private-key /secure/authority.private.pem` to print the public-key SHA-256 fingerprint, public PEM, and Base64 X.509 value for `LICENSE_AUTHORITY_PUBLIC_KEY`. This authority key is unrelated to `EXPECTED_RELEASE_CERT_SHA256`, which identifies the Android app signing certificate. Never put the authority private key in Android source, configuration, assets, or resources.

## Initial activation

1. Install and configure Sales Terminal.
2. Open Settings → Subscription and generate an activation request.
3. Transfer the activation JSON through any available channel.
4. Inspect it with `inspect-request activation.json`.
5. After external payment/approval checks, run `issue`. Restaurant, terminal, and device identity are copied from the request. Review the summary and type `YES`, or deliberately pass `--yes` for automation.
6. Return the signed JSON to the customer for offline import; selling becomes enabled when Android validates it.

Example: `./gradlew :license-issuer:run --args='issue --request activation.json --private-key /secure/authority.private.pem --plan PILOT --sequence 1 --expires 2026-09-30T23:59:59Z --grace-until 2026-10-10T23:59:59Z --output license.json --audit /secure/license-audit.jsonl'`

## Renewal and replacement

Confirm payment externally, then run `renew --license license.json --private-key ... --expires ... --grace-until ... --output renewal.json`. The old signature is verified with the supplied authority, sequence advances by exactly one, binding is preserved, and a fresh license ID is generated. If `--request` is supplied, all identity fields must match. A tampered license cannot be renewed. Importing older sequence files remains rejected by Android's anti-downgrade state.

A replacement device always creates a new activation request and requires an explicit new `issue`; it is never a silent renewal. Cross-terminal subscription counts and revoked/disabled decisions remain manual in the pilot. Fully offline devices cannot receive immediate revocation, so stop renewals and use short leases.

## Audit and queries

Every successful write appends license metadata, request ID, and the output SHA-256 hash to a local JSONL audit file. It contains no key material. Use `list --restaurant ID`, `show-license ID`, or `show-device DEVICE_ID` with `--audit`; list output includes the number of distinct devices whose grace period is still active.

Normal issuance rejects past expiration. `--allow-expired-for-testing` is an explicit QA-only escape hatch. Generated files are never overwritten unless `--force` is supplied.
