package com.venkoi.terminal.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
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
}
