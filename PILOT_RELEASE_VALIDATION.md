# M11 Final Pilot / Release Validation

Date: 2026-08-16 (America/New_York)  
Overall status: **NOT READY** — automated verification and the core activation/sale smoke path pass, but the complete runtime pilot matrix was not executed.  
Validation build: debug with `ENFORCE_LICENSE_IN_DEBUG=true` and a temporary DEVELOPMENT P-256 authority. No production credentials were created.

## Automated verification

| Component | Result | Evidence |
|---|---|---|
| License Contract | PASS | Clean `build`, 2/2 tests including the canonical V1 vector, JAR/module/POM generation, and `publishToMavenLocal` succeeded independently. |
| License Admin | PASS | Clean JVM `build`; 9/9 tests passed (issuance, renewal, mismatch/tamper/compatibility coverage). Real `generate-keypair`, `key-info`, `inspect-request`, `issue`, `renew`, and `list` commands succeeded with `/tmp` development material. |
| Sales Terminal | PASS | `assembleDebug`, 80/80 JVM tests, `lintDebug`, `compileDebugAndroidTestKotlin`, and R8-minified `assembleRelease` succeeded with a non-production public key and placeholder certificate digest. Normal-debug device instrumentation passed 47/47. |

The enforced-license instrumentation APK was also run. Seven repository integration tests failed because they intentionally rely on debug developer authorization and were run under enforcement without installing a license; the same 47-test suite passed under its normal debug test configuration. This is recorded as a test-configuration limitation, not a product runtime failure.

## Runtime target and evidence

- PASS — Pixel Tablet AVD, 2560×1600 landscape: clean install, cold start, menu setup, navigation rail, three-panel Orders layout, Subscription UI, disabled selling before activation, activation, restart, one completed sale, History, renewal, and stale rejection.
- PASS — Clock rollback fail-closed behavior: a stale AVD snapshot placed the guest clock eight minutes behind the issuer; the accepted license remained unauthorized with a localized clock warning. A no-snapshot cold boot synchronized guest/host time and the same persisted license enabled selling.
- NOT RUN — 1024×600 and 1280×800 tablet sizes; no suitably configured AVD was run.
- NOT RUN — portrait phone smoke. A personal Pixel 7 was connected, but the validation APK was not installed on that device and a phone AVD was not run.

## Scenario record

`PASS` means the behavior was actually exercised by a command, automated test, or device interaction. Automated-only evidence is explicitly identified.

| # | Scenario | Result | Evidence / reason |
|---:|---|---|---|
| 2 | Full automated verification | PASS | All requested build/test/lint/compile/minification gates passed; 47 device instrumentation tests also passed normally. |
| 5 | Device targets | NOT RUN | Primary large tablet ran; representative smaller tablet and phone targets did not. |
| 6 | Clean install / first start | PASS | Clean enforced build started, setup was usable, Subscription visible, and New Order/product cards were disabled before licensing. |
| 7–8 | True offline activation and identity binding | PASS | The app generated the exact request used by Admin. Schema 1, `SALES_TERMINAL`, restaurant `venkoi-demo`, terminal ID, and device key ID matched issuance; sequence 1 import succeeded and enabled selling. |
| 9 | Copy to another device | NOT RUN | A second configured terminal identity was not provisioned at runtime. Automated verifier coverage passed. |
| 10 | Tampered license | NOT RUN | Runtime import not performed; automated signature-tamper tests passed. |
| 11 | Wrong authority | NOT RUN | Runtime build/import not repeated under authority B; automated wrong-authority tests passed. |
| 12 | Renewal and stale rejection | PASS | Admin issued sequence 2; import updated expiration without reinstall; sequence 1 re-import was rejected as older and sequence 2 remained installed. |
| 13 | Same-sequence semantic duplicate | PASS | Automated `OfflineLicensingTest` exercised separately signed equal semantic payloads. |
| 14 | Valid / expiring / grace / expired | PASS | Automated license-policy tests passed; runtime temporal transitions were not observed. |
| 15–16 | Live expiration and UI race | PASS | Repository authorization and feedback regression tests passed; no runtime wall-clock transition/race was manually induced. |
| 17–18 | Expired read access / open order | PASS | Automated authorization/repository coverage passed; full expired-mode UI matrix was not manually repeated. |
| 19 | Basic sale | PASS | Runtime: created an order, added Classic Burger, used default TRANSFER, completed revision 1, and observed 1,500.00 CUP COMPLETED in History. CASH/mixed modes were automated-only. |
| 20 | Three open orders | PASS | Automated independent-sales integration test passed; runtime three-order switching not performed. |
| 21 | Restart recovery | PASS | On-device durable readback tests passed; setup/license survived cold boot. Manual multi-order restart was not performed. |
| 22 | Completion failure safety | PASS | Automated completion failure/atomicity tests passed; no manual storage failure was injected. |
| 23 | Double-tap protection | PASS | Automated DAO concurrency/idempotency tests passed; manual rapid-tap matrix not performed. |
| 24–25 | History and VOID | PASS | Runtime completed History record passed; immutable snapshots and VOID revision/idempotency passed automated tests. Manual runtime VOID was not performed. |
| 26 | Reports | PASS | Automated report DAO/builders passed. Runtime report screen/data combinations were not exhaustively exercised. |
| 27 | Business date | PASS | Automated exact cutoff/boundary tests passed for configured restaurant rules. |
| 28 | Timezone | PASS | Runtime History displayed the completed sale with configured data; timezone behavior is also covered by automated business-date tests. Printing/export timezone display was not manually inspected. |
| 29–30 | Money and cash discount | PASS | Exact `BigDecimal`, formatting, pricing, totals, and serialization tests passed. Runtime sample total was exact; full CASH mode matrix was automated-only. |
| 31 | Printing / Save as PDF | NOT RUN | Print layout/content tests passed, but Android print dialog and PDF output were not executed. |
| 32–35 | Pending/day export and failure safety | PASS | Automated SalesBatch/export coordinator and export DAO lifecycle tests passed. Android file-picker export/cancel/bookkeeping-failure paths were not manually executed. |
| 36–40 | Large-volume menu/orders/history/reports/export | NOT RUN | Only the 11-item demo and ordinary automated fixtures were used; pilot-scale runtime datasets were not loaded. |
| 41 | English | PASS | Runtime setup, Orders, Settings/Subscription, activation messages, completion dialog, and History were exercised in English. Print/export messages were not runtime-tested. |
| 42 | Spanish | NOT RUN | Spanish resources compiled/linted; runtime key flows were not repeated in Español. |
| 43 | System-default language | NOT RUN | Restart behavior under a changed system locale was not exercised. |
| 44–45 | Tablet UI / product cards | PASS | Large landscape tablet visually/semantically exposed navigation rail, orders, menu, current order, category chips, cards, totals, and actions without structural overlap in UI hierarchy. Smaller tablet sizes were not run. |
| 46 | Accessibility basics | PASS | Lint passed and UI hierarchy exposed labeled navigation/actions and disabled states. Full TalkBack/contrast audit was not run. |
| 47 | Long text | NOT RUN | No dedicated long-text dataset was imported. |
| 48 | Phone smoke | NOT RUN | No phone runtime target was modified. |
| 49 | License Admin pilot operation | PASS | Real development key generation/info/request inspection/issue/renew/list succeeded; tests cover mismatch, overwrite protection, and audit safety. `show-license`/`show-device` were not separately invoked. |
| 50 | Contract independence | PASS | Contract clean build/test/publication ran before Admin and Android verification. |
| 51 | Production authority key | NOT YET CREATED | No production authority material exists or was fabricated. |
| 52 | Android production signing | NOT CONFIGURED | Minified release build passed only with non-production public configuration and placeholder certificate digest; no production-signed artifact was verified. |
| 53 | Security quick check | PASS | Product source scan/build guard found no authority private material; release verifier fails closed; rollback fail-closed observed; malformed/tamper/device/wrong-authority behavior is automated. Test-only signing helpers exist under `src/test`, not product sources. |
| 54 | No Internet | NOT RUN | Licensing has no online dependency by design, but network was not explicitly disabled for a full runtime flow. |
| 55 | Full restaurant pilot scenario | NOT RUN | Activation, one sale, restart, History, renewal, and stale rejection ran; the complete ordered 26-step scenario (two orders, reports, print, exports, VOID, Spanish) did not. |
| 56 | Severity classification | PASS | Remaining gaps below are classified. |
| 57–59 | Readiness / acceptance | NOT READY | Runtime print/export, second-device rejection, expiration/read-only flows, representative device sizes, localization, load, and full pilot scenario remain unexecuted. |

