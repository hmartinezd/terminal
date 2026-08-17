# Sales Terminal pilot release manifest

Status: **NOT READY — production ceremony pending**

This file records non-secret release metadata only. Complete it from the final, verified artifact; never add private keys, keystores, passwords, or administrator audit data.

| Field | Value |
|---|---|
| R1 readiness | READY FOR CONTROLLED PILOT (product validation) |
| Application ID | `com.venkoi.terminal` (permanent identity) |
| Version name | `1.0.0-pilot.1` |
| Version code | `1` |
| Database production baseline | Room schema version `4` |
| APK filename | PENDING |
| APK SHA-256 | PENDING |
| Android signing certificate SHA-256 | PENDING |
| License authority public-key fingerprint | PENDING |
| Pilot build date/time | PENDING |
| Production-authority configuration | PENDING |
| Authority private-key backup | NOT CONFIRMED |
| Android signing-keystore backup | NOT CONFIRMED |
| Backup recovery checks | NOT CONFIRMED |

Version convention: `1.0.0-pilot.N` identifies controlled R1 pilot builds. `versionCode` increases for every installable update and is never reused. Version code 1/schema 4 are the first distributed production baselines; all later releases require non-destructive upgrade handling.

Key warning: loss of the authority private key prevents normal future renewals under that authority after existing licenses expire or leave grace. Compromise allows an attacker to issue valid licenses. Keep one access-controlled primary copy and one independently stored encrypted offline backup. Keep the Android signing keystore and its independently stored protected backup with the same care; losing it prevents compatible application upgrades.
