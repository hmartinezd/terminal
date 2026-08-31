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
        val dto = batch.transactions.single()
        assertEquals("VOIDED", dto.status)
        assertEquals("menu", batch.menuId)
        assertEquals(3L, batch.publicationRevision)
        assertEquals("USD", batch.currency)
        assertEquals(listOf("a", "z"), dto.lines.map { it.saleLineId })
        assertEquals("0.333", dto.lines.first().quantity)
        assertEquals("10.00", dto.lines.first().unitPrice)
        assertEquals("3.33000", dto.lines.first().gross)
        assertEquals("0.33000", dto.lines.first().discount)
        assertEquals("3.000", dto.lines.first().net)
        val encoded = com.venkoi.terminal.integration.sales.SalesExportJsonCodec.encode(batch)
        assertTrue(encoded.contains("3.000"))
        assertTrue(!encoded.contains("E+"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid completed lifecycle revision`() {
        builder("restaurant", "terminal", "batch", Instant.EPOCH, listOf(snapshot(SaleStatus.COMPLETED, 2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects legacy sale without immutable menu provenance`() {
        val legacy = snapshot(SaleStatus.COMPLETED, 1).copy(sale = snapshot(SaleStatus.COMPLETED, 1).sale.copy(menuId = null, publicationRevision = null))
        builder("restaurant", "terminal", "batch", Instant.EPOCH, listOf(legacy))
    }

    @Test fun `maps completed revision one persisted fields exactly`() {
        val source = snapshot(SaleStatus.COMPLETED, 1)
        val dto = builder(
            "restaurant", "terminal", "batch", Instant.parse("2026-08-16T03:00:00Z"), listOf(source)
        ).transactions.single()

        assertEquals("sale", dto.transactionId)
        assertEquals("COMPLETED", dto.status)
        source.lines.sortedBy { it.lineId.value }.zip(dto.lines).forEach { (persisted, exported) ->
            assertEquals(persisted.lineId.value, exported.saleLineId)
            assertEquals(persisted.menuItemId, exported.sellableItemId)
            assertEquals(persisted.commercialRevision.toLong(), exported.commercialRevision)
            assertEquals(persisted.consumptionRevision.toLong(), exported.consumptionRevision)
            assertEquals(persisted.itemNameSnapshot, exported.displayNameSnapshot)
            assertEquals(persisted.quantity.toPlainString(), exported.quantity)
            assertEquals(persisted.regularUnitPriceSnapshot.amount.toPlainString(), exported.unitPrice)
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

        assertTrue(first.exportId != second.exportId)
        assertTrue(first.generatedAt != second.generatedAt)
        assertEquals(first.transactions, second.transactions)
        assertEquals(SaleStatus.COMPLETED, source.sale.status)
        assertEquals(1, source.sale.revision)
    }

    private fun snapshot(status: SaleStatus, revision: Int): SaleWithLines {
        val saleId = SaleId("sale")
        val sale = Sale(saleId, TerminalId("terminal"), Instant.EPOCH, Instant.EPOCH, "Patio", status,
            revision, Instant.parse("2026-08-10T12:00:00Z"),
            if (status == SaleStatus.VOIDED) Instant.parse("2026-08-15T12:00:00Z") else null,
            LocalDate.parse("2026-08-10"), "USD", 3, "menu", 3)
        fun line(id: String, quantity: String, total: String): SaleLine {
            val quantityValue = BigDecimal(quantity)
            val gross = BigDecimal("10.00").multiply(quantityValue)
            val net = BigDecimal(total)
            val discount = gross.subtract(net)
            return SaleLine(
            LineId(id), saleId, "item-$id", 7, 9, "Historical item $id", BigDecimal(quantity), Money("10.00"),
            CashDiscountMode.APPLY_DEFAULT, BigDecimal("10.00"), PricingMode.CASH, true,
            BigDecimal("10.00"), Money(discount), Money("9.00"), Money(total)
            )
        }
        return SaleWithLines(sale, listOf(line("z", "1", "9.00"), line("a", "0.333", "3.000")))
    }
}
