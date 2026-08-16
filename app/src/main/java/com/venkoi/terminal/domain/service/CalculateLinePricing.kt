package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal
import java.math.RoundingMode

data class LinePricingResult(
    val cashDiscountApplied: Boolean,
    val cashDiscountPercent: BigDecimal,
    val cashDiscountAmount: Money,
    val finalUnitPrice: Money,
    val lineTotal: Money
)

object CalculateLinePricing {
    fun calculate(
        regularUnitPrice: Money,
        quantity: BigDecimal,
        pricingMode: PricingMode,
        cashDiscountMode: CashDiscountMode,
        restaurantDefaultCashDiscountPercent: BigDecimal,
        currencyScale: Int = 2
    ): LinePricingResult {
        val regularSubtotal = regularUnitPrice.amount.multiply(quantity)
        
        val shouldApplyDiscount = pricingMode == PricingMode.CASH && 
                                cashDiscountMode == CashDiscountMode.APPLY_DEFAULT
        
        val discountPercent = if (shouldApplyDiscount) restaurantDefaultCashDiscountPercent else BigDecimal.ZERO
        
        val exactDiscount = regularSubtotal.multiply(discountPercent).divide(BigDecimal("100"))
        val persistedDiscount = exactDiscount.setScale(currencyScale, RoundingMode.HALF_UP)
        
        val lineTotalAmount = regularSubtotal.subtract(persistedDiscount).setScale(currencyScale, RoundingMode.HALF_UP)
        
        val finalUnitPriceAmount = if (quantity > BigDecimal.ZERO) {
            lineTotalAmount.divide(quantity, currencyScale, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(currencyScale)
        }

        return LinePricingResult(
            cashDiscountApplied = shouldApplyDiscount && discountPercent > BigDecimal.ZERO,
            cashDiscountPercent = discountPercent,
            cashDiscountAmount = Money(persistedDiscount),
            finalUnitPrice = Money(finalUnitPriceAmount),
            lineTotal = Money(lineTotalAmount)
        )
    }
}
