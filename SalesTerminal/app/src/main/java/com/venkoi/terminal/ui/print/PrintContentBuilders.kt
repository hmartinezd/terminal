package com.venkoi.terminal.ui.print

import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.service.OrderTotals
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter
import com.venkoi.terminal.ui.util.TerminalDateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object MoneyReportPrintContentBuilder {
    fun build(
        report: DailyMoneyReport,
        restaurantName: String,
        terminalName: String,
        generatedAt: Instant,
        timezone: ZoneId,
        locale: Locale,
        labels: PrintLabels
    ): PrintDocumentModel {
        val lines = header(restaurantName, terminalName, report.businessDate, generatedAt, timezone, locale, labels)
        report.currencySections.forEach { section ->
            lines += PrintLine("${labels.currency}: ${section.currencyCode}", PrintEmphasis.HEADING)
            lines += PrintLine("${labels.validSales}: ${section.validSaleCount}")
            lines += PrintLine("${labels.voidedSales}: ${section.voidedSaleCount}", PrintEmphasis.VOIDED)
            lines += moneyLine(labels.cash, section.cashTotal, section.currencyCode, section.currencyScale, locale = locale)
            lines += moneyLine(labels.transfer, section.transferTotal, section.currencyCode, section.currencyScale, locale = locale)
            lines += moneyLine(labels.netSales, section.grandTotal, section.currencyCode, section.currencyScale, PrintEmphasis.STRONG, locale)
            lines += moneyLine(labels.cashDiscounts, section.cashDiscounts, section.currencyCode, section.currencyScale, locale = locale)
            lines += moneyLine(labels.voidedAmount, section.voidedAmount, section.currencyCode, section.currencyScale, PrintEmphasis.VOIDED, locale)
            lines += PrintLine("")
        }
        return PrintDocumentModel(
            "${labels.dailySalesReport} ${TerminalDateFormatter.formatDate(report.businessDate, locale)}",
            lines
        )
    }
}

object ProductReportPrintContentBuilder {
    fun build(
        report: ProductReport,
        restaurantName: String,
        terminalName: String,
        generatedAt: Instant,
        timezone: ZoneId,
        locale: Locale,
        labels: PrintLabels
    ): PrintDocumentModel {
        val lines = header(restaurantName, terminalName, report.businessDate, generatedAt, timezone, locale, labels)
        report.currencySections.forEach { section ->
            lines += PrintLine("${labels.currency}: ${section.currencyCode}", PrintEmphasis.HEADING)
            lines += PrintLine("${labels.product} | ${labels.quantity} | ${labels.amount}", PrintEmphasis.STRONG)
            section.rows.forEach { row ->
                val amount = HistoryMoneyFormatter.format(row.amount, section.currencyCode, section.currencyScale, locale)
                lines += PrintLine("${row.itemNameSnapshot} | ${row.quantity.stripTrailingZeros().toPlainString()} | $amount")
            }
            lines += PrintLine("")
        }
        return PrintDocumentModel(
            "${labels.productReport} ${TerminalDateFormatter.formatDate(report.businessDate, locale)}",
            lines
        )
    }
}

object SalePrintContentBuilder {
    fun build(
        sale: Sale,
        saleLines: List<SaleLine>,
        totals: OrderTotals,
        restaurantName: String,
        terminalName: String,
        timezone: ZoneId,
        locale: Locale,
        labels: PrintLabels
    ): PrintDocumentModel {
        val code = sale.currencyCodeSnapshot
        val scale = sale.currencyScaleSnapshot
        val displayBusinessDate = sale.businessDate
            ?.let { TerminalDateFormatter.formatDate(it, locale) }
            ?: labels.notAvailable
        val lines = mutableListOf(PrintLine(restaurantName, PrintEmphasis.HEADING), PrintLine("${labels.terminal}: $terminalName"))
        if (sale.status == SaleStatus.VOIDED) lines += PrintLine(labels.voided, PrintEmphasis.VOIDED)
        sale.tableLabel?.takeIf(String::isNotBlank)?.let { lines += PrintLine("${labels.table}: $it") }
        lines += PrintLine("${labels.businessDate}: $displayBusinessDate")
        sale.completedAtUtc?.let {
            lines += PrintLine("${labels.completedAt}: ${TerminalDateFormatter.formatDateTime(it, timezone, locale)}")
        }
        sale.voidedAtUtc?.let {
            lines += PrintLine(
                "${labels.voidedAt}: ${TerminalDateFormatter.formatDateTime(it, timezone, locale)}",
                PrintEmphasis.VOIDED
            )
        }
        lines += PrintLine("${labels.status}: ${if (sale.status == SaleStatus.VOIDED) labels.voided else labels.completed}")
        lines += PrintLine("${labels.currency}: $code")
        lines += PrintLine("")
        saleLines.forEach { line ->
            lines += PrintLine("${line.itemNameSnapshot}  x${line.quantity.stripTrailingZeros().toPlainString()}", PrintEmphasis.STRONG)
            lines += PrintLine("${labels.pricingMode}: ${if (line.pricingMode == PricingMode.CASH) labels.cash else labels.transfer}")
            lines += PrintLine("${labels.unitPrice}: ${HistoryMoneyFormatter.format(line.regularUnitPriceSnapshot, code, scale, locale)}")
            if (line.cashDiscountAmount.amount.signum() != 0) {
                lines += PrintLine("${labels.cashDiscounts}: ${HistoryMoneyFormatter.format(line.cashDiscountAmount, code, scale, locale)}")
            }
            lines += PrintLine("${labels.lineTotal}: ${HistoryMoneyFormatter.format(line.lineTotal, code, scale, locale)}")
        }
        lines += PrintLine("")
        lines += PrintLine("${labels.cash}: ${HistoryMoneyFormatter.format(totals.cashTotal, code, scale, locale)}")
        lines += PrintLine("${labels.transfer}: ${HistoryMoneyFormatter.format(totals.transferTotal, code, scale, locale)}")
        lines += PrintLine("${labels.grandTotal}: ${HistoryMoneyFormatter.format(totals.grandTotal, code, scale, locale)}", PrintEmphasis.STRONG)
        return PrintDocumentModel(
            "${labels.sale} $displayBusinessDate",
            lines
        )
    }
}

private fun header(
    restaurantName: String,
    terminalName: String,
    businessDate: LocalDate,
    generatedAt: Instant,
    timezone: ZoneId,
    locale: Locale,
    labels: PrintLabels
): MutableList<PrintLine> {
    return mutableListOf(
        PrintLine(restaurantName, PrintEmphasis.HEADING),
        PrintLine("${labels.terminal}: $terminalName"),
        PrintLine("${labels.businessDate}: ${TerminalDateFormatter.formatDate(businessDate, locale)}"),
        PrintLine("${labels.generatedAt}: ${TerminalDateFormatter.formatDateTime(generatedAt, timezone, locale)}"),
        PrintLine("")
    )
}

private fun moneyLine(
    label: String,
    money: com.venkoi.terminal.core.Money,
    code: String,
    scale: Int,
    emphasis: PrintEmphasis = PrintEmphasis.NORMAL,
    locale: Locale = Locale.getDefault()
) = PrintLine("$label: ${HistoryMoneyFormatter.format(money, code, scale, locale)}", emphasis)