## Readiness summary

| Area | Status | Basis |
|---|---|---|
| Core Sales | READY WITH MINOR ISSUE | One runtime sale and automated lifecycle coverage passed; complete payment-mode runtime matrix not run. |
| OPEN Order Recovery | READY WITH MINOR ISSUE | Device persistence tests passed; manual multiple-order restart not run. |
| History / VOID | READY WITH MINOR ISSUE | Runtime History passed; VOID is automated-only. |
| Reports | READY WITH MINOR ISSUE | Automated only for report combinations. |
| Printing | NOT READY | No Android print dialog/PDF runtime evidence. |
| Sales Export | READY WITH MINOR ISSUE | Strong automated lifecycle coverage, no Android document-provider runtime flow. |
| Localization | NOT READY | English runtime passed; Spanish/system-default runtime not run. |
| Tablet UI | READY WITH MINOR ISSUE | Large landscape tablet passed; smaller representative sizes/long text/load not run. |
| Offline Licensing | READY WITH MINOR ISSUE | Activation/renewal/stale/rollback runtime passed; device-copy/tamper/wrong-authority/expiration runtime matrix incomplete. |
| License Admin | READY | Build/tests and real development operations passed. |
| Security Boundary | READY WITH MINOR ISSUE | Automated and source/build guard evidence passed; second-device runtime test missing. |
| Automated Verification | READY | All required gates passed. |

## Remaining issues

### BLOCKER

- None found.

### MAJOR

- Release evidence is incomplete: Android printing/PDF, Spanish/system-default language, phone and smaller-tablet layouts, no-network operation, full export/VOID flow, second-device license rejection, live expiration/read-only behavior, and the complete restaurant-pilot sequence were not run. These are validation gaps, not observed product defects, but M11 acceptance cannot be claimed without them.
- Production authority key and real Android release-signing configuration are not configured. This is an operational release task; no fake credentials were created.

### MINOR

- The on-device instrumentation suite assumes the normal debug developer authorization; running that suite unchanged in an enforced-license APK produces seven expected authorization failures. A dedicated licensed-test fixture would be needed if enforced instrumentation is made a formal gate.
- The saved Medium Tablet quick-boot snapshot had a stale guest clock. Rollback protection correctly failed closed; cold boot without the snapshot synchronized time and restored authorization.

### POLISH

- Compiler output contains deprecation/opt-in warnings, but lint and all requested builds pass.

## Recommendation

**NOT READY FOR PILOT** until the MAJOR validation gaps above are actually executed and recorded. No BLOCKER product defect or sale-data-loss defect was observed in the work completed.
