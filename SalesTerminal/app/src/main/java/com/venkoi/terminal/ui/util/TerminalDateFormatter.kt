package com.venkoi.terminal.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TerminalDateFormatter {
    fun formatDate(date: LocalDate, locale: Locale): String =
        date.format(dateFormatter(locale))

    fun formatDateTime(instant: Instant, zoneId: ZoneId, locale: Locale): String {
        val zoned = instant.atZone(zoneId)
        val time = formatTime(instant, zoneId, locale)
        return "${formatDate(zoned.toLocalDate(), locale)}, $time"
    }

    fun formatTime(instant: Instant, zoneId: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(instant.atZone(zoneId))

    fun formatProtocolDateTime(value: String, zoneId: ZoneId, locale: Locale): String =
        runCatching { formatDateTime(Instant.parse(value), zoneId, locale) }
            .getOrElse { value }

    fun formatDateWithToday(
        date: LocalDate,
        currentBusinessDate: LocalDate,
        locale: Locale,
        todayLabel: String
    ): String = buildString {
        append(formatDate(date, locale))
        if (date == currentBusinessDate) append(" ($todayLabel)")
    }

    private fun dateFormatter(locale: Locale): DateTimeFormatter {
        val pattern = if (locale.language == Locale.ENGLISH.language) "MMM d, yyyy" else "d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale)
    }
}
