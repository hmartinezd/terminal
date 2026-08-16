package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.PricingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PricingTest {

    @Test
    fun `TRANSFER with any discount policy has no discount`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountPolicyPercent = BigDecimal("10"),
            currencyScale = 2
        )
        assertEquals(Money("100.00"), result.lineTotal)
        assertEquals(Money("0.00"), result.cashDiscountAmount)
        assertTrue(BigDecimal.ZERO.compareTo(result.cashDiscountPercent) == 0)
        assertEquals(false, result.cashDiscountApplied)
    }

    @Test
    fun `CASH with positive discount policy applies discount`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("10"),
            currencyScale = 2
        )
        assertEquals(Money("90.00"), result.lineTotal)
        assertEquals(Money("10.00"), result.cashDiscountAmount)
        assertTrue(BigDecimal("10").compareTo(result.cashDiscountPercent) == 0)
        assertEquals(true, result.cashDiscountApplied)
    }

    @Test
    fun `CASH with zero discount policy has no discount`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal.ZERO,
            currencyScale = 2
        )
        assertEquals(Money("100.00"), result.lineTotal)
        assertEquals(Money("0.00"), result.cashDiscountAmount)
        assertEquals(false, result.cashDiscountApplied)
    }

    @Test
    fun `rounding HALF_UP works on discount boundary`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("3.335"),
            currencyScale = 2
        )
        // 100 * 0.03335 = 3.335 -> rounds to 3.34
        assertEquals(Money("3.34"), result.cashDiscountAmount)
        assertEquals(Money("96.66"), result.lineTotal)
    }

    @Test
    fun `explicit scale 0 works`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("10.5"),
            currencyScale = 0
        )
        // 100 * 0.105 = 10.5 -> rounds to 11
        assertEquals(Money("11"), result.cashDiscountAmount)
        assertEquals(Money("89"), result.lineTotal)
    }

    @Test
    fun `explicit scale 3 works`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("1.234"),
            quantity = BigDecimal("2"),
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("10"),
            currencyScale = 3
        )
        // (1.234 * 2) = 2.468
        // 2.468 * 0.10 = 0.2468 -> rounds to 0.247
        assertEquals(Money("0.247"), result.cashDiscountAmount)
        assertEquals(Money("2.221"), result.lineTotal)
    }

    @Test
    fun `quantity greater than 1 works`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal("2"),
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("10"),
            currencyScale = 2
        )
        assertEquals(Money("20.00"), result.cashDiscountAmount)
        assertEquals(Money("180.00"), result.lineTotal)
    }

    @Test
    fun `final unit price is correctly calculated`() {
        val result = CalculateLinePricing.calculate(
            regularUnitPrice = Money("100"),
            quantity = BigDecimal("3"),
            pricingMode = PricingMode.CASH,
            cashDiscountPolicyPercent = BigDecimal("10"),
            currencyScale = 2
        )
        // 300 * 0.10 = 30
        // 300 - 30 = 270
        // 270 / 3 = 90
        assertEquals(Money("90.00"), result.finalUnitPrice)
    }
}
