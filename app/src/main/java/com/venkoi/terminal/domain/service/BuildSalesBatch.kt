package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.integration.sales.PricingMode
import com.venkoi.terminal.integration.sales.SaleDto
import com.venkoi.terminal.integration.sales.SaleLineDto
import com.venkoi.terminal.integration.sales.SaleStatus
import com.venkoi.terminal.integration.sales.SalesBatchV1
import java.time.Instant
import javax.inject.Inject

class BuildSalesBatch @Inject constructor() {
    operator fun invoke(
        restaurantId: String,
        terminalId: String,
        batchId: String,
        exportedAtUtc: Instant,
        sales: List<SaleWithLines>
    ): SalesBatchV1 {
        require(restaurantId.isNotBlank()) { "restaurantId must not be blank" }
        require(terminalId.isNotBlank()) { "terminalId must not be blank" }
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(sales.isNotEmpty()) { "Sales export must contain at least one sale" }

        return SalesBatchV1(
            restaurantId = restaurantId,
            terminalId = terminalId,
            batchId = batchId,
            exportedAtUtc = exportedAtUtc.toString(),
            sales = sales.sortedWith(compareBy({ it.sale.businessDate }, { it.sale.completedAtUtc }, { it.sale.saleId.value }))
                .map { mapSale(it, terminalId) }
        )
    }

    private fun mapSale(value: SaleWithLines, terminalId: String): SaleDto {
        val sale = value.sale
        require(sale.saleId.value.isNotBlank()) { "saleId must not be blank" }
        require(sale.terminalId.value == terminalId) {
            "Sale ${sale.saleId.value} belongs to terminal ${sale.terminalId.value}, not $terminalId"
        }
        require(sale.status == com.venkoi.terminal.domain.model.SaleStatus.COMPLETED ||
            sale.status == com.venkoi.terminal.domain.model.SaleStatus.VOIDED) { "Sale ${sale.saleId.value} is not exportable" }
        val revision = requireNotNull(sale.revision) { "Sale ${sale.saleId.value} has no lifecycle revision" }
        val completed = requireNotNull(sale.completedAtUtc) { "Sale ${sale.saleId.value} has no completion timestamp" }
        val date = requireNotNull(sale.businessDate) { "Sale ${sale.saleId.value} has no business date" }
        when (sale.status) {
            com.venkoi.terminal.domain.model.SaleStatus.COMPLETED -> {
                require(revision == 1 && sale.voidedAtUtc == null) { "COMPLETED sale ${sale.saleId.value} has inconsistent lifecycle data" }
            }
            com.venkoi.terminal.domain.model.SaleStatus.VOIDED -> {
                require(revision == 2 && sale.voidedAtUtc != null) { "VOIDED sale ${sale.saleId.value} has inconsistent lifecycle data" }
            }
            else -> error("Unreachable")
        }
        require(value.lines.isNotEmpty()) { "Sale ${sale.saleId.value} has no lines" }
        require(sale.currencyCodeSnapshot.isNotBlank()) { "Sale ${sale.saleId.value} has no currency code" }
        require(sale.currencyScaleSnapshot >= 0) { "Sale ${sale.saleId.value} has invalid currency scale" }

        return SaleDto(
            saleId = sale.saleId.value,
            revision = revision,
            status = SaleStatus.valueOf(sale.status.name),
            openedAtUtc = sale.openedAtUtc.toString(),
            completedAtUtc = completed.toString(),
            voidedAtUtc = sale.voidedAtUtc?.toString(),
            businessDate = date.toString(),
            tableLabel = sale.tableLabel,
            currencyCodeSnapshot = sale.currencyCodeSnapshot,
            currencyScaleSnapshot = sale.currencyScaleSnapshot,
            lines = value.lines.sortedBy { it.lineId.value }.map { line ->
                require(line.lineId.value.isNotBlank()) { "lineId must not be blank" }
                require(line.saleId == sale.saleId) {
                    "Line ${line.lineId.value} belongs to sale ${line.saleId.value}, not ${sale.saleId.value}"
                }
                require(line.menuItemId.isNotBlank()) { "Line ${line.lineId.value} has no menu item ID" }
                require(line.quantity.signum() > 0) { "Line ${line.lineId.value} has invalid quantity" }
                require(line.commercialRevision > 0 && line.consumptionRevision > 0) { "Line ${line.lineId.value} has invalid revisions" }
                require(line.itemNameSnapshot.isNotBlank()) { "Line ${line.lineId.value} has no item name" }
                SaleLineDto(
                    lineId = line.lineId.value,
                    menuItemId = line.menuItemId,
                    commercialRevision = line.commercialRevision,
                    consumptionRevision = line.consumptionRevision,
                    itemNameSnapshot = line.itemNameSnapshot,
                    quantity = line.quantity.toPlainString(),
                    regularUnitPriceSnapshot = line.regularUnitPriceSnapshot,
                    pricingMode = PricingMode.valueOf(line.pricingMode.name),
                    cashDiscountApplied = line.cashDiscountApplied,
                    cashDiscountPercentSnapshot = line.cashDiscountPercent.toPlainString(),
                    cashDiscountAmountSnapshot = line.cashDiscountAmount,
                    finalUnitPriceSnapshot = line.finalUnitPrice,
                    lineTotal = line.lineTotal
                )
            }
        )
    }
}
