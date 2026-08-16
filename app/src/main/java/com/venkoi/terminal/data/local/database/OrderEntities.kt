package com.venkoi.terminal.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.OpenOrder
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.OrderStatus
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "open_orders")
data class OpenOrderEntity(
    @PrimaryKey val saleId: SaleId,
    val terminalId: TerminalId,
    val openedAtUtc: Instant,
    val updatedAtUtc: Instant,
    val tableLabel: String?,
    val status: OrderStatus
) {
    fun toDomain() = OpenOrder(
        saleId = saleId,
        terminalId = terminalId,
        openedAtUtc = openedAtUtc,
        updatedAtUtc = updatedAtUtc,
        tableLabel = tableLabel,
        status = status
    )

    companion object {
        fun fromDomain(order: OpenOrder) = OpenOrderEntity(
            saleId = order.saleId,
            terminalId = order.terminalId,
            openedAtUtc = order.openedAtUtc,
            updatedAtUtc = order.updatedAtUtc,
            tableLabel = order.tableLabel,
            status = order.status
        )
    }
}

@Entity(tableName = "open_order_lines")
data class OpenOrderLineEntity(
    @PrimaryKey val lineId: LineId,
    val saleId: SaleId,
    val menuItemId: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    val itemNameSnapshot: String,
    val quantity: BigDecimal,
    val regularUnitPriceSnapshot: Money,
    val cashDiscountModeSnapshot: com.venkoi.terminal.domain.model.CashDiscountMode,
    val pricingMode: PricingMode,
    val cashDiscountApplied: Boolean,
    val cashDiscountPercentSnapshot: BigDecimal,
    val cashDiscountAmount: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money
) {
    fun toDomain() = OpenOrderLine(
        lineId = lineId,
        saleId = saleId,
        menuItemId = menuItemId,
        commercialRevision = commercialRevision,
        consumptionRevision = consumptionRevision,
        itemNameSnapshot = itemNameSnapshot,
        quantity = quantity,
        regularUnitPriceSnapshot = regularUnitPriceSnapshot,
        cashDiscountModeSnapshot = cashDiscountModeSnapshot,
        pricingMode = pricingMode,
        cashDiscountApplied = cashDiscountApplied,
        cashDiscountPercentSnapshot = cashDiscountPercentSnapshot,
        cashDiscountAmount = cashDiscountAmount,
        finalUnitPrice = finalUnitPrice,
        lineTotal = lineTotal
    )

    companion object {
        fun fromDomain(line: OpenOrderLine) = OpenOrderLineEntity(
            lineId = line.lineId,
            saleId = line.saleId,
            menuItemId = line.menuItemId,
            commercialRevision = line.commercialRevision,
            consumptionRevision = line.consumptionRevision,
            itemNameSnapshot = line.itemNameSnapshot,
            quantity = line.quantity,
            regularUnitPriceSnapshot = line.regularUnitPriceSnapshot,
            cashDiscountModeSnapshot = line.cashDiscountModeSnapshot,
            pricingMode = line.pricingMode,
            cashDiscountApplied = line.cashDiscountApplied,
            cashDiscountPercentSnapshot = line.cashDiscountPercentSnapshot,
            cashDiscountAmount = line.cashDiscountAmount,
            finalUnitPrice = line.finalUnitPrice,
            lineTotal = line.lineTotal
        )
    }
}
