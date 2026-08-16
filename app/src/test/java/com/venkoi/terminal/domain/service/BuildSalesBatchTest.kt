package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BuildSalesBatchTest {
    private val builder = BuildSalesBatch()

    @Test fun `maps persisted void snapshot exactly and uses deterministic line order`() {
        val sale = snapshot(SaleStatus.VOIDED, 2)
        val batch = builder("restaurant", "terminal", "batch", Instant.parse("2026-08-16T02:45:00Z"), listOf(sale))
        val dto = batch.sales.single()
        assertEquals("VOIDED", dto.status.name)
        assertEquals(2, dto.revision)
        assertEquals("Patio", dto.tableLabel)
        assertEquals("USD", dto.currencyCodeSnapshot)
        assertEquals(3, dto.currencyScaleSnapshot)
        assertEquals(listOf("a", "z"), dto.lines.map { it.lineId })
        assertEquals("0.333", dto.lines.first().quantity)
        assertEquals(Money("1000000.125"), dto.lines.first().lineTotal)
        val encoded = Json.encodeToString(batch)
        assertTrue(encoded.contains("1000000.125"))
        assertTrue(!encoded.contains("E+"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid completed lifecycle revision`() {
        builder("restaurant", "terminal", "batch", Instant.EPOCH, listOf(snapshot(SaleStatus.COMPLETED, 2)))
    }

    private fun snapshot(status: SaleStatus, revision: Int): SaleWithLines {
        val saleId = SaleId("sale")
        val sale = Sale(saleId, TerminalId("terminal"), Instant.EPOCH, Instant.EPOCH, "Patio", status,
            revision, Instant.parse("2026-08-10T12:00:00Z"),
            if (status == SaleStatus.VOIDED) Instant.parse("2026-08-15T12:00:00Z") else null,
            LocalDate.parse("2026-08-10"), "USD", 3)
        fun line(id: String, quantity: String, total: String) = SaleLine(
            LineId(id), saleId, "item-$id", 7, 9, "Historical item $id", BigDecimal(quantity), Money("10.00"),
            CashDiscountMode.APPLY_DEFAULT, BigDecimal("10.00"), PricingMode.CASH, true,
            BigDecimal("10.00"), Money("1.00"), Money("9.00"), Money(total)
        )
        return SaleWithLines(sale, listOf(line("z", "1", "9.00"), line("a", "0.333", "1000000.125")))
    }
}
