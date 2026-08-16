package com.venkoi.terminal.data.dto

import com.venkoi.terminal.core.Money
import kotlinx.serialization.Serializable

@Serializable
data class SalesBatchV1(
    val schemaVersion: Int = 1,
    val sourceSystem: String = "SalesTerminal",
    val restaurantId: String,
    val terminalId: String,
    val batchId: String,
    val exportedAtUtc: String,
    val sales: List<SaleDto>
) {
    init {
        require(schemaVersion == 1) { "Unsupported SalesBatch schema version: $schemaVersion" }
    }
}

@Serializable
data class SaleDto(
    val saleId: String,
    val revision: Int,
    val status: String, // COMPLETED, VOIDED
    val openedAtUtc: String,
    val completedAtUtc: String?,
    val voidedAtUtc: String?,
    val businessDate: String, // ISO date "YYYY-MM-DD"
    val tableNumber: String?,
    val lines: List<SaleLineDto>
)

@Serializable
data class SaleLineDto(
    val lineId: String,
    val menuItemId: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    val itemNameSnapshot: String,
    val quantity: String, // BigDecimal as String
    val regularUnitPriceSnapshot: Money,
    val pricingMode: String, // CASH, TRANSFER
    val cashDiscountApplied: Boolean,
    val cashDiscountPercentSnapshot: String,
    val cashDiscountAmountSnapshot: Money,
    val finalUnitPriceSnapshot: Money,
    val lineTotal: Money
)
