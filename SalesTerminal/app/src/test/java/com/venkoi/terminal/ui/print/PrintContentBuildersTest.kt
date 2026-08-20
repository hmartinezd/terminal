package com.venkoi.terminal.ui.print

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.DailyMoneyCurrencySection
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.ProductReportCurrencySection
import com.venkoi.terminal.domain.model.ProductReportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.Locale

class PrintContentBuildersTest {
    private val labels = PrintLabels("Business Date", "Generated At", "Terminal", "Currency", "Valid Sales", "Voided Sales", "Cash", "Transfer", "Net Sales", "Cash Discounts", "Voided Amount", "Product", "Quantity", "Amount", "Status", "Completed At", "Voided At", "Table", "Pricing Mode", "Unit Price", "Line Total", "Grand Total", "COMPLETED", "VOIDED", "Daily Sales Report", "Product Report", "Sale", "N/A")

    @Test fun `money content uses report totals directly`() {
        val report = DailyMoneyReport(LocalDate.parse("2026-08-10"), listOf(
            DailyMoneyCurrencySection("USD", 2, 1, 1, Money("20"), Money.ZERO, Money("20"), Money("2"), Money("15"))
        ))
        val document = MoneyReportPrintContentBuilder.build(report, "Cafe", "Front", Instant.EPOCH, ZoneOffset.UTC, Locale.US, labels)
        val text = document.lines.joinToString("\n") { it.text }
        assertTrue(text.contains("Net Sales: 20.00 USD"))
        assertTrue(text.contains("Voided Amount: 15.00 USD"))
    }

    @Test fun `long product content retains every row`() {
        val rows = (1..120).map { ProductReportRow("p$it", "Product $it", BigDecimal.ONE, Money("1")) }
        val report = ProductReport(LocalDate.parse("2026-08-10"), listOf(ProductReportCurrencySection("USD", 2, rows)))
        val document = ProductReportPrintContentBuilder.build(report, "Cafe", "Front", Instant.EPOCH, ZoneOffset.UTC, Locale.US, labels)
        assertEquals(120, document.lines.count { it.text.matches(Regex("Product \\d+.*")) })
        assertTrue(document.lines.any { it.text.contains("Product 120") })
    }

    @Test fun `report dates and titles are localized without ISO output`() {
        val report = DailyMoneyReport(LocalDate.parse("2026-08-19"), emptyList())
        val english = MoneyReportPrintContentBuilder.build(
            report,
            "Cafe",
            "Front",
            Instant.parse("2026-08-19T00:00:00Z"),
            ZoneId.of("America/New_York"),
            Locale.US,
            labels
        )
        val spanish = MoneyReportPrintContentBuilder.build(
            report,
            "Cafe",
            "Front",
            Instant.parse("2026-08-19T00:00:00Z"),
            ZoneId.of("America/New_York"),
            Locale.forLanguageTag("es"),
            labels
        )

        assertEquals("Daily Sales Report Aug 19, 2026", english.jobName)
        assertTrue(english.lines.any { it.text == "Business Date: Aug 19, 2026" })
        assertTrue(english.lines.any { it.text.startsWith("Generated At: Aug 18, 2026, 8:00") })
        assertEquals("Daily Sales Report 19 ago 2026", spanish.jobName)
        assertTrue(spanish.lines.any { it.text.startsWith("Generated At: 18 ago 2026,") })
        assertTrue(english.lines.none { it.text.contains("2026-08-19") })
    }
}
