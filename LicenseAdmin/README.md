# License Admin

License Admin is a standalone Kotlin/JVM command-line product for a trusted administrator computer. It is not Android and has no server, payment, or cloud dependency. Its signing core is separate from CLI parsing so it can later sit behind a secured service and HSM/KMS without changing `SignedLicenseV1`.

From the top-level workspace, run `./gradlew :LicenseAdmin:test` for verification and `./gradlew :LicenseAdmin:run --args='--help'` for commands. The module consumes `:LicenseContract` directly, so local development does not require Maven Local publication. Contract schema version 1 and its canonical encoding are immutable; incompatible changes require schema version 2.

## Initial activation

Inspect the Sales Terminal request with `inspect-request activation.json`. Issue only after checking restaurant, terminal, and device identity. `issue` requires `--request`, `--private-key`, `--public-key`, `--plan`, `--sequence`, `--expires`, `--grace-until`, and `--output`.

Both keys are required. Before signing, License Admin signs a random challenge with the PKCS#8 private key and verifies it with the X.509 public key. A mismatch aborts issuance. The issued license is also self-verified.

## Renewal and device replacement

Use `renew --license current.json` with both key paths and new expiration/grace instants. The existing signature must verify, binding is preserved, a new license ID is generated, and sequence increases exactly by one. An optional activation request must match the existing binding.

A new device must generate a new `ActivationRequestV1` and receive an explicit new issuance. Never transfer the old device license implicitly.

## Keypair generation and backup

`generate-keypair --private /secure/authority-private.pem --public /secure/authority-public.pem` generates P-256 using standard JCA APIs. Existing files are not overwritten without `--force`. Keep the production private key outside this repository and preferably offline. Never log it, put it in fixtures, or copy it into Sales Terminal.

If the private key is lost, installed licenses continue through expiration/grace, but normal renewal under that authority becomes impossible. If it is compromised, an attacker can forge valid licenses. Maintain a protected offline backup with controlled access.

## Public key configuration

`key-info --public-key /secure/authority-public.pem` prints only the fingerprint, Base64 X.509 value, and public PEM. Configure the Base64 value as Sales Terminal `LICENSE_AUTHORITY_PUBLIC_KEY`.

This license authority key is unrelated to `EXPECTED_RELEASE_CERT_SHA256`, which identifies the Android release signing certificate.

## Audit, QA, and production

Issuance appends JSONL records containing license/binding/plan/timing IDs, output SHA-256, and status. `list`, `show-license`, and `show-device` inspect them. The audit contains no private keys, sales, orders, menus, reports, or customer operational data.

Use a dedicated development authority only for tests and debug QA. The public compatibility fixture is non-secret; its private key is not retained. Generate and protect the production authority separately, back it up offline, and never commit it.
