package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import java.math.BigDecimal
import java.time.Instant

enum class OrderStatus {
    OPEN,
    DISCARDED
}

data class OpenOrder(
    val saleId: SaleId,
    val terminalId: TerminalId,
    val openedAtUtc: Instant,
    val updatedAtUtc: Instant,
    val tableLabel: String?,
    val status: OrderStatus
)

data class OpenOrderLine(
    val lineId: LineId,
    val saleId: SaleId,
    val menuItemId: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    val itemNameSnapshot: String,
    val quantity: BigDecimal,
    val regularUnitPriceSnapshot: Money,
    val cashDiscountModeSnapshot: CashDiscountMode,
    val pricingMode: PricingMode,
    val cashDiscountApplied: Boolean,
    val cashDiscountPercentSnapshot: BigDecimal,
    val cashDiscountAmount: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money
)
