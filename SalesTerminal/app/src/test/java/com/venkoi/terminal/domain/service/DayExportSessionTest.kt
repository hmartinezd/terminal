package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.ExportedSaleRevision
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

class DayExportSessionTest {
    @Test
    fun `already exported groups re-export in order then a new session restarts`() {
        val date = LocalDate.parse("2026-08-10")
        val keys = PartitionSalesExport()(listOf(
            testSale("p3", date, "menu-3", 3),
            testSale("p1", date, "menu-1", 1),
            testSale("p2", date, "menu-2", 2)
        )).canonicalGroups.map { it.key }
        val session = DayExportSession(date, keys)

        assertEquals("menu-1", session.currentGroupKey!!.menuId)
        session.markCurrentGroupSuccessfullyHandedOff()
        assertEquals("menu-2", session.currentGroupKey!!.menuId)
        session.markCurrentGroupSuccessfullyHandedOff()
        assertEquals("menu-3", session.currentGroupKey!!.menuId)
        session.markCurrentGroupSuccessfullyHandedOff()
        assertNull(session.currentGroupKey)

        val restarted = DayExportSession(date, keys)
        assertEquals("menu-1", restarted.currentGroupKey!!.menuId)
    }

    @Test
    fun `cancel or failure leaves the current group unchanged and date uses a new session`() {
        val dateA = LocalDate.parse("2026-08-10")
        val dateB = LocalDate.parse("2026-08-11")
        val keysA = PartitionSalesExport()(listOf(
            testSale("a1", dateA, "menu-1", 1), testSale("a2", dateA, "menu-2", 2)
        )).canonicalGroups.map { it.key }
        val keysB = PartitionSalesExport()(listOf(testSale("b1", dateB, "menu-1", 1))).canonicalGroups.map { it.key }
        val sessionA = DayExportSession(dateA, keysA)

        // Cancellation/write failure does not call markCurrentGroupSuccessfullyHandedOff().
        assertEquals(0, sessionA.nextGroupIndex)
        assertEquals("menu-1", sessionA.currentGroupKey!!.menuId)

        sessionA.markCurrentGroupSuccessfullyHandedOff()
        assertEquals("menu-2", sessionA.currentGroupKey!!.menuId)
        val sessionB = DayExportSession(dateB, keysB)
        assertEquals("menu-1", sessionB.currentGroupKey!!.menuId)
    }

    @Test
    fun `each preparation resolves the current sale state for the current key`() {
        val date = LocalDate.parse("2026-08-10")
        val initial = PartitionSalesExport()(listOf(
            testSale("p1", date, "menu-1", 1), testSale("p2", date, "menu-2", 2)
        )).canonicalGroups
        val session = DayExportSession(date, initial.map { it.key })
        session.markCurrentGroupSuccessfullyHandedOff()

        val fresh = PartitionSalesExport()(listOf(
            testSale("p1", date, "menu-1", 1),
            testSale("p2", date, "menu-2", 2, SaleStatus.VOIDED)
        ))
        session.reconcile(fresh.canonicalGroups.map { it.key })
        val current = fresh.canonicalGroups.single { it.key == session.currentGroupKey }
        val export = BuildSalesExport()(
            restaurantId = "restaurant-1", terminalId = "terminal-1", exportId = "export-2",
            generatedAt = Instant.EPOCH, sales = current.sales
        )

        assertEquals("menu-2", session.currentGroupKey!!.menuId)
        assertEquals(SaleStatus.VOIDED.name, export.transactions.single().status.name)
        assertEquals("2026-08-10T12:01:00Z", export.transactions.single().closedAt)
        assertEquals(2, current.sales.single().sale.revision)
        assertEquals(listOf(ExportedSaleRevision(SaleId("p2"), 2)), current.sales.map {
            ExportedSaleRevision(it.sale.saleId, requireNotNull(it.sale.revision))
        })
        assertEquals(1, export.transactions.single().lines.size)
    }

    @Test
    fun `newly discovered groups join the sequence without repeating handed-off groups`() {
        val date = LocalDate.parse("2026-08-10")
        val initialKeys = PartitionSalesExport()(listOf(
            testSale("p1", date, "menu-1", 1), testSale("p2", date, "menu-2", 2)
        )).canonicalGroups.map { it.key }
        val session = DayExportSession(date, initialKeys)
        session.markCurrentGroupSuccessfullyHandedOff()

        val currentKeys = PartitionSalesExport()(listOf(
            testSale("p1", date, "menu-1", 1),
            testSale("p2", date, "menu-2", 2),
            testSale("p3", date, "menu-3", 3)
        )).canonicalGroups.map { it.key }
        session.reconcile(currentKeys)
        assertEquals("menu-2", session.currentGroupKey!!.menuId)
        session.markCurrentGroupSuccessfullyHandedOff()
        session.reconcile(currentKeys)
        assertEquals("menu-3", session.currentGroupKey!!.menuId)
    }

    private fun testSale(
        id: String,
        date: LocalDate,
        menuId: String,
        publicationRevision: Int,
        status: SaleStatus = SaleStatus.COMPLETED
    ) = SaleWithLines(
        sale = Sale(
            saleId = SaleId(id), terminalId = TerminalId("terminal-1"),
            openedAtUtc = Instant.parse("${date}T11:59:00Z"),
            updatedAtUtc = Instant.parse("${date}T12:00:00Z"), tableLabel = null,
            status = status, revision = if (status == SaleStatus.VOIDED) 2 else 1,
            completedAtUtc = Instant.parse("${date}T12:00:00Z"),
            voidedAtUtc = if (status == SaleStatus.VOIDED) Instant.parse("${date}T12:01:00Z") else null,
            businessDate = date, currencyCodeSnapshot = "USD", currencyScaleSnapshot = 2,
            restaurantIdSnapshot = "restaurant-1", menuIdSnapshot = menuId,
            publicationRevisionSnapshot = publicationRevision
        ),
        lines = listOf(SaleLine(
            lineId = LineId("line-$id"), saleId = SaleId(id), menuItemId = "item",
            commercialRevision = 1, consumptionRevision = 1, itemNameSnapshot = "Item",
            quantity = BigDecimal.ONE, regularUnitPriceSnapshot = Money("10"),
            cashDiscountModeSnapshot = CashDiscountMode.NONE,
            cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO,
            pricingMode = PricingMode.TRANSFER, cashDiscountApplied = false,
            cashDiscountPercent = BigDecimal.ZERO, cashDiscountAmount = Money.ZERO,
            finalUnitPrice = Money("10"), lineTotal = Money("10")
        ))
    )
}
