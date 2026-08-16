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

    @Test fun `maps completed revision one persisted fields exactly`() {
        val source = snapshot(SaleStatus.COMPLETED, 1)
        val dto = builder(
            "restaurant", "terminal", "batch", Instant.parse("2026-08-16T03:00:00Z"), listOf(source)
        ).sales.single()

        assertEquals("sale", dto.saleId)
        assertEquals(1, dto.revision)
        assertEquals("COMPLETED", dto.status.name)
        assertEquals("Patio", dto.tableLabel)
        assertEquals("USD", dto.currencyCodeSnapshot)
        assertEquals(3, dto.currencyScaleSnapshot)
        source.lines.sortedBy { it.lineId.value }.zip(dto.lines).forEach { (persisted, exported) ->
            assertEquals(persisted.lineId.value, exported.lineId)
            assertEquals(persisted.menuItemId, exported.menuItemId)
            assertEquals(persisted.commercialRevision, exported.commercialRevision)
            assertEquals(persisted.consumptionRevision, exported.consumptionRevision)
            assertEquals(persisted.itemNameSnapshot, exported.itemNameSnapshot)
            assertEquals(persisted.quantity.toPlainString(), exported.quantity)
            assertEquals(persisted.regularUnitPriceSnapshot, exported.regularUnitPriceSnapshot)
            assertEquals(persisted.pricingMode.name, exported.pricingMode.name)
            assertEquals(persisted.cashDiscountApplied, exported.cashDiscountApplied)
            assertEquals(persisted.cashDiscountPercent.toPlainString(), exported.cashDiscountPercentSnapshot)
            assertEquals(persisted.cashDiscountAmount, exported.cashDiscountAmountSnapshot)
            assertEquals(persisted.finalUnitPrice, exported.finalUnitPriceSnapshot)
            assertEquals(persisted.lineTotal, exported.lineTotal)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a sale from another terminal`() {
        val value = snapshot(SaleStatus.COMPLETED, 1)
        builder("restaurant", "other-terminal", "batch", Instant.EPOCH, listOf(value))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a line belonging to another sale`() {
        val value = snapshot(SaleStatus.COMPLETED, 1)
        val mismatched = value.copy(lines = value.lines.toMutableList().also {
            it[0] = it[0].copy(saleId = SaleId("different-sale"))
        })
        builder("restaurant", "terminal", "batch", Instant.EPOCH, listOf(mismatched))
    }

    @Test fun `duplicate builds change envelope identity but not persisted sale payload`() {
        val source = snapshot(SaleStatus.COMPLETED, 1)
        val first = builder("restaurant", "terminal", "batch-1", Instant.EPOCH, listOf(source))
        val second = builder("restaurant", "terminal", "batch-2", Instant.EPOCH.plusSeconds(1), listOf(source))

        assertTrue(first.batchId != second.batchId)
        assertTrue(first.exportedAtUtc != second.exportedAtUtc)
        assertEquals(first.sales, second.sales)
        assertEquals(SaleStatus.COMPLETED, source.sale.status)
        assertEquals(1, source.sale.revision)
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
