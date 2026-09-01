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
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class DayExportSessionTest {
    @Test
    fun `already exported groups re-export in order then a new session restarts`() {
        val groups = PartitionSalesExport()(listOf(
            testSale("p3", "menu-3", 3),
            testSale("p1", "menu-1", 1),
            testSale("p2", "menu-2", 2)
        )).canonicalGroups
        val session = DayExportSession(LocalDate.parse("2026-08-10"), groups)

        assertEquals("p1", session.currentGroup!!.sales.single().sale.saleId.value)
        session.advanceAfterSuccessfulHandoff()
        assertEquals("p2", session.currentGroup!!.sales.single().sale.saleId.value)
        session.advanceAfterSuccessfulHandoff()
        assertEquals("p3", session.currentGroup!!.sales.single().sale.saleId.value)
        session.advanceAfterSuccessfulHandoff()
        assertNull(session.currentGroup)

        val restarted = DayExportSession(session.businessDate, groups)
        assertEquals("p1", restarted.currentGroup!!.sales.single().sale.saleId.value)
    }

    @Test
    fun `cancel or failure leaves the current group unchanged and date uses a new session`() {
        val dateA = LocalDate.parse("2026-08-10")
        val dateB = LocalDate.parse("2026-08-11")
        val groupsA = PartitionSalesExport()(listOf(testSale("a1", "menu-1", 1), testSale("a2", "menu-2", 2))).canonicalGroups
        val groupsB = PartitionSalesExport()(listOf(testSale("b1", "menu-1", 1))).canonicalGroups
        val sessionA = DayExportSession(dateA, groupsA)

        assertEquals("a1", sessionA.currentGroup!!.sales.single().sale.saleId.value)
        // Cancellation/write failure does not call advanceAfterSuccessfulHandoff().
        assertEquals(0, sessionA.nextGroupIndex)
        assertEquals("a1", sessionA.currentGroup!!.sales.single().sale.saleId.value)

        sessionA.advanceAfterSuccessfulHandoff()
        assertEquals("a2", sessionA.currentGroup!!.sales.single().sale.saleId.value)
        val sessionB = DayExportSession(dateB, groupsB)
        assertEquals("b1", sessionB.currentGroup!!.sales.single().sale.saleId.value)
    }

    private fun testSale(id: String, menuId: String, publicationRevision: Int) =
        SaleWithLines(
            sale = Sale(
                saleId = SaleId(id), terminalId = TerminalId("terminal-1"),
                openedAtUtc = Instant.parse("2026-08-10T11:59:00Z"),
                updatedAtUtc = Instant.parse("2026-08-10T12:00:00Z"), tableLabel = null,
                status = SaleStatus.COMPLETED, revision = 1,
                completedAtUtc = Instant.parse("2026-08-10T12:00:00Z"),
                businessDate = LocalDate.parse("2026-08-10"),
                currencyCodeSnapshot = "USD", currencyScaleSnapshot = 2,
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
