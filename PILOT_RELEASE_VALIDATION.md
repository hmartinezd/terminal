# M11B Critical Pilot Acceptance Closure

Date: 2026-08-16 (America/New_York)  
Overall status: **NOT READY** — all executed tablet-first critical runtime paths passed, but the critical second-device binding rejection remains NOT RUN because the available device-B emulator did not reach an unlocked interactive Android user.
Validation build: debug with `ENFORCE_LICENSE_IN_DEBUG=true` and a temporary DEVELOPMENT P-256 authority. No production credentials were created.

## Automated verification

| Component | Result | Evidence |
|---|---|---|
| License Contract | PASS | M11B clean `build` and publication succeeded; 2/2 tests passed, including the canonical V1 golden vector. |
| License Admin | PASS | M11B clean JVM `build` succeeded; 9/9 issuance, renewal, mismatch, tamper, and compatibility tests passed. |
| Sales Terminal | PASS | M11B `assembleDebug`, 89/89 JVM tests, `lintDebug`, `compileDebugAndroidTestKotlin`, and R8-minified `assembleRelease` succeeded with a non-production public key and placeholder certificate digest. Prior normal-debug device instrumentation remains 47/47 PASS. |

The enforced-license instrumentation APK was also run. Seven repository integration tests failed because they intentionally rely on debug developer authorization and were run under enforcement without installing a license; the same 47-test suite passed under its normal debug test configuration. This is recorded as a test-configuration limitation, not a product runtime failure.

## Runtime target and evidence

- PASS — Pixel Tablet AVD, 2560×1600 landscape: clean install, cold start, menu setup, navigation rail, three-panel Orders layout, Subscription UI, disabled selling before activation, activation, restart, one completed sale, History, renewal, and stale rejection.
- PASS — Clock rollback fail-closed behavior: a stale AVD snapshot placed the guest clock eight minutes behind the issuer; the accepted license remained unauthorized with a localized clock warning. A no-snapshot cold boot synchronized guest/host time and the same persisted license enabled selling.
- PASS — representative 1280×800 effective landscape tablet: NavigationRail, Open Orders, dominant Menu, Current Order, product cards, totals, and completion action were all visible and usable without clipping. A preliminary 1280×800-pixel/320-dpi run was correctly discarded because it represented only 640×400 dp.
- NOT RUN — portrait phone smoke. A personal Pixel 7 was connected, but the validation APK was not installed on that device and a phone AVD was not run.

## Scenario record

`PASS` means the behavior was actually exercised by a command, automated test, or device interaction. Automated-only evidence is explicitly identified.

