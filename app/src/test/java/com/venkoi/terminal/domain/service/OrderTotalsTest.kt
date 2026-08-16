package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class OrderTotalsTest {

    @Test
    fun `totals equal sum of persisted line totals`() {
        val saleId = SaleId("sale-123")
        val lines = listOf(
            SaleLine(
                lineId = LineId("line-1"),
                saleId = saleId,
                menuItemId = "item-1",
                commercialRevision = 1,
                consumptionRevision = 1,
                itemNameSnapshot = "Item 1",
                quantity = BigDecimal.ONE,
                regularUnitPriceSnapshot = Money("100"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
                pricingMode = PricingMode.CASH,
                cashDiscountApplied = true,
                cashDiscountPercent = BigDecimal("10"),
                cashDiscountAmount = Money("10"),
                finalUnitPrice = Money("90"),
                lineTotal = Money("90")
            ),
            SaleLine(
                lineId = LineId("line-2"),
                saleId = saleId,
                menuItemId = "item-2",
                commercialRevision = 1,
                consumptionRevision = 1,
                itemNameSnapshot = "Item 2",
                quantity = BigDecimal.ONE,
                regularUnitPriceSnapshot = Money("200"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
                pricingMode = PricingMode.TRANSFER,
                cashDiscountApplied = false,
                cashDiscountPercent = BigDecimal.ZERO,
                cashDiscountAmount = Money.ZERO,
                finalUnitPrice = Money("200"),
                lineTotal = Money("200")
            )
        )

        val totals = CalculateOrderTotals.calculate(lines)
        assertEquals(Money("300"), totals.regularSubtotal)
        assertEquals(Money("10"), totals.cashDiscounts)
        assertEquals(Money("90"), totals.cashTotal)
        assertEquals(Money("200"), totals.transferTotal)
        assertEquals(Money("290"), totals.grandTotal)
    }

    @Test
    fun `mixed order total with three lines is correct`() {
        val saleId = SaleId("sale-123")
        val lines = listOf(
            SaleLine(
                lineId = LineId("l1"), saleId = saleId, menuItemId = "i1",
                commercialRevision = 1, consumptionRevision = 1, itemNameSnapshot = "Burger",
                quantity = BigDecimal("2"), regularUnitPriceSnapshot = Money("1500"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
                pricingMode = PricingMode.CASH, cashDiscountApplied = true,
                cashDiscountPercent = BigDecimal("10"), cashDiscountAmount = Money("300"),
                finalUnitPrice = Money("1350"), lineTotal = Money("2700")
            ),
            SaleLine(
                lineId = LineId("l2"), saleId = saleId, menuItemId = "i2",
                commercialRevision = 1, consumptionRevision = 1, itemNameSnapshot = "Drink",
                quantity = BigDecimal("1"), regularUnitPriceSnapshot = Money("500"),
                cashDiscountModeSnapshot = CashDiscountMode.NONE,
                cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO,
                pricingMode = PricingMode.TRANSFER, cashDiscountApplied = false,
                cashDiscountPercent = BigDecimal.ZERO, cashDiscountAmount = Money.ZERO,
                finalUnitPrice = Money("500"), lineTotal = Money("500")
            ),
            SaleLine(
                lineId = LineId("l3"), saleId = saleId, menuItemId = "i3",
                commercialRevision = 1, consumptionRevision = 1, itemNameSnapshot = "Dessert",
                quantity = BigDecimal("1"), regularUnitPriceSnapshot = Money("1000"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
                pricingMode = PricingMode.CASH, cashDiscountApplied = true,
                cashDiscountPercent = BigDecimal("10"), cashDiscountAmount = Money("100"),
                finalUnitPrice = Money("900"), lineTotal = Money("900")
            )
        )

        val totals = CalculateOrderTotals.calculate(lines)
        assertEquals(Money("4500"), totals.regularSubtotal) // (1500*2) + 500 + 1000
        assertEquals(Money("400"), totals.cashDiscounts) // 300 + 100
        assertEquals(Money("3600"), totals.cashTotal) // 2700 + 900
        assertEquals(Money("500"), totals.transferTotal)
        assertEquals(Money("4100"), totals.grandTotal) // 3600 + 500
        
        val sumLineTotals = lines.map { it.lineTotal.amount }.reduce { a, b -> a.add(b) }
        assertEquals(0, sumLineTotals.compareTo(totals.grandTotal.amount))
    }
}
