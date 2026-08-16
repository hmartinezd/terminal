package com.venkoi.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BusinessDateResolverTest {

    private val zoneId = ZoneId.of("UTC")
    private val resolver = BusinessDateResolver(zoneId)

    @Test
    fun testResolveBeforeCutoff() {
        // 2026-08-15 03:59:59 UTC
        val instant = Instant.parse("2026-08-15T03:59:59Z")
        assertEquals(LocalDate.parse("2026-08-14"), resolver.resolve(instant))
    }

    @Test
    fun testResolveAfterCutoff() {
        // 2026-08-15 04:00:00 UTC
        val instant = Instant.parse("2026-08-15T04:00:00Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(instant))
    }

    @Test
    fun testResolveLateNight() {
        // 2026-08-15 23:59:59 UTC
        val instant = Instant.parse("2026-08-15T23:59:59Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(instant))
    }
}
