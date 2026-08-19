package com.venkoi.terminal.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class TerminalDateFormatterTest {
    private val date = LocalDate.of(2026, 1, 6)

    @Test
    fun `English date uses abbreviated textual month`() {
        assertEquals("Jan 6, 2026", TerminalDateFormatter.formatDate(date, Locale.US))
    }

    @Test
    fun `Spanish date uses locale order and abbreviated textual month`() {
        assertEquals("6 ene 2026", TerminalDateFormatter.formatDate(date, Locale.forLanguageTag("es")))
    }

    @Test
    fun `today decoration compares supplied business dates`() {
        val locale = Locale.US

        assertEquals(
            "Jan 6, 2026 (Today)",
            TerminalDateFormatter.formatDateWithToday(date, date, locale, "Today")
        )
        assertEquals(
            "Jan 6, 2026",
            TerminalDateFormatter.formatDateWithToday(date, date.minusDays(1), locale, "Today")
        )
    }

    @Test
    fun `date time keeps localized date and localized short time`() {
        assertEquals(
            "Aug 19, 2026, 3:42\u202fPM",
            TerminalDateFormatter.formatDateTime(
                Instant.parse("2026-08-19T19:42:00Z"),
                ZoneId.of("America/New_York"),
                Locale.US
            )
        )
    }

    @Test
    fun `protocol instant uses restaurant timezone across calendar boundary`() {
        val instant = "2026-08-19T00:00:00Z"
        val newYork = ZoneId.of("America/New_York")

        assertEquals(
            "Aug 18, 2026, 8:00\u202fPM",
            TerminalDateFormatter.formatProtocolDateTime(instant, newYork, Locale.US)
        )
        assertEquals(
            "18 ago 2026, 20:00",
            TerminalDateFormatter.formatProtocolDateTime(instant, newYork, Locale.forLanguageTag("es"))
        )
    }

    @Test
    fun `invalid protocol instant safely preserves raw value`() {
        assertEquals(
            "invalid",
            TerminalDateFormatter.formatProtocolDateTime("invalid", ZoneId.of("UTC"), Locale.US)
        )
    }
}
