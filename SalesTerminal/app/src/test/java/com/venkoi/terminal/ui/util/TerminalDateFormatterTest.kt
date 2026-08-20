package com.venkoi.terminal.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TerminalDateFormatterTest {
    private val date = LocalDate.of(2026, 1, 6)

    @Test
    fun `formats English date with textual month`() {
        assertEquals("Jan 6, 2026", TerminalDateFormatter.formatDate(date, Locale.ENGLISH))
    }

    @Test
    fun `formats Spanish date with textual month`() {
        assertEquals("6 ene 2026", TerminalDateFormatter.formatDate(date, Locale.forLanguageTag("es")))
    }

    @Test
    fun `today decoration depends on supplied business date`() {
        assertEquals(
            "Jan 6, 2026 (Today)",
            TerminalDateFormatter.formatDateWithToday(date, date, Locale.ENGLISH, "Today")
        )
        assertEquals(
            "Jan 6, 2026",
            TerminalDateFormatter.formatDateWithToday(date, date.plusDays(1), Locale.ENGLISH, "Today")
        )
    }

    @Test
    fun `date time uses restaurant timezone rather than UTC date`() {
        val formatted = TerminalDateFormatter.formatDateTime(
            Instant.parse("2026-08-19T00:00:00Z"),
            ZoneId.of("America/New_York"),
            Locale.US
        )
        assertTrue(formatted.startsWith("Aug 18, 2026, 8:00"))
        assertTrue(formatted.endsWith("PM"))
    }

    @Test
    fun `protocol date time uses restaurant timezone and locale`() {
        assertEquals(
            "18 ago 2026, 20:00",
            TerminalDateFormatter.formatProtocolDateTime(
                "2026-08-19T00:00:00Z",
                ZoneId.of("America/New_York"),
                Locale.forLanguageTag("es")
            )
        )
    }

    @Test
    fun `invalid protocol date time falls back to raw value`() {
        assertEquals(
            "not-an-instant",
            TerminalDateFormatter.formatProtocolDateTime(
                "not-an-instant",
                ZoneId.of("America/New_York"),
                Locale.US
            )
        )
    }
}
