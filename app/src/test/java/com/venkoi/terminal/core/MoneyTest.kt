package com.venkoi.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun testPlus() {
        val m1 = Money("10.00")
        val m2 = Money("5.50")
        assertEquals(Money("15.50"), m1 + m2)
    }

    @Test
    fun testMinus() {
        val m1 = Money("10.00")
        val m2 = Money("5.50")
        assertEquals(Money("4.50"), m1 - m2)
    }

    @Test
    fun testTimes() {
        val m = Money("10.00")
        assertEquals(Money("30.00"), m * 3)
        assertEquals(Money("25.00"), m * BigDecimal("2.5"))
    }

    @Test
    fun testRounding() {
        // HALF_UP rounding to 2 decimal places
        assertEquals(Money("10.56"), Money.from(BigDecimal("10.555")))
        assertEquals(Money("10.55"), Money.from(BigDecimal("10.554")))
    }
}
