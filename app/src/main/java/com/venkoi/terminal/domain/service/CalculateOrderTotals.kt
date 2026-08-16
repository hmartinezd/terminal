package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal

data class OrderTotals(
    val regularSubtotal: Money,
    val cashDiscounts: Money,
    val cashTotal: Money,
    val transferTotal: Money,
    val grandTotal: Money
)

object CalculateOrderTotals {
    fun calculate(lines: List<SaleLine>): OrderTotals {
        var regularSubtotal = BigDecimal.ZERO
        var cashDiscounts = BigDecimal.ZERO
        var cashTotal = BigDecimal.ZERO
        var transferTotal = BigDecimal.ZERO
        var grandTotal = BigDecimal.ZERO

        for (line in lines) {
            val lineRegularSubtotal = line.regularUnitPriceSnapshot.amount.multiply(line.quantity)
            regularSubtotal = regularSubtotal.add(lineRegularSubtotal)
            cashDiscounts = cashDiscounts.add(line.cashDiscountAmount.amount)
            
            if (line.pricingMode == PricingMode.CASH) {
                cashTotal = cashTotal.add(line.lineTotal.amount)
            } else {
                transferTotal = transferTotal.add(line.lineTotal.amount)
            }
            
            grandTotal = grandTotal.add(line.lineTotal.amount)
        }

        return OrderTotals(
            regularSubtotal = Money(regularSubtotal),
            cashDiscounts = Money(cashDiscounts),
            cashTotal = Money(cashTotal),
            transferTotal = Money(transferTotal),
            grandTotal = Money(grandTotal)
        )
    }
}
