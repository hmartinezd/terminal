package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.integration.sales.SalesExportLineV1
import com.venkoi.terminal.integration.sales.SalesExportTransactionV1
import com.venkoi.terminal.integration.sales.SalesExportV1
import com.venkoi.terminal.integration.sales.SALES_EXPORT_FORMAT
import com.venkoi.terminal.integration.sales.SALES_EXPORT_FORMAT_VERSION
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class BuildSalesBatch @Inject constructor() {
    operator fun invoke(
        restaurantId: String,
        terminalId: String,
        batchId: String,
        exportedAtUtc: Instant,
        sales: List<SaleWithLines>
    ): SalesExportV1 {
        require(restaurantId.isNotBlank()) { "restaurantId must not be blank" }
        require(terminalId.isNotBlank()) { "terminalId must not be blank" }
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(sales.isNotEmpty()) { "Sales export must contain at least one sale" }

        val orderedSales = sales.sortedWith(compareBy({ it.sale.businessDate }, { it.sale.completedAtUtc }, { it.sale.saleId.value }))
        val first = orderedSales.first().sale
        val businessDate = requireNotNull(first.businessDate) { "Sale ${first.saleId.value} has no business date" }
        val menuId = requireNotNull(first.menuId?.takeIf { it.isNotBlank() }) {
            "Sale ${first.saleId.value} has no menu provenance"
        }
        val publicationRevision = requireNotNull(first.publicationRevision?.takeIf { it > 0 }) {
            "Sale ${first.saleId.value} has no publication provenance"
        }
        require(orderedSales.all { it.sale.businessDate == businessDate }) {
            "Sales export must contain one business date"
        }
        require(orderedSales.all { it.sale.menuId == menuId && it.sale.publicationRevision == publicationRevision }) {
            "Sales export must contain one menu publication"
        }
        require(orderedSales.all { it.sale.currencyCodeSnapshot == first.currencyCodeSnapshot }) {
            "Sales export must contain one currency"
        }

        return SalesExportV1(
            format = SALES_EXPORT_FORMAT,
            formatVersion = SALES_EXPORT_FORMAT_VERSION,
            exportId = batchId,
            terminalId = terminalId,
            restaurantId = restaurantId,
            generatedAt = exportedAtUtc.toString(),
            businessDate = businessDate.toString(),
            menuId = menuId,
            publicationRevision = publicationRevision.toLong(),
            currency = first.currencyCodeSnapshot,
            transactions = orderedSales
                .map { mapSale(it, terminalId) }
        )
    }

    private fun mapSale(value: SaleWithLines, terminalId: String): SalesExportTransactionV1 {
        val sale = value.sale
        require(sale.saleId.value.isNotBlank()) { "saleId must not be blank" }
        require(sale.terminalId.value == terminalId) {
            "Sale ${sale.saleId.value} belongs to terminal ${sale.terminalId.value}, not $terminalId"
        }
        require(sale.status == com.venkoi.terminal.domain.model.SaleStatus.COMPLETED ||
            sale.status == com.venkoi.terminal.domain.model.SaleStatus.VOIDED) { "Sale ${sale.saleId.value} is not exportable" }
        val revision = requireNotNull(sale.revision) { "Sale ${sale.saleId.value} has no lifecycle revision" }
        val completed = requireNotNull(sale.completedAtUtc) { "Sale ${sale.saleId.value} has no completion timestamp" }
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

        return SalesExportTransactionV1(
            transactionId = sale.saleId.value,
            openedAt = sale.openedAtUtc.toString(),
            closedAt = completed.toString(),
            status = sale.status.name,
            lines = value.lines.sortedBy { it.lineId.value }.map { line ->
                require(line.lineId.value.isNotBlank()) { "lineId must not be blank" }
                require(line.saleId == sale.saleId) {
                    "Line ${line.lineId.value} belongs to sale ${line.saleId.value}, not ${sale.saleId.value}"
                }
                require(line.menuItemId.isNotBlank()) { "Line ${line.lineId.value} has no menu item ID" }
                require(line.quantity.signum() > 0) { "Line ${line.lineId.value} has invalid quantity" }
                require(line.commercialRevision > 0 && line.consumptionRevision > 0) { "Line ${line.lineId.value} has invalid revisions" }
                require(line.itemNameSnapshot.isNotBlank()) { "Line ${line.lineId.value} has no item name" }
                val gross = line.regularUnitPriceSnapshot.amount.multiply(line.quantity)
                val discount = line.cashDiscountAmount.amount
                val net = line.lineTotal.amount
                require(net.compareTo(gross.subtract(discount)) == 0) {
                    "Line ${line.lineId.value} has inconsistent gross, discount, and net"
                }
                SalesExportLineV1(
                    saleLineId = line.lineId.value,
                    sellableItemId = line.menuItemId,
                    displayNameSnapshot = line.itemNameSnapshot,
                    quantity = line.quantity.toPlainString(),
                    unitPrice = line.regularUnitPriceSnapshot.amount.toPlainString(),
                    gross = gross.toPlainString(),
                    discount = discount.toPlainString(),
                    net = net.toPlainString(),
                    commercialRevision = line.commercialRevision.toLong(),
                    consumptionRevision = line.consumptionRevision.toLong()
                )
            }
        )
    }
}
