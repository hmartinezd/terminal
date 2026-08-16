# Sales Terminal offline licensing

Sales Terminal generates a version 1 activation request from the provisioning screen. Send that JSON to an authorized License Admin operator, then import the returned signed-license JSON in the same screen. A renewal is imported the same way and must have a higher sequence than the installed license.

The app contains only the authority public key and verifies licenses locally. It cannot create, sign, or renew a license. A replacement device creates a new device identity and therefore requires a new activation request and an explicit new issuance; licenses are never silently transferred.

License states are shown in English or Spanish. Selling remains subject to the existing valid, warning, grace, expiration, trusted-time, binding, and application-integrity rules.

`LICENSE_AUTHORITY_PUBLIC_KEY` is the Base64 X.509 public key that verifies subscription licenses. `EXPECTED_RELEASE_CERT_SHA256` is the Android signing-certificate digest used for application-integrity checks. They are independent controls and must not be combined.
