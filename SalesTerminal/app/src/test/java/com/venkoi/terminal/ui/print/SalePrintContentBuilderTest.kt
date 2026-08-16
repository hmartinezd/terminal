package com.venkoi.terminal.ui.print

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.service.CalculateOrderTotals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class SalePrintContentBuilderTest {
    private val labels = PrintLabels("Business Date", "Generated At", "Terminal", "Currency", "Valid Sales", "Voided Sales", "Cash", "Transfer", "Net Sales", "Cash Discounts", "Voided Amount", "Product", "Quantity", "Amount", "Status", "Completed At", "Voided At", "Table", "Pricing Mode", "Unit Price", "Line Total", "Grand Total", "COMPLETED", "VOIDED", "Daily Sales Report", "Product Report", "Sale")

    @Test fun `completed sale content contains immutable commercial detail without internals`() {
        val lines = listOf(line(1, PricingMode.CASH, "Burger", "9.00", "1.00"), line(2, PricingMode.TRANSFER, "Juice", "5.00"))
        val document = build(sale(SaleStatus.COMPLETED), lines)
        val text = document.lines.joinToString("\n") { it.text }
        listOf("Cafe", "Terminal: Front", "Table: Patio 4", "Business Date: 2026-08-10", "Completed At:",
            "Status: COMPLETED", "Burger  x1", "Juice  x1", "Pricing Mode: Cash", "Pricing Mode: Transfer",
            "Unit Price:", "Cash Discounts: 1.00 USD", "Line Total:", "Cash: 9.00 USD", "Transfer: 5.00 USD",
            "Grand Total: 14.00 USD", "Currency: USD").forEach { assertTrue("missing $it", text.contains(it)) }
        assertFalse(text.contains("full-internal-sale-id"))
        assertFalse(text.contains("revision"))
    }

    @Test fun `voided sale preserves original details and is unmistakable`() {
        val original = sale(SaleStatus.VOIDED)
        val lines = listOf(line(1, PricingMode.CASH, "Original Burger", "9.00"))
        val before = original.copy()
        val text = build(original, lines).lines.joinToString("\n") { it.text }
        assertTrue(text.contains("VOIDED"))
        assertTrue(text.contains("Completed At:"))
        assertTrue(text.contains("Voided At:"))
        assertTrue(text.contains("Original Burger"))
        assertTrue(text.contains("Grand Total: 9.00 USD"))
        assertTrue(original == before)
    }

    @Test fun `long sale layout retains header every item and totals`() {
        val lines = (1..80).map { line(it, PricingMode.CASH, "Item $it", "1.00") }
        val document = build(sale(SaleStatus.COMPLETED), lines)
        val plan = PrintLayoutPlanner.plan(document, 60f, 20f,
            PrintTextMeasurer { text, _ -> text.length.toFloat() }, PrintLineHeightProvider { 1f })
        val text = plan.pages.flatMap { it.lines }.joinToString("\n") { it.text }
        assertTrue(plan.pages.size > 1)
        assertTrue(text.contains("Cafe"))
        (1..80).forEach { assertTrue(text.contains("Item $it  x1")) }
        assertTrue(text.contains("Grand Total: 80.00 USD"))
    }

    private fun build(sale: Sale, lines: List<SaleLine>) = SalePrintContentBuilder.build(
        sale, lines, CalculateOrderTotals.calculate(lines), "Cafe", "Front", ZoneOffset.UTC, Locale.US, labels
    )

    private fun sale(status: SaleStatus) = Sale(
        SaleId("full-internal-sale-id"), TerminalId("terminal"), Instant.parse("2026-08-10T12:00:00Z"),
        Instant.parse("2026-08-12T12:00:00Z"), "Patio 4", status, 27,
        Instant.parse("2026-08-10T12:00:00Z"), if (status == SaleStatus.VOIDED) Instant.parse("2026-08-12T12:00:00Z") else null,
        LocalDate.parse("2026-08-10"), "USD", 2
    )

    private fun line(index: Int, mode: PricingMode, name: String, total: String, discount: String = "0") = SaleLine(
        LineId("line-$index"), SaleId("full-internal-sale-id"), "item-$index", 99, 88, name, BigDecimal.ONE,
        Money(if (discount == "0") total else BigDecimal(total).add(BigDecimal(discount)).toPlainString()),
        CashDiscountMode.APPLY_DEFAULT, BigDecimal.TEN, mode, discount != "0", BigDecimal.TEN,
        Money(discount), Money(total), Money(total)
    )
}
