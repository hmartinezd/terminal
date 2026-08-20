# Historical M11C Device-Binding Validation / Pilot Readiness Evidence

Date: 2026-08-16 (America/New_York)  
Historical status at execution: **READY FOR CONTROLLED PILOT** — all then-current automated gates and executed tablet-first critical runtime paths passed, including real rejection of Device A's unchanged signed license on an independently created Device B. This section records the application and security behavior that existed on 2026-08-16; it is not evidence that the later tablet redesign or authenticated clock-recovery closeout had already been exercised.
Validation build: debug with `ENFORCE_LICENSE_IN_DEBUG=true` and a temporary DEVELOPMENT P-256 authority. No production credentials were created.

## Automated verification

| Component | Result | Evidence |
|---|---|---|
| License Contract | PASS | M11C clean `build` and publication succeeded; 2/2 tests passed, including the canonical V1 golden vector. |
| License Admin | PASS | M11C clean JVM `build` succeeded; 9/9 issuance, renewal, mismatch, tamper, and compatibility tests passed. |
| Sales Terminal | PASS | M11C `assembleDebug`, 89/89 JVM tests, `lintDebug`, `compileDebugAndroidTestKotlin`, and R8-minified `assembleRelease` succeeded with a non-production public key and placeholder certificate digest. Prior normal-debug device instrumentation remains 47/47 PASS. |

The enforced-license instrumentation APK was also run. Seven repository integration tests failed because they intentionally rely on debug developer authorization and were run under enforcement without installing a license; the same 47-test suite passed under its normal debug test configuration. This is recorded as a test-configuration limitation, not a product runtime failure.

## Runtime target and evidence

- PASS — Pixel Tablet AVD, 2560×1600 landscape: clean install, cold start, menu setup, navigation rail, three-panel Orders layout, Subscription UI, disabled selling before activation, activation, restart, one completed sale, History, renewal, and stale rejection.
- HISTORICAL PASS — Clock rollback fail-closed behavior at M11C: a stale AVD snapshot placed the guest clock eight minutes behind the issuer; the accepted license remained unauthorized with a localized clock warning. The then-observed cold-boot recovery predates authenticated trusted-time recovery and is not current operational guidance. Current behavior requires corrected Android time plus a valid signed higher-sequence recovery license, as documented in the current addendum below.
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
| 9 | Copy to another device | PASS | RUNTIME: a new `Device_B_M11C` AVD was created from scratch with an independent data partition/Android Keystore. Device A `deviceKeyId` `rwSaphCXrHIF0yBwupytnRTeZdaDW686GWspDBHCRP8` differed from Device B `UdN6t0W6oeeP348qkp9C5kBeZEnKOUUm8Rdwtf83POo`. Device A's exact unchanged signed sequence-4 license was transferred and rejected on Device B with `Unable to verify license`; status remained `Activation required`, New Order and product controls remained disabled, and attempted selling created no sale. A newly issued sequence-1 DEVELOPMENT license bound to Device B was then accepted, status became `Active`, and selling controls became enabled. |
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
| 55 | Full restaurant pilot scenario | PASS | All tablet-first lifecycle steps ran across M11/M11B/M11C: offline activation, independent-device binding rejection and positive control, multiple orders/recovery, CASH/TRANSFER, completion, History, VOID, Reports, print/PDF, pending/day/revision-2 exports, Spanish/system default, no-network, expiration/read-only, and renewal. |
| 56 | Severity classification | PASS | Remaining gaps below are classified. |
| 57–59 | Readiness / acceptance | READY FOR CONTROLLED PILOT | All critical tablet-first paths and the real independent-device binding check pass with no known data-loss defect or BLOCKER/MAJOR product defect. |

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
| Offline Licensing | READY | Activation, renewal, stale rejection, rollback fail-closed, expiration/read-only/resume, no-network operation, real independent-device rejection, and correctly bound Device-B acceptance passed. |
| License Admin | READY | Build/tests and real development operations passed. |
| Security Boundary | READY | Automated and source/build guard evidence passed; Device A and Device B had independent Keystore-derived identities, and the unchanged Device-A license was rejected offline on Device B. |
| Automated Verification | READY | All required gates passed. |

## Remaining issues

### BLOCKER

- None found.

### MAJOR

- None found.

### MINOR

