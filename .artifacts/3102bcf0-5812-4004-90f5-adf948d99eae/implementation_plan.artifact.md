# Implementation Plan - M4: Completion + Immutable History + Void

Implement the full sale lifecycle, including completion, immutable history, and voiding, while ensuring data persistence and integrity.

## User Review Required

> [!IMPORTANT]
> The database schema will be significantly refactored to unify "Open Orders" and "Sales" into a single conceptual model. This involves renaming tables and entities. Development data will be cleared as per the policy.

## Proposed Changes

### Domain Layer

#### [MODIFY] [OrderModels.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/domain/model/OrderModels.kt)
- Rename `OrderStatus` to `SaleStatus` and add `COMPLETED` and `VOIDED`.
- Rename `OpenOrder` to `Sale`.
- Add fields to `Sale`: `completedAtUtc`, `voidedAtUtc`, `businessDate`, `revision`, `currencyCodeSnapshot`, `currencyScaleSnapshot`.
- Rename `OpenOrderLine` to `SaleLine`.

#### [NEW] [CompleteSale.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/domain/service/CompleteSale.kt)
- Implement `CompleteSale` domain service to handle the completion logic.
- Input: `saleId`.
- Steps: Load sale, verify state, calculate totals, resolve business date, atomic persistence.

#### [NEW] [VoidSale.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/domain/service/VoidSale.kt)
- Implement `VoidSale` domain service for the transition from `COMPLETED` to `VOIDED`.
- Ensure idempotency.

### Data Layer

#### [MODIFY] [OrderEntities.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/data/local/database/OrderEntities.kt)
- Rename `OpenOrderEntity` to `SaleEntity` and `OpenOrderLineEntity` to `SaleLineEntity`.
- Update table names to `sales` and `sale_lines`.
- Add new fields to `SaleEntity` as specified in the domain model.

#### [MODIFY] [OrderDao.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/data/local/database/OrderDao.kt)
- Rename to `SaleDao`.
- Update all queries to use new table names and `SaleStatus`.
- Add `getSaleSync` and `getSaleLinesSync` for transactional use.
- Add `observeHistorySales()` to observe `COMPLETED` and `VOIDED` sales.

#### [MODIFY] [AppDatabase.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/data/local/database/AppDatabase.kt)
- Update entity list and DAO names.
- Increment database version to 3.

#### [MODIFY] [OrderRepository.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/domain/repository/OrderRepository.kt)
- Rename to `SaleRepository`.
- Add `completeSale(saleId)` and `voidSale(saleId)`.
- Update existing methods to work with the renamed models.

#### [MODIFY] [RoomOrderRepository.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/data/local/repository/RoomOrderRepository.kt)
- Rename to `RoomSaleRepository`.
- Implement `completeSale` and `voidSale` using the new DAOs and domain services.
- Ensure all mutations on `COMPLETED` or `VOIDED` sales are rejected.

### UI Layer

#### [MODIFY] [OrdersViewModel.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/OrdersViewModel.kt)
- Add `completeSale()` method.
- Handle loading state and errors during completion.

#### [MODIFY] [OrdersScreen.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/OrdersScreen.kt)
- Add "Complete Sale" button.
- Implement confirmation dialog with totals.
- Handle success/failure feedback.

#### [NEW] [HistoryViewModel.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/HistoryViewModel.kt)
- Manage state for the History screen.
- Observe completed/voided sales.

#### [NEW] [HistoryScreen.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/HistoryScreen.kt)
- Implement the list view for sale history.
- Navigation to detail view.

#### [NEW] [HistoryDetailScreen.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/HistoryDetailScreen.kt)
- Implement the detailed view of a historical sale.
- Add "Void Sale" action for `COMPLETED` sales.

#### [MODIFY] [MainScreen.kt](file:///Users/hector/Projects/Terminal/app/src/main/java/com/venkoi/terminal/ui/MainScreen.kt)
- Wire up `HistoryScreen` and `HistoryDetailScreen`.

## Verification Plan

### Automated Tests
- **Unit Tests**:
    - `CompleteSaleTest`: Verify success, empty sale, non-existent sale, DISCARDED sale, business date resolution.
    - `VoidSaleTest`: Verify success, idempotency, original data preservation.
    - `SaleRepositoryTest`: Verify that `COMPLETED` sales cannot be mutated.
- **Instrumentation Tests**:
    - `SaleLifecycleTest`: End-to-end flow from OPEN to COMPLETED to VOIDED.
    - `HistoryPersistenceTest`: Verify history survives app restart.
    - `MenuChangeHistoryTest`: Verify history displays original prices after menu update.

### Manual Verification
- Create an order, add items, complete it.
- Verify it moves from Orders to History.
- Verify totals in confirmation dialog match the order.
- Void a sale and verify status change in History.
- Change menu prices and verify History still shows old prices.
- Restart the app and verify History is preserved.
