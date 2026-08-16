package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class SaleStatus {
    OPEN,
    DISCARDED,
    COMPLETED,
    VOIDED
}

data class Sale(
    val saleId: SaleId,
    val terminalId: TerminalId,
    val openedAtUtc: Instant,
    val updatedAtUtc: Instant,
    val tableLabel: String?,
    val status: SaleStatus,
    val revision: Int? = null,
    val completedAtUtc: Instant? = null,
    val voidedAtUtc: Instant? = null,
    val businessDate: LocalDate? = null,
    val currencyCodeSnapshot: String,
    val currencyScaleSnapshot: Int
)

data class SaleLine(
    val lineId: LineId,
    val saleId: SaleId,
    val menuItemId: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    val itemNameSnapshot: String,
    val quantity: BigDecimal,
    val regularUnitPriceSnapshot: Money,
    val cashDiscountModeSnapshot: CashDiscountMode,
    val cashDiscountPolicyPercentSnapshot: BigDecimal,
    val pricingMode: PricingMode,
    val cashDiscountApplied: Boolean,
    val cashDiscountPercent: BigDecimal,
    val cashDiscountAmount: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money
)

data class SaleWithLines(
    val sale: Sale,
    val lines: List<SaleLine>
)