- The on-device instrumentation suite assumes the normal debug developer authorization; running that suite unchanged in an enforced-license APK produces seven expected authorization failures. A dedicated licensed-test fixture would be needed if enforced instrumentation is made a formal gate.
- Historical M11C observation only: the saved Medium Tablet quick-boot snapshot had a stale guest clock and the then-current implementation recovered after a cold boot synchronized time. Authenticated trusted-time behavior supersedes this; cold boot or simple clock correction is not sufficient current recovery.
- Portrait phone optimization, dedicated long-text data, and large-volume soak matrices remain future hardening; no failure was observed in these secondary areas.
- Production authority creation and Android production signing remain separate operational release tasks: **NOT YET CREATED / CONFIGURED** and **NOT YET CONFIGURED**, respectively.

### POLISH

- Compiler output contains deprecation/opt-in warnings, but lint and all requested builds pass.

## M11C rejection integrity evidence

- The failed Device-A import did not crash the application and did not store a license envelope on Device B.
- Before/after database evidence remained: 1 restaurant configuration, 1 terminal configuration, 1 published menu, 4 categories, 11 menu items, 0 sales, 0 sale lines, and 0 export-state rows.
- Restaurant ID `venkoi-demo` and Device-B terminal ID `b3a1b97a-2326-4977-9a7b-03be1452df72` were unchanged. The displayed Device Code also remained unchanged.
- Device A remained authorized with New Order enabled after Device B's rejection, confirming the source license remained usable on its bound device.
- Sales Terminal's product-source private-key guard passed. No authority private key exists in or is accessed by the shipped Android application; signing remained confined to License Admin.

## Recommendation

**READY FOR CONTROLLED PILOT.** No BLOCKER product defect, MAJOR product defect, or sale-data-loss defect was found. Production authority creation and Android production signing remain separate operational release tasks and were not fabricated for this validation.

## R1 final polish validation — compact Current Order and Save/Share

Date: 2026-08-16 (America/New_York)

The latest debug APK was exercised on the Pixel Tablet at 2560×1600/320 dpi and at a representative 1280×800/160 dpi. A Spanish footer wrap found during runtime inspection was fixed with two compact equal-width mode columns; no totals or monetary logic changed.

### Current Order

| Scenario | Result | Evidence |
|---|---|---|
| Large landscape tablet | PASS | Spanish mixed-mode order remained readable; item list was the only scrolling region and totals, Grand Total, Complete, and Discard stayed fixed. |
| 1280×800 effective landscape | PASS | One- and four-line states remained usable without clipping; fixed footer/actions stayed visible. |
| Long order (8+ lines) | PASS | Last item was fully reachable by scrolling only the item list; no footer/list overlap. |
| Long product name | NOT RUN | No dedicated long-name menu item was available in the installed immutable demo menu. Source constrains names to two lines, but this row is not claimed as runtime evidence. |
| Spanish totals | PASS | `EFECTIVO` and `TRANSFERENCIA` rendered as equal-width label/value columns without clipping or value ellipsis after the presentation fix. |
| Large amounts | NOT RUN | No large-value runtime fixture was injected; important totals are no longer ellipsized, but this row is not claimed as runtime evidence. |
| Expired state | NOT RUN | The installed emulator license reported a license problem rather than a reproducible expired state; prior M11C expired-order evidence was not reused as proof of this new compact layout. |

### Sales Share

| Scenario | Result | Evidence |
|---|---|---|
| Pending Share | PASS | Android chooser opened with `application/json`, a `content://` attachment, and a sensible `.json` filename; exact prepared revision became non-pending after handoff. |
| VOID revision-2 Share | PASS | Shared JSON retained sale ID `eee3e613-039f-4021-8302-e980b59bddcc`, contained revision `2`, status `VOIDED`, and the persisted revision-1 line snapshots. |
| Day Share | PASS | Android chooser opened with the repeatable business-day SalesBatchV1 JSON attachment. |
| Save regression | PASS | Day and Pending Save opened DocumentsUI; Pending cancellation left one revision pending, and retry wrote `sales_pending_2026-08-16_215722.json`. |
| Airplane-mode chooser | PASS | Airplane mode remained enabled (`airplane_mode_on=1`) with no connected network while Pending, VOID, and Day share choosers opened. |
| Expired-license Share | NOT RUN | A reproducible expired license could not be established on this emulator without replacing its installed licensing fixture. Static UI/coordinator review found no selling-authorization gate on historical export. |
| Spanish UI | PASS | Runtime chooser showed `Elegir método de exportación`, `Guardar archivo`, and `Compartir`; no English leftover was visible in the exercised flow. |

Save and Share both consumed the same `PreparedSalesExport.json`. SalesBatchV1 and receiver-side code/schema were unchanged. Sharing remained private-cache `FileProvider` transport with an unexported provider, `content://` URI, JSON MIME type, and temporary read permission.

## Current R1 post-redesign and security-closeout addendum

Date: 2026-08-20 (America/New_York)

