package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal

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
        cashDiscountPolicyPercent: BigDecimal,
        currencyScale: Int
    ): LinePricingResult {
        val regularSubtotal = regularUnitPrice.amount.multiply(quantity)
        
        val shouldApplyDiscount = pricingMode == PricingMode.CASH && 
                                cashDiscountPolicyPercent > BigDecimal.ZERO
        
        val discountPercent = if (shouldApplyDiscount) cashDiscountPolicyPercent else BigDecimal.ZERO
        
        val exactDiscount = regularSubtotal.multiply(discountPercent).divide(BigDecimal("100"))
        val persistedDiscount = exactDiscount.setScale(currencyScale, CurrencyRoundingPolicy.roundingMode)
        
        val lineTotalAmount = regularSubtotal.subtract(persistedDiscount).setScale(currencyScale, CurrencyRoundingPolicy.roundingMode)
        
        val finalUnitPriceAmount = if (quantity > BigDecimal.ZERO) {
            lineTotalAmount.divide(quantity, currencyScale, CurrencyRoundingPolicy.roundingMode)
        } else {
            BigDecimal.ZERO.setScale(currencyScale)
        }

        return LinePricingResult(
            cashDiscountApplied = shouldApplyDiscount,
            cashDiscountPercent = discountPercent,
            cashDiscountAmount = Money(persistedDiscount),
            finalUnitPrice = Money(finalUnitPriceAmount),
            lineTotal = Money(lineTotalAmount)
        )
    }
}