| # | Scenario | Result | Evidence / reason |
|---:|---|---|---|
| 2 | Full automated verification | PASS | All requested build/test/lint/compile/minification gates passed; 47 device instrumentation tests also passed normally. |
| 5 | Device targets | PASS WITH MINOR GAP | Primary large tablet and representative 1280×800 effective landscape tablet passed. Portrait phone remains secondary/NOT RUN. |
| 6 | Clean install / first start | PASS | Clean enforced build started, setup was usable, Subscription visible, and New Order/product cards were disabled before licensing. |
| 7–8 | True offline activation and identity binding | PASS | The app generated the exact request used by Admin. Schema 1, `SALES_TERMINAL`, restaurant `venkoi-demo`, terminal ID, and device key ID matched issuance; sequence 1 import succeeded and enabled selling. |
| 9 | Copy to another device | NOT RUN | A second emulator was created and the enforced QA APK installed, but its primary Android user/storage remained locked behind a black system surface, so license import could not be exercised. Automated device-mismatch coverage passed; no runtime result is claimed. |
| 10 | Tampered license | NOT RUN | Runtime import not performed; automated signature-tamper tests passed. |
| 11 | Wrong authority | NOT RUN | Runtime build/import not repeated under authority B; automated wrong-authority tests passed. |
| 12 | Renewal and stale rejection | PASS | Admin issued sequence 2; import updated expiration without reinstall; sequence 1 re-import was rejected as older and sequence 2 remained installed. |
| 13 | Same-sequence semantic duplicate | PASS | Automated `OfflineLicensingTest` exercised separately signed equal semantic payloads. |
| 14 | Valid / expiring / grace / expired | PASS | RUNTIME: imported a deliberately short-lived development sequence-3 renewal, observed `Expires soon`, then `Expired` after the emulator clock crossed grace. Automated policy tests also pass. |
| 15–16 | Live expiration and UI race | PASS | RUNTIME: Complete Sale appeared enabled immediately before policy refresh; the post-expiration tap produced the selling-disabled message, did not open confirmation or complete the sale, left the order OPEN, and refreshed controls disabled. |
| 17–18 | Expired read access / open order | PASS | RUNTIME: preserved OPEN order remained with label, burger ×2, CASH snapshots and 2,700.00 total; New Order, item/label/quantity/pricing mutations, and Complete were disabled; Discard stayed enabled. History, Reports, Android print entry, Settings, activation/renewal controls, enabled export controls, and historical VOID remained available. Sequence 4 restored selling and the same order without reconstruction. |
| 19 | Basic sale | PASS | Runtime: created an order, added Classic Burger, used default TRANSFER, completed revision 1, and observed 1,500.00 CUP COMPLETED in History. CASH/mixed modes were automated-only. |
| 20 | Three open orders | PASS | RUNTIME: created three differently labeled orders with distinct products and quantities; burger ×2 used CASH (3,000.00 subtotal, 300.00 discount, 2,700.00 total), soft drink ×3 and flan ×1 used TRANSFER. Repeated switching showed no cross-order mutation. |
| 21 | Restart recovery | PASS | RUNTIME: force-stop/cold reopen preserved all three OPEN orders and their labels, lines, quantities, pricing modes, price/discount/currency snapshots, and totals. |
| 22 | Completion failure safety | PASS | Automated completion failure/atomicity tests passed; no manual storage failure was injected. |
| 23 | Double-tap protection | PASS | Automated DAO concurrency/idempotency tests passed; manual rapid-tap matrix not performed. |
| 24–25 | History and VOID | PASS | RUNTIME: completed one recovered order; the other two remained OPEN. History showed immutable Flan ×1 TRANSFER snapshots. VOID confirmation changed it to VOIDED in place, retained original data, did not reopen it, removed the repeat VOID action, and later historical VOID also worked while expired. |
| 26 | Reports | PASS | RUNTIME: report updated to 1 valid/1 voided with 400.00 CUP voided amount, and later reflected subsequent lifecycle changes. Automated DAO/builders also pass. |
| 27 | Business date | PASS | Automated exact cutoff/boundary tests passed for configured restaurant rules. |
| 28 | Timezone | PASS | Runtime History displayed the completed sale with configured data; timezone behavior is also covered by automated business-date tests. Printing/export timezone display was not manually inspected. |
| 29–30 | Money and cash discount | PASS | Exact `BigDecimal`, formatting, pricing, totals, and serialization tests passed. Runtime sample total was exact; full CASH mode matrix was automated-only. |
| 31 | Printing / Save as PDF | PASS | RUNTIME: Android print spooler opened for a sale and report; Save as PDF created readable 20,734-byte sale and 30,234-byte report PDFs. Rendered inspection confirmed correct headers/status/totals, margins, wrapping, and no clipping. The report preview was allowed to finish layout before the accepted artifact was saved. |
| 32–35 | Pending/day export and failure safety | PASS | RUNTIME: Android picker cancellation left 2 pending and `Never` unchanged; retry wrote a valid SalesBatchV1 JSON. Order B revision 1 COMPLETED exported with exact Soft Drink ×3 snapshots, then VOID made one change pending and revision 2 VOIDED exported with the same saleId/lineId. Day export wrote all three then-current COMPLETED/VOIDED sales for business date 2026-08-16 despite prior pending exports. Decimal snapshot strings used plain notation. |
| 36–40 | Large-volume menu/orders/history/reports/export | NOT RUN | Only the 11-item demo and ordinary automated fixtures were used; pilot-scale runtime datasets were not loaded. |
| 41 | English | PASS | Runtime setup, Orders, Settings/Subscription, activation messages, completion dialog, and History were exercised in English. Print/export messages were not runtime-tested. |
| 42 | Spanish | PASS | RUNTIME: navigation, Orders, CASH/TRANSFER totals, completion dialog, History statuses, Reports, Settings/Subscription, VOID and print labels were inspected in Español. Restaurant/menu/order-provided names remained verbatim. Export/license resources compile and lint; no visible fixed-language leftover was found in exercised flows. |
| 43 | System-default language | PASS | RUNTIME: selected System default from Español and cold-restarted; the app followed the emulator's known English system locale. |
| 44–45 | Tablet UI / product cards | PASS | RUNTIME: both 2560×1600 and a true 1280×800 effective landscape configuration exposed NavigationRail, Open Orders, dominant Menu, Current Order, category chips, usable cards, totals, and reachable completion without clipping. |
| 46 | Accessibility basics | PASS | Lint passed and UI hierarchy exposed labeled navigation/actions and disabled states. Full TalkBack/contrast audit was not run. |
| 47 | Long text | NOT RUN | No dedicated long-text dataset was imported. |
| 48 | Phone smoke | NOT RUN | No phone runtime target was modified. |
| 49 | License Admin pilot operation | PASS | Real development key generation/info/request inspection/issue/renew/list succeeded; tests cover mismatch, overwrite protection, and audit safety. `show-license`/`show-device` were not separately invoked. |
| 50 | Contract independence | PASS | Contract clean build/test/publication ran before Admin and Android verification. |
| 51 | Production authority key | NOT YET CREATED | No production authority material exists or was fabricated. |
| 52 | Android production signing | NOT CONFIGURED | Minified release build passed only with non-production public configuration and placeholder certificate digest; no production-signed artifact was verified. |
| 53 | Security quick check | PASS | Product source scan/build guard found no authority private material; release verifier fails closed; rollback fail-closed observed; malformed/tamper/device/wrong-authority behavior is automated. Test-only signing helpers exist under `src/test`, not product sources. |
| 54 | No Internet | PASS | RUNTIME: Wi-Fi disabled and airplane mode enabled throughout cold start, new order, item add, completion, History, Reports, restart/persistence, and a real pending JSON export. License evaluation stayed offline; connectivity was not re-enabled. |
| 55 | Full restaurant pilot scenario | PASS WITH EXCEPTION | All tablet-first lifecycle steps ran across M11/M11B: offline activation, multiple orders/recovery, CASH/TRANSFER, completion, History, VOID, Reports, print/PDF, pending/day/revision-2 exports, Spanish/system default, no-network, expiration/read-only, and renewal. The separate second-device rejection remains NOT RUN. |
| 56 | Severity classification | PASS | Remaining gaps below are classified. |
| 57–59 | Readiness / acceptance | NOT READY | The executed tablet-first critical paths pass with no known data-loss defect. The remaining critical acceptance gap is runtime second-device license rejection. |