This addendum describes the candidate being prepared for the final production artifact ceremony. Historical M11/M11B/M11C runtime statements above remain historical and must not be read as post-redesign execution evidence.

### Current controlled-pilot baseline

- Application identity: `com.venkoi.terminal`, version `1.0.0-pilot.1`, version code `1`.
- Database: Room schema `5`, the intended first distributed production baseline. No distributed schema-4 upgrade origin exists. All later schema changes require explicit, tested, non-destructive migrations; release has no destructive fallback.
- Orders: overlay application drawer, compact top bar, horizontal stable OPEN-order strip with New Order first, approximately 400dp Current Order, compact line controls, header Cancel Order, and fixed totals/completion footer.
- History: approximately 400dp dense master pane, Business Date grouping, time-only transaction rows, Order/Closed/Amount/Status columns, persistent sale detail, dense ITEM/QTY/MODE/AMOUNT ledger, Sale Ref, Print/Save PDF, and historical VOID.
- Reports: compact Business Date navigation with always-actionable Today/Hoy, Money/Products segmented selection, Net Sales/Cash/Transfer primary summary, compact audit metrics, and dense lazy Products table.
- Settings: 260dp master/detail subsection pane for Language, Subscription, Sales Export, Restaurant, Terminal, Menu, and App Information.

The architecture statements above were reconciled against current source. They do not manufacture new screenshot evidence. Earlier runtime evidence for compact Current Order and Save/Share remains recorded in its dated section; older NavigationRail, vertical Open Orders, card-layout, and scrolling-Settings observations describe only the historical build exercised at that time.

### Current security runtime evidence

The following suites executed on the Medium Tablet Android 15 AVD on 2026-08-20:

| Gate | Result | Current evidence |
|---|---|---|
| `LicenseManagerRecoveryTest` | PASS — 7/7, 0 failed, 0 skipped | Authenticated trusted-time state; five-minute future-issued tolerance boundary; monotonic trusted time; signed downward re-anchor; interrupted recovery continuation using the exact installed verified payload; Duplicate after completed recovery; SequenceConflict; Stale; invalid signature/product/restaurant/terminal/device; expired recovery rejection; invalid authenticated local state; and preservation of installed envelope/trusted state on rejection. |
| `SellingAuthorizationRepositoryIntegrationTest` | PASS — 3/3, 0 failed, 0 skipped | All seven repository mutations denied with unchanged persisted state under both EXPIRED and CLOCK_ROLLBACK_DETECTED; previously valid snapshot cannot authorize a later restricted mutation because `LicenseManager.requireSelling()` reevaluates current state; Cancel Order/discard and historical VOID remain allowed. |
| Relevant Android regression | PASS — 12/12, 0 failed, 0 skipped | Sale lifecycle, completion concurrency, durable order/sale readback, report DAO, export DAO, and Android Keystore-backed device identity persistence. |

The selling-denial matrix passed for `createSale`, `updateSaleLabel`, `addItem`, `updateLineQuantity`, `changeLinePricingMode`, `removeLine`, and `completeSale` in each restricted state. Clock-specific, expired, and generic security-state messages exist in both English and Spanish resources; fresh compilation and lint passed.

Current clock recovery is fail-closed. Correcting Android time, restarting, or cold booting does not itself clear `CLOCK_ROLLBACK_DETECTED`. After correcting device time, the operator imports an administrator-issued, device-bound, higher-sequence signed license; validation then securely re-anchors authenticated trusted time. If license persistence completed but re-anchoring was interrupted, re-import of the exact same verified installed license may resume recovery when its sequence remains above the authenticated highest sequence and all validation still passes. Later rollback remains detectable through normal monotonic trusted-time operation.

`LOCAL_SECURITY_STATE_INVALID` is separate from expiration and ordinary clock correction. It blocks selling, uses generic security-state guidance, and has no documented manual reset or bypass.

### Fresh automated release-readiness verification

| Component | Result |
|---|---|
| License Contract | PASS — clean build; 2/2 tests passed. |
| License Admin | PASS — clean build; 9/9 tests passed. |
| Sales Terminal | PASS — clean `assembleDebug`, all JVM tests, `lintDebug`, `compileDebugAndroidTestKotlin`, and R8-minified/resource-shrunk non-production `assembleRelease`. |
| Product-source private-material guard | PASS — executed during Android builds. |
| Release package controls | PASS — `/compatibility/**` exclusion remains configured; FileProvider JSON share infrastructure and immutable prepared-export reuse remain present. |

Final APK filename/hash, signing-certificate hash, production authority fingerprint, build timestamp, and production configuration remain **PENDING** for the separate final production build/sign/verify/hash ceremony.
