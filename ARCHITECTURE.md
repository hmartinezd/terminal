# Product boundaries

This project is a unified Gradle workspace containing three modules:

- **SalesTerminal** — the customer-facing Android product. It creates activation requests and verifies signed licenses offline. Production application code is verifier-only and contains no authority private-key loading, issuance, renewal generation, or audit capability.
- **LicenseContract** — the versioned, public, non-secret Kotlin/JVM compatibility artifact `com.venkoi:license-contract:1.0.0`. It owns only the schema-version-1 wire models, product code, canonical encoder, and public compatibility fixtures.
- **LicenseAdmin** — the trusted Kotlin/JVM administrator application. **License Admin is not Android.** It owns authority key generation/loading/validation, signing, issuance, renewal, audit, and its CLI.

Both products consume the `:LicenseContract` project directly in the unified Gradle workspace. Neither product depends on the other, and ordinary development does not require Maven Local publication. When distribution is needed, `./gradlew :LicenseContract:publishToMavenLocal` can still publish the same coordinate.

The license authority public key verifies subscription licenses. `EXPECTED_RELEASE_CERT_SHA256` independently identifies the Android release certificate; these trust anchors must remain separate. Production authority-key creation and custody are an operational release ceremony and no production private key belongs in this repository.
