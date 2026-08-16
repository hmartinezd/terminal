# Sales Terminal R1 Pilot QA Checklist

Record device, app version, tester, date, locale, restaurant timezone/cutoff, and MenuPackage revision. Mark each item Pass, Fail, or Not Run and attach a short note for failures. Use landscape tablets near 1024×600, 1280×800, and 1920×1200 dp where available, plus one portrait phone.

## Startup and configuration

- [ ] Fresh install shows setup promptly, with no blank screen or endless spinner.
- [ ] Valid setup reaches Orders and survives app/process restart.
- [ ] Incomplete or mismatched configuration explains that the terminal is not ready and how reconfiguration may fix it.
- [ ] Existing valid configuration restarts without requesting setup again.

## Menu

- [ ] Import a valid MenuPackage; progress is visible and one success message appears.
- [ ] Invalid/unsupported packages are rejected with a useful employee-facing error and no partial replacement.
- [ ] Import a newer publication; its active menu replaces the prior publication.
- [ ] Inactive items cannot be ordered.
- [ ] Exercise 10+ long category names and 100+ items; chips remain reachable, scrolling is smooth, cards remain stable, and names use at most three lines.
- [ ] Category/card pastel colors match and remain deterministic; an unexpected category renders neutrally rather than crashing.
- [ ] Switch English/Spanish: fixed UI changes, while restaurant/category/item data remains verbatim.

## Orders and recovery

- [ ] Create one order, then rapidly tap New Order; each intentional tap creates at most one order.
- [ ] Create 3 open orders, label each, add different items/quantities, and use CASH, TRANSFER, and mixed pricing.
- [ ] Verify CASH with default discount, CASH without discount, and TRANSFER: discount copy is neutral, totals exact, and mode changes retain snapshots.
- [ ] Switch repeatedly between orders; no line, label, quantity, or pricing change crosses orders.
- [ ] Edit a label at the 100-character boundary; extra input is refused without changing persisted/exported text.
- [ ] Remove a line and verify only the intended line/order changes.
- [ ] Force-stop/terminate and reopen: all 3 orders, labels, quantities, pricing modes, currency, prices, and discount snapshots remain; a sensible order is selected.
- [ ] Complete a sale and rapidly repeat confirmation; exactly one completed sale is created.
- [ ] Simulate completion failure: order remains OPEN with intact lines, no success appears, and retry works.
- [ ] Discard with confirmation and rapidly repeat; only the selected order is discarded.
- [ ] With about 20 multi-line open orders, list/selection stays responsive and persistence/cross-order isolation holds.

## History and voiding

- [ ] Completed sale appears with immutable label/item/price/discount/currency snapshots.
- [ ] VOID changes status reactively while preserving the original historical sale.
- [ ] Rapid or duplicate VOID attempts do not create duplicate effects.
- [ ] Simulated VOID failure leaves COMPLETED status, shows no fake success/badge, and permits retry.
- [ ] A moderate history dataset loads/selects acceptably with exact localized money.
- [ ] Print completed and voided sales; verify print dialog, Save as PDF, pagination, timestamps, and English/Spanish labels.

## Reports and business date

- [ ] Empty date has clear money/product empty states.
- [ ] Verify exact CASH, TRANSFER, mixed-day, VOID, cash-discount, product quantity, and product amount results.
- [ ] Previous/next date and Today work. In America/Havana with 04:00 cutoff, 03:59:59 resolves to prior business date and 04:00:00 to current date.
- [ ] Completion, Reports default, and Export Sales for Day use the same business-date rule; historical dates remain unchanged.
- [ ] Moderate high-volume day remains responsive, exact, single-currency, and printable across pages.
- [ ] Print/Save PDF for money and product reports in English and Spanish.

## Export

- [ ] Pending count matches unexported revisions; zero pending has a clear empty message.
- [ ] Cancel destination selection: no revision is marked exported.
- [ ] Simulate write failure: no revision is marked exported and retry is safe.
- [ ] Successful Pending export creates valid SalesBatchV1 JSON and marks exactly written revisions.
- [ ] VOID an exported sale: pending count returns and revision 2 exports.
- [ ] Simulate bookkeeping failure after file write: message says retry is safe; retry remains duplicate-safe.
- [ ] Export a selected day, re-export it, and export an empty day.
- [ ] Restart preserves pending/export timestamps and revision tracking.
- [ ] Large pilot-scale batch writes deterministically without scientific notation, currency/locale mutation, or memory failure.

## Localization, layout, accessibility, and polish

- [ ] Test System default, English, and Español; restart after each selection.
- [ ] Check navigation, dialogs, orders, history, reports, settings, print labels, and export messages for fixed-language leftovers.
- [ ] Money separators and human dates/times follow app locale; historical/export timestamps use configured restaurant timezone where intended.
- [ ] At each tablet width, NavigationRail and open/current panels remain usable, menu is largest, cards/buttons are touch-friendly, and no important content clips.
- [ ] Portrait phone has no crash or overlapping unusable content.
- [ ] Screen reader announces meaningful navigation, quantity, remove, complete, void, print, export, category, product, and language controls.
- [ ] Long restaurant/terminal/category/item/order names do not overlap; display ellipsis never changes stored/exported values.
- [ ] Normal employee screens avoid raw IDs/technical errors; Terminal ID remains secondary in Settings; App Version matches the installed build.

## End-to-end pilot run

- [ ] Import/provision; create two differently priced orders; complete one; leave one OPEN; restart and recover it.
- [ ] Inspect History and Reports; print the completed sale; export Pending; VOID; confirm report changes; export Pending revision 2; switch to Spanish and inspect key screens.
- [ ] Record which print/export/device steps were actually executed. Do not mark unavailable runtime checks as passed.