## Readiness summary

| Area | Status | Basis |
|---|---|---|
| Core Sales | READY | Multiple runtime sales, CASH/TRANSFER snapshots, completion, recovery, expiration denial, and automated lifecycle coverage passed. |
| OPEN Order Recovery | READY | Three independent runtime orders survived force-stop/restart with immutable snapshots. |
| History / VOID | READY | Runtime History, VOID, no-repeat semantics, and expired historical VOID passed. |
| Reports | READY | Runtime completed/voided reporting and PDF passed. |
| Printing | READY | Real Android sale/report printing and rendered PDF review passed. |
| Sales Export | READY | Real picker cancel/retry, pending revision 1, VOID revision 2, day re-export, and offline export passed. |
| Localization | READY | Spanish key flows and system-default restart passed. |
| Tablet UI | READY WITH MINOR ISSUE | Large and 1280×800 effective landscape tablets passed; dedicated long-text data and phone optimization remain secondary. |
| Offline Licensing | READY WITH MAJOR VALIDATION GAP | Activation, renewal, stale rejection, rollback fail-closed, expiration/read-only/resume, and no-network operation passed; runtime second-device rejection remains NOT RUN. |
| License Admin | READY | Build/tests and real development operations passed. |
| Security Boundary | READY WITH MINOR ISSUE | Automated and source/build guard evidence passed; second-device runtime test missing. |
| Automated Verification | READY | All required gates passed. |

## Remaining issues

### BLOCKER

- None found.

### MAJOR

- Runtime second-device binding rejection remains NOT RUN. Device B was created and received the enforced APK, but its Android primary user/storage stayed locked and its display remained black, preventing the import attempt. This is a critical validation gap, not an observed product defect.

### MINOR

- The on-device instrumentation suite assumes the normal debug developer authorization; running that suite unchanged in an enforced-license APK produces seven expected authorization failures. A dedicated licensed-test fixture would be needed if enforced instrumentation is made a formal gate.
- The saved Medium Tablet quick-boot snapshot had a stale guest clock. Rollback protection correctly failed closed; cold boot without the snapshot synchronized time and restored authorization.
- Portrait phone optimization, dedicated long-text data, and large-volume soak matrices remain future hardening; no failure was observed in these secondary areas.
- Production authority creation and Android production signing remain separate operational release tasks: **NOT YET CREATED / CONFIGURED** and **NOT YET CONFIGURED**, respectively.

### POLISH

- Compiler output contains deprecation/opt-in warnings, but lint and all requested builds pass.

## Recommendation

**NOT READY FOR PILOT** until runtime device-binding rejection is actually executed and recorded. No BLOCKER product defect, MAJOR observed product defect, or sale-data-loss defect was found.
