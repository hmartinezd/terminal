package com.venkoi.terminal.ui

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class HistoryPresentationTest {
    @Test
    fun `inserts headers at business date transitions without changing sale order`() {
        val aug19 = LocalDate.of(2026, 8, 19)
        val aug18 = LocalDate.of(2026, 8, 18)
        val aug17 = LocalDate.of(2026, 8, 17)
        val entries = historyPresentationEntries(
            listOf(sale("A", aug19), sale("B", aug19), sale("C", aug18), sale("D", aug18), sale("E", aug17))
        )

        assertEquals(
            listOf("header:2026-08-19", "sale:A", "sale:B", "header:2026-08-18", "sale:C", "sale:D", "header:2026-08-17", "sale:E"),
            entries.map(::description)
        )
    }

    @Test
    fun `null business date creates a safe header and repeated dates follow source sequence`() {
        val aug19 = LocalDate.of(2026, 8, 19)
        val entries = historyPresentationEntries(listOf(sale("A", aug19), sale("B", null), sale("C", null), sale("D", aug19)))

        assertEquals(
            listOf("header:2026-08-19", "sale:A", "header:null", "sale:B", "sale:C", "header:2026-08-19", "sale:D"),
            entries.map(::description)
        )
        assertEquals(listOf("A", "B", "C", "D"), entries.filterIsInstance<HistoryPresentationEntry.SaleRow>().map { it.item.sale.saleId.value })
    }

    @Test
    fun `fifty plus sales across several dates keep every row in source order`() {
        val source = (0 until 55).map { index ->
            sale("sale-$index", LocalDate.of(2026, 8, 19).minusDays((index / 10).toLong()))
        }

        val entries = historyPresentationEntries(source)

        assertEquals(6, entries.filterIsInstance<HistoryPresentationEntry.BusinessDateHeader>().size)
        assertEquals(
            source.map { it.sale.saleId },
            entries.filterIsInstance<HistoryPresentationEntry.SaleRow>().map { it.item.sale.saleId }
        )
    }

    @Test
    fun `after-midnight timestamp is grouped by persisted prior business date`() {
        val priorBusinessDate = LocalDate.of(2026, 8, 19)
        val entries = historyPresentationEntries(listOf(sale("after-midnight", priorBusinessDate)))

        assertEquals(
            priorBusinessDate,
            entries.filterIsInstance<HistoryPresentationEntry.BusinessDateHeader>().single().businessDate
        )
    }

    private fun description(entry: HistoryPresentationEntry): String = when (entry) {
        is HistoryPresentationEntry.BusinessDateHeader -> "header:${entry.businessDate}"
        is HistoryPresentationEntry.SaleRow -> "sale:${entry.item.sale.saleId.value}"
    }

    private fun sale(id: String, businessDate: LocalDate?) = SaleWithTotal(
        sale = Sale(
            saleId = SaleId(id),
            terminalId = TerminalId("terminal"),
            openedAtUtc = Instant.parse("2026-08-20T01:00:00Z"),
            updatedAtUtc = Instant.parse("2026-08-20T02:00:00Z"),
            tableLabel = id,
            status = SaleStatus.COMPLETED,
            completedAtUtc = Instant.parse("2026-08-20T02:00:00Z"),
            businessDate = businessDate,
            currencyCodeSnapshot = "USD",
            currencyScaleSnapshot = 2
        ),
        grandTotal = Money(BigDecimal.TEN)
    )
}
