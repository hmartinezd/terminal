# Sales Terminal pilot release manifest

Status: **READY FOR FINAL PRODUCTION ARTIFACT CEREMONY — artifact pending**

This file records non-secret release metadata only. Complete it from the final, verified artifact; never add private keys, keystores, passwords, or administrator audit data.

| Field | Value |
|---|---|
| R1 readiness | READY FOR FINAL PRODUCTION ARTIFACT CEREMONY |
| Application ID | `com.venkoi.terminal` (permanent identity) |
| Version name | `1.0.0-pilot.1` |
| Version code | `1` |
| Database production baseline | Room schema version `6` (current candidate; explicit non-destructive 5 → 6 migration) |
| APK filename | PENDING |
| APK SHA-256 | PENDING |
| Android signing certificate SHA-256 | PENDING |
| License authority public-key fingerprint | PENDING |
| Pilot build date/time | PENDING |
| Production-authority configuration | PENDING |
| Authority private-key backup | NOT CONFIRMED |
| Android signing-keystore backup | NOT CONFIRMED |
| Backup recovery checks | NOT CONFIRMED |

Version convention: `1.0.0-pilot.N` identifies controlled R1 pilot builds. `versionCode` increases for every installable update and is never reused. Version code 1/schema 5 remain the historical first-distributed baseline; this candidate is Room schema 6. No distributed schema-4 upgrade origin exists; every schema change after the first distributed schema-5 build requires an explicit, tested, non-destructive migration.

Current candidate data compatibility: the explicit 5 → 6 migration only adds nullable immutable-provenance columns. Existing schema-5 client rows, sale IDs, lines, and export bookkeeping are preserved. Pre-provenance sales remain available in History, Reports, and Print/PDF but are not safely canonical-exportable; new schema-6 sales capture restaurant, menu, and publication-revision provenance at creation time. Final production artifact metadata remains PENDING.

Key warning: loss of the authority private key prevents normal future renewals under that authority after existing licenses expire or leave grace. Compromise allows an attacker to issue valid licenses. Keep one access-controlled primary copy and one independently stored encrypted offline backup. Keep the Android signing keystore and its independently stored protected backup with the same care; losing it prevents compatible application upgrades.
