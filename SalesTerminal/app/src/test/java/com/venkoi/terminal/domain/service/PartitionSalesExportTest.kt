package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.data.file.DocumentWriteResult
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.PreparedSalesExport
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PartitionSalesExportTest {
    private val partition = PartitionSalesExport()

    @Test
    fun `pending sales are split by every immutable envelope field in deterministic order`() {
        val dayA = sale("a", LocalDate.parse("2026-08-10"), "menu-1", 1)
        val dayB = sale("b", LocalDate.parse("2026-08-11"), "menu-1", 1)
        val publication2 = sale("c", LocalDate.parse("2026-08-10"), "menu-2", 2)
        val legacy = sale("legacy", LocalDate.parse("2026-08-09"), "menu-1", 1).copy(
            sale = sale("legacy", LocalDate.parse("2026-08-09"), "menu-1", 1).sale.copy(menuIdSnapshot = null)
        )

        val result = partition(listOf(publication2, legacy, dayB, dayA))

        assertEquals(
            listOf(
                listOf("a"),
                listOf("c"),
                listOf("b")
            ), result.canonicalGroups.map { group -> group.sales.map { it.sale.saleId.value } }
        )
        assertEquals(listOf("legacy"), result.legacyUnsupported.map { it.sale.saleId.value })
        assertEquals("menu-1", result.canonicalGroups.first().key.menuId)
        assertEquals(1, result.canonicalGroups.first().key.publicationRevision)
    }

    @Test
    fun `one group can be handed off and only its exact revisions are selected`() {
        val first = sale("first", LocalDate.parse("2026-08-10"), "menu-1", 1)
        val second = sale("second", LocalDate.parse("2026-08-11"), "menu-1", 1)
        val groups = partition(listOf(second, first)).canonicalGroups

        assertEquals(listOf("first"), groups.first().sales.map { it.sale.saleId.value })
        assertEquals(listOf("second"), groups[1].sales.map { it.sale.saleId.value })
        var marked = emptyList<ExportedSaleRevision>()
        val prepared = PreparedSalesExport(
            json = "{}", exportId = "export-1", exportedAtUtc = Instant.EPOCH,
            revisions = groups.first().sales.map { ExportedSaleRevision(it.sale.saleId, requireNotNull(it.sale.revision)) },
            suggestedFileName = "sales.json"
        )
        val handoff = kotlinx.coroutines.runBlocking {
            ExportSalesCoordinator().execute(
                destination = "file",
                prepared = prepared,
                write = { _, _ -> DocumentWriteResult.Success },
                markExactRevisions = { marked = it.revisions }
            )
        }
        assertEquals(ExportCoordinationResult.Success, handoff)
        assertEquals(listOf(ExportedSaleRevision(SaleId("first"), 1)), marked)
        assertEquals(listOf(SaleId("first")), groups.first().sales.map { it.sale.saleId })
        assertEquals(listOf(SaleId("second")), groups[1].sales.map { it.sale.saleId })
        assertNull(partition(listOf(first.copy(sale = first.sale.copy(restaurantIdSnapshot = null))))
            .canonicalGroups.firstOrNull())
    }

    @Test
    fun `same day publications each build as an individually canonical envelope`() {
        val groups = partition(
            listOf(
                sale("publication-1", LocalDate.parse("2026-08-10"), "menu-1", 1),
                sale("publication-2", LocalDate.parse("2026-08-10"), "menu-2", 2)
            )
        ).canonicalGroups

        groups.forEachIndexed { index, group ->
            val export = BuildSalesExport()(
                restaurantId = group.key.restaurantId,
                terminalId = "terminal-1",
                exportId = "export-$index",
                generatedAt = Instant.EPOCH,
                sales = group.sales
            )
            assertEquals(group.key.menuId, export.menuId)
            assertEquals(group.key.publicationRevision, export.publicationRevision)
            assertEquals(listOf(group.sales.single().sale.saleId.value), export.transactions.map { it.transactionId })
        }
    }

    private fun sale(id: String, date: LocalDate, menuId: String, publication: Int): SaleWithLines {
        val saleId = SaleId(id)
        val time = Instant.parse("${date}T12:00:00Z")
        return SaleWithLines(
            Sale(
                saleId = saleId,
                terminalId = TerminalId("terminal-1"),
                openedAtUtc = time.minusSeconds(60),
                updatedAtUtc = time,
                tableLabel = null,
                status = SaleStatus.COMPLETED,
                revision = 1,
                completedAtUtc = time,
                businessDate = date,
                currencyCodeSnapshot = "USD",
                currencyScaleSnapshot = 2,
                restaurantIdSnapshot = "restaurant-1",
                menuIdSnapshot = menuId,
                publicationRevisionSnapshot = publication
            ),
            listOf(
                SaleLine(
                    lineId = LineId("line-$id"), saleId = saleId, menuItemId = "item",
                    commercialRevision = 1, consumptionRevision = 1, itemNameSnapshot = "Item",
                    quantity = BigDecimal.ONE, regularUnitPriceSnapshot = Money("10"),
                    cashDiscountModeSnapshot = CashDiscountMode.NONE,
                    cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO,
                    pricingMode = PricingMode.TRANSFER, cashDiscountApplied = false,
                    cashDiscountPercent = BigDecimal.ZERO, cashDiscountAmount = Money.ZERO,
                    finalUnitPrice = Money("10"), lineTotal = Money("10")
                )
            )
        )
    }
}
