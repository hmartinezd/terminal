package com.venkoi.terminal.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val saleId: SaleId,
    val terminalId: TerminalId,
    val openedAtUtc: Instant,
    val updatedAtUtc: Instant,
    val tableLabel: String?,
    val status: SaleStatus,
    val revision: Int?,
    val completedAtUtc: Instant?,
    val voidedAtUtc: Instant?,
    val businessDate: LocalDate?,
    val currencyCodeSnapshot: String,
    val currencyScaleSnapshot: Int
) {
    fun toDomain() = Sale(
        saleId = saleId,
        terminalId = terminalId,
        openedAtUtc = openedAtUtc,
        updatedAtUtc = updatedAtUtc,
        tableLabel = tableLabel,
        status = status,
        revision = revision,
        completedAtUtc = completedAtUtc,
        voidedAtUtc = voidedAtUtc,
        businessDate = businessDate,
        currencyCodeSnapshot = currencyCodeSnapshot,
        currencyScaleSnapshot = currencyScaleSnapshot
    )

    companion object {
        fun fromDomain(sale: Sale) = SaleEntity(
            saleId = sale.saleId,
            terminalId = sale.terminalId,
            openedAtUtc = sale.openedAtUtc,
            updatedAtUtc = sale.updatedAtUtc,
            tableLabel = sale.tableLabel,
            status = sale.status,
            revision = sale.revision,
            completedAtUtc = sale.completedAtUtc,
            voidedAtUtc = sale.voidedAtUtc,
            businessDate = sale.businessDate,
            currencyCodeSnapshot = sale.currencyCodeSnapshot,
            currencyScaleSnapshot = sale.currencyScaleSnapshot
        )
    }
}

@Entity(tableName = "sale_lines")
data class SaleLineEntity(
    @PrimaryKey val lineId: LineId,
    val saleId: SaleId,
    val menuItemId: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    val itemNameSnapshot: String,
    val quantity: BigDecimal,
    val regularUnitPriceSnapshot: Money,
    val cashDiscountModeSnapshot: com.venkoi.terminal.domain.model.CashDiscountMode,
    val cashDiscountPolicyPercentSnapshot: BigDecimal,
    val pricingMode: PricingMode,
    val cashDiscountApplied: Boolean,
    val cashDiscountPercent: BigDecimal,
    val cashDiscountAmount: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money
) {
    fun toDomain() = SaleLine(
        lineId = lineId,
        saleId = saleId,
        menuItemId = menuItemId,
        commercialRevision = commercialRevision,
        consumptionRevision = consumptionRevision,
        itemNameSnapshot = itemNameSnapshot,
        quantity = quantity,
        regularUnitPriceSnapshot = regularUnitPriceSnapshot,
        cashDiscountModeSnapshot = cashDiscountModeSnapshot,
        cashDiscountPolicyPercentSnapshot = cashDiscountPolicyPercentSnapshot,
        pricingMode = pricingMode,
        cashDiscountApplied = cashDiscountApplied,
        cashDiscountPercent = cashDiscountPercent,
        cashDiscountAmount = cashDiscountAmount,
        finalUnitPrice = finalUnitPrice,
        lineTotal = lineTotal
    )

    companion object {
        fun fromDomain(line: SaleLine) = SaleLineEntity(
            lineId = line.lineId,
            saleId = line.saleId,
            menuItemId = line.menuItemId,
            commercialRevision = line.commercialRevision,
            consumptionRevision = line.consumptionRevision,
            itemNameSnapshot = line.itemNameSnapshot,
            quantity = line.quantity,
            regularUnitPriceSnapshot = line.regularUnitPriceSnapshot,
            cashDiscountModeSnapshot = line.cashDiscountModeSnapshot,
            cashDiscountPolicyPercentSnapshot = line.cashDiscountPolicyPercentSnapshot,
            pricingMode = line.pricingMode,
            cashDiscountApplied = line.cashDiscountApplied,
            cashDiscountPercent = line.cashDiscountPercent,
            cashDiscountAmount = line.cashDiscountAmount,
            finalUnitPrice = line.finalUnitPrice,
            lineTotal = line.lineTotal
        )
    }
}
