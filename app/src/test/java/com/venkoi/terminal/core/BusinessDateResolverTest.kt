package com.venkoi.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BusinessDateResolverTest {

    private val resolver = BusinessDateResolver()
    private val zoneIdHavana = ZoneId.of("America/Havana")
    private val cutoffHavana = LocalTime.of(4, 0)

    @Test
    fun testResolveBeforeCutoff() {
        // 2026-08-15 03:59:59 local in Havana
        // America/Havana is UTC-4 in August (EDT)
        // 03:59:59 local is 07:59:59 UTC
        val instant = Instant.parse("2026-08-15T07:59:59Z")
        assertEquals(LocalDate.parse("2026-08-14"), resolver.resolve(instant, zoneIdHavana, cutoffHavana))
    }

    @Test
    fun testResolveExactlyAtCutoff() {
        // 2026-08-15 04:00:00 local in Havana
        // 04:00:00 local is 08:00:00 UTC
        val instant = Instant.parse("2026-08-15T08:00:00Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(instant, zoneIdHavana, cutoffHavana))
    }

    @Test
    fun testResolveAfterCutoff() {
        // 2026-08-15 04:00:01 local in Havana
        val instant = Instant.parse("2026-08-15T08:00:01Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(instant, zoneIdHavana, cutoffHavana))
    }

    @Test
    fun testResolveUTC() {
        val zoneIdUtc = ZoneId.of("UTC")
        val cutoffUtc = LocalTime.of(0, 0)
        
        // Exactly at midnight UTC
        val instant = Instant.parse("2026-08-15T00:00:00Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(instant, zoneIdUtc, cutoffUtc))
        
        // One second before midnight UTC
        val before = Instant.parse("2026-08-15T23:59:59Z")
        assertEquals(LocalDate.parse("2026-08-15"), resolver.resolve(before, zoneIdUtc, cutoffUtc))
        
        // One second after midnight UTC
        val after = Instant.parse("2026-08-16T00:00:01Z")
        assertEquals(LocalDate.parse("2026-08-16"), resolver.resolve(after, zoneIdUtc, cutoffUtc))
    }
}
