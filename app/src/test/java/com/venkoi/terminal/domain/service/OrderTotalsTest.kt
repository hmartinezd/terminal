package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.PricingMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class OrderTotalsTest {

    @Test
    fun `totals equal sum of persisted line totals`() {
        val saleId = SaleId()
        val lines = listOf(
            OpenOrderLine(
                lineId = LineId(),
                saleId = saleId,
                menuItemId = "item-1",
                commercialRevision = 1,
                consumptionRevision = 1,
                itemNameSnapshot = "Item 1",
                quantity = BigDecimal.ONE,
                regularUnitPriceSnapshot = Money("100"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                pricingMode = PricingMode.CASH,
                cashDiscountApplied = true,
                cashDiscountPercentSnapshot = BigDecimal("10"),
                cashDiscountAmount = Money("10"),
                finalUnitPrice = Money("90"),
                lineTotal = Money("90")
            ),
            OpenOrderLine(
                lineId = LineId(),
                saleId = saleId,
                menuItemId = "item-2",
                commercialRevision = 1,
                consumptionRevision = 1,
                itemNameSnapshot = "Item 2",
                quantity = BigDecimal.ONE,
                regularUnitPriceSnapshot = Money("200"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                pricingMode = PricingMode.TRANSFER,
                cashDiscountApplied = false,
                cashDiscountPercentSnapshot = BigDecimal.ZERO,
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
}
