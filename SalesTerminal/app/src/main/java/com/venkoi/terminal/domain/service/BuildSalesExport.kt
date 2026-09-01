package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.integration.sales.SALES_EXPORT_FORMAT
import com.venkoi.terminal.integration.sales.SALES_EXPORT_FORMAT_VERSION
import com.venkoi.terminal.integration.sales.SalesExportLineV1
import com.venkoi.terminal.integration.sales.SalesExportStatus
import com.venkoi.terminal.integration.sales.SalesExportTransactionV1
import com.venkoi.terminal.integration.sales.SalesExportV1
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class BuildSalesExport @Inject constructor() {
    operator fun invoke(
        restaurantId: String,
        terminalId: String,
        exportId: String,
        generatedAt: Instant,
        sales: List<SaleWithLines>
    ): SalesExportV1 {
        require(restaurantId.isNotBlank()) { "restaurantId must not be blank" }
        require(terminalId.isNotBlank()) { "terminalId must not be blank" }
        require(exportId.isNotBlank()) { "exportId must not be blank" }
        require(sales.isNotEmpty()) { "Sales export must contain at least one transaction" }

        val firstSale = sales.first().sale
        val businessDate = requireNotNull(firstSale.businessDate) {
            "Sale ${firstSale.saleId.value} has no business date"
        }
        val saleRestaurantId = requireNotNull(firstSale.restaurantIdSnapshot) {
            "Sale ${firstSale.saleId.value} has no immutable restaurant provenance"
        }
        val menuId = requireNotNull(firstSale.menuIdSnapshot) {
            "Sale ${firstSale.saleId.value} has no immutable menu provenance"
        }
        val publicationRevision = requireNotNull(firstSale.publicationRevisionSnapshot) {
            "Sale ${firstSale.saleId.value} has no immutable publication provenance"
        }
        require(saleRestaurantId == restaurantId) {
            "Sale ${firstSale.saleId.value} belongs to restaurant $saleRestaurantId, not $restaurantId"
        }
        require(menuId.isNotBlank()) { "Sale ${firstSale.saleId.value} has a blank menu ID" }
        require(publicationRevision > 0) {
            "Sale ${firstSale.saleId.value} has an invalid publication revision"
        }

        val currency = firstSale.currencyCodeSnapshot
        require(currency.isNotBlank()) { "Sale ${firstSale.saleId.value} has no currency code" }

        val transactions = sales
            .sortedWith(compareBy({ it.sale.businessDate }, { closedAt(it.sale) }, { it.sale.saleId.value }))
            .map { mapTransaction(it, terminalId, restaurantId, businessDate, menuId, publicationRevision, currency) }

        return SalesExportV1(
            format = SALES_EXPORT_FORMAT,
            formatVersion = SALES_EXPORT_FORMAT_VERSION,
            exportId = exportId,
            terminalId = terminalId,
            restaurantId = restaurantId,
            generatedAt = generatedAt.toString(),
            businessDate = businessDate.toString(),
            menuId = menuId,
            publicationRevision = publicationRevision,
            currency = currency,
            transactions = transactions
        )
    }

    private fun mapTransaction(
        value: SaleWithLines,
        terminalId: String,
        restaurantId: String,
        businessDate: java.time.LocalDate,
        menuId: String,
        publicationRevision: Int,
        currency: String
    ): SalesExportTransactionV1 {
        val sale = value.sale
        require(sale.saleId.value.isNotBlank()) { "transactionId must not be blank" }
        require(sale.terminalId.value == terminalId) {
            "Sale ${sale.saleId.value} belongs to terminal ${sale.terminalId.value}, not $terminalId"
        }
        require(sale.restaurantIdSnapshot == restaurantId) {
            "Sale ${sale.saleId.value} has mismatched restaurant provenance"
        }
        require(sale.menuIdSnapshot == menuId && sale.publicationRevisionSnapshot == publicationRevision) {
            "Sale ${sale.saleId.value} has mismatched publication provenance"
        }
        require(sale.businessDate == businessDate) { "SalesExport V1 cannot mix business dates" }
        require(sale.currencyCodeSnapshot == currency) { "SalesExport V1 cannot mix currencies" }
        require(sale.status == SaleStatus.COMPLETED || sale.status == SaleStatus.VOIDED) {
            "Sale ${sale.saleId.value} is not exportable"
        }
        val revision = requireNotNull(sale.revision) { "Sale ${sale.saleId.value} has no lifecycle revision" }
        val closedAt = closedAt(sale)
        require(closedAt >= sale.openedAtUtc) { "Sale ${sale.saleId.value} closed before it opened" }
        when (sale.status) {
            SaleStatus.COMPLETED -> require(revision == 1 && sale.voidedAtUtc == null) {
                "COMPLETED sale ${sale.saleId.value} has inconsistent lifecycle data"
            }
            SaleStatus.VOIDED -> require(revision == 2 && sale.completedAtUtc != null) {
                "VOIDED sale ${sale.saleId.value} has inconsistent lifecycle data"
            }
            else -> error("Unreachable")
        }
        require(value.lines.isNotEmpty()) { "Sale ${sale.saleId.value} has no lines" }

        return SalesExportTransactionV1(
            transactionId = sale.saleId.value,
            openedAt = sale.openedAtUtc.toString(),
            closedAt = closedAt.toString(),
            status = SalesExportStatus.valueOf(sale.status.name),
            lines = value.lines.sortedBy { it.lineId.value }.map { line -> mapLine(line, sale.saleId.value) }
        )
    }

    private fun mapLine(line: com.venkoi.terminal.domain.model.SaleLine, saleId: String): SalesExportLineV1 {
        require(line.lineId.value.isNotBlank()) { "saleLineId must not be blank" }
        require(line.saleId.value == saleId) {
            "Line ${line.lineId.value} belongs to sale ${line.saleId.value}, not $saleId"
        }
        require(line.menuItemId.isNotBlank()) { "Line ${line.lineId.value} has no sellable item ID" }
        require(line.itemNameSnapshot.isNotBlank()) { "Line ${line.lineId.value} has no item name" }
        require(line.quantity.signum() > 0) { "Line ${line.lineId.value} has invalid quantity" }
        require(line.commercialRevision > 0 && line.consumptionRevision > 0) {
            "Line ${line.lineId.value} has invalid revisions"
        }

        val quantity = line.quantity
        val unitPrice = line.regularUnitPriceSnapshot.amount
        val gross = quantity.multiply(unitPrice)
        val discount = line.cashDiscountAmount.amount
        val net = gross.subtract(discount)
        require(unitPrice >= BigDecimal.ZERO) { "Line ${line.lineId.value} has a negative unit price" }
        require(discount >= BigDecimal.ZERO && discount <= gross) {
            "Line ${line.lineId.value} has an invalid discount"
        }
        require(net >= BigDecimal.ZERO) { "Line ${line.lineId.value} has a negative net" }
        require(net.compareTo(line.lineTotal.amount) == 0) {
            "Line ${line.lineId.value} has inconsistent net arithmetic"
        }

        return SalesExportLineV1(
            saleLineId = line.lineId.value,
            sellableItemId = line.menuItemId,
            displayNameSnapshot = line.itemNameSnapshot,
            quantity = quantity.toPlainString(),
            unitPrice = unitPrice.toPlainString(),
            gross = gross.toPlainString(),
            discount = discount.toPlainString(),
            net = net.toPlainString(),
            commercialRevision = line.commercialRevision,
            consumptionRevision = line.consumptionRevision
        )
    }

    private fun closedAt(sale: com.venkoi.terminal.domain.model.Sale): Instant = when (sale.status) {
        SaleStatus.COMPLETED -> requireNotNull(sale.completedAtUtc) {
            "Sale ${sale.saleId.value} has no completion timestamp"
        }
        SaleStatus.VOIDED -> requireNotNull(sale.voidedAtUtc) {
            "Sale ${sale.saleId.value} has no void timestamp"
        }
        else -> error("Sale ${sale.saleId.value} is not exportable")
    }
}
