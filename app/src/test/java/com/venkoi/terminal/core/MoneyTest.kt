package com.venkoi.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class MoneyTest {

    @Test
    fun testExactDecimals() {
        val m1 = Money("10.999")
        assertEquals("10.999", m1.toString())
    }

    @Test
    fun testPlus() {
        val m1 = Money("10.50")
        val m2 = Money("5.25")
        assertEquals(Money("15.75"), m1 + m2)
    }

    @Test
    fun testMinus() {
        val m1 = Money("10.50")
        val m2 = Money("5.25")
        assertEquals(Money("5.25"), m1 - m2)
    }

    @Test
    fun testTimes() {
        val m = Money("10.00")
        assertEquals(Money("30.00"), m * 3)
        assertEquals(Money("25.000"), m * BigDecimal("2.5"))
    }

    @Test
    fun testExplicitRounding() {
        val m = Money("10.555")
        
        // Round to 2 decimal places HALF_UP
        assertEquals(Money("10.56"), m.round(2, RoundingMode.HALF_UP))
        
        // Round to 0 decimal places HALF_UP
        assertEquals(Money("11"), m.round(0, RoundingMode.HALF_UP))
        
        // Round to 3 decimal places (no change)
        assertEquals(Money("10.555"), m.round(3, RoundingMode.HALF_UP))
    }

    @Test(expected = NumberFormatException::class)
    fun testInvalidInput() {
        Money.fromString("not-a-number")
    }

    @Test
    fun testSerializationRoundTrip() {
        val m = Money("123.456")
        val json = kotlinx.serialization.json.Json.encodeToString(Money.serializer(), m)
        
        // Should serialize as a JSON string to preserve precision
        assertEquals("\"123.456\"", json)
        
        val deserialized = kotlinx.serialization.json.Json.decodeFromString(Money.serializer(), json)
        assertEquals(m, deserialized)
        assertEquals("123.456", deserialized.amount.toPlainString())
    }
}
