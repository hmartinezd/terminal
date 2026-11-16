# Sales export V2: source pricing breakdown

## Rollout status

V2 is now the default producer used by `SettingsViewModel.prepareExport` for both pending and daily exports, as explicitly requested by the owner. The existing V1 builder remains for compatibility tests. No build flags, user options, version bump or customer APK are added.

Serialization, file handoff and exported-revision bookkeeping remain unchanged. Each newly generated export uses a fresh exportId; existing export bytes are not replaced. Source activation is not distribution: deploy the backend enrichment migration, update Web and Serve Android consumers, then distribute updated Terminal builds. Installed old Serve versions strictly reject V2, so do not distribute the new Terminal first.

## Wire contract

- `format`: `venkoi-serve-sales-export` (unchanged).
- `formatVersion`: **2**.
- Same root, transaction and line fields as V1, plus required `pricingMode` on each line.
- All quantity/money values remain exact decimal strings. Do not use binary floating-point.
- `pricingMode` is a stable string code matching `[A-Z][A-Z0-9_]{0,63}`. Current Terminal produces `CASH` and `TRANSFER` from the saved line, not from discounts. Consumers must preserve syntactically valid future codes and display a fallback label rather than reject or reinterpret them. Localized UI labels are not wire identifiers.
- This represents **source pricing classification**, not bank settlement or a separate payments ledger. No new payment capture or split-tender model is introduced.
- Currency, restaurant, menu, publication, terminal/transaction/line identity, lifecycle and source timestamps remain unchanged. One export still cannot mix currency, business date or publication.
- A void keeps its historical lines and classifications. Summaries include only COMPLETED transactions; show voids separately, as Terminal does.
- Sum line `net` grouped dynamically by mode and currency. For mixed-mode sales, each line contributes only to its own group. Do not classify the whole transaction by its first line, or sum transaction totals into both groups.
- Current request is **amount breakdown**, not per-method transaction counts. Keep existing sale counts unchanged.

## Consumer work (not implemented in this Terminal-only change)

1. Serve must read both V1 and V2 using explicit version dispatch, validate the new field, and preserve it in import models/Room.
2. Update canonical sync payloads/backend validators and persistence, plus back-office strict validators/types, before sending new fields. Preserve all canonical identities, exact replay/void semantics and restaurant isolation.
3. V1/historical records have *unavailable classification*, never default CASH or TRANSFER, and must not infer it from discount.
4. Decide and test V1-to-V2 enrichment for re-exported transactions. Existing immutability/conflict checks must not silently overwrite classified data. Reimporting an old export must never erase newer classification. Do not change inventory-consumption identity or post consumption again.
5. Add simple amount rows under net sales in the import summary and matching back-office detail. Avoid a new configuration/payment workflow.
6. Integration fixtures cover mixed modes, zero-discount cash, void, unknown future code, old V1 and duplicate reimport. Check grouped net equals completed net and keep currencies separate.

## Test fixture

`SalesTerminal/app/src/test/resources/contracts/sales-export-v2.golden.json` is synthetic and asserted against the actual V2 producer (mixed completed sale plus void). Its V1 projection is tested to match existing V1 golden, preserving every old field. No client data is included.
