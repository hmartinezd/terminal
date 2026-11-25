# Sales Terminal pilot release manifest

Status: **READY FOR FINAL PRODUCTION ARTIFACT CEREMONY — artifact pending**

This file records non-secret release metadata only. Complete it from the final, verified artifact; never add private keys, keystores, passwords, or administrator audit data.

| Field | Value |
|---|---|
| R1 readiness | READY FOR FINAL PRODUCTION ARTIFACT CEREMONY |
| Application ID | `com.venkoi.terminal` (permanent identity) |
| Version name | `1.0.0-pilot.1` |
| Version code | `2` (development candidate; confirm installed pilot version before distribution) |
| Database production baseline | Room schema version `7` (explicit non-destructive upgrades from schema 5 and both schema-6 variants) |
| APK filename | PENDING |
| APK SHA-256 | PENDING |
| Android signing certificate SHA-256 | PENDING |
| License authority public-key fingerprint | PENDING |
| Pilot build date/time | PENDING |
| Production-authority configuration | PENDING |
| Authority private-key backup | NOT CONFIRMED |
| Android signing-keystore backup | NOT CONFIRMED |
| Backup recovery checks | NOT CONFIRMED |

Version convention: `1.0.0-pilot.N` identifies controlled R1 pilot builds. `versionCode` increases for every installable update and is never reused. Version code 1/schema 5 remain the historical first-distributed baseline; this candidate is Room schema 7. No distributed schema-4 upgrade origin exists; every schema change after the first distributed schema-5 build requires an explicit, tested, non-destructive migration.

Current candidate data compatibility: schema 5 gains nullable immutable-provenance columns. Schema 6 from Redesign already has the correct layout; schema 6 from Main maps stored menu and publication provenance into their snapshot columns and leaves unrecorded restaurant provenance unknown. Sale IDs, lines, and export bookkeeping are preserved. Incomplete provenance remains available in History, Reports, and Print/PDF, but is not safe for canonical export. New sales capture all three provenance values. No destructive fallback is enabled. Final production artifact metadata remains PENDING.

Key warning: loss of the authority private key prevents normal future renewals under that authority after existing licenses expire or leave grace. Compromise allows an attacker to issue valid licenses. Keep one access-controlled primary copy and one independently stored encrypted offline backup. Keep the Android signing keystore and its independently stored protected backup with the same care; losing it prevents compatible application upgrades.
