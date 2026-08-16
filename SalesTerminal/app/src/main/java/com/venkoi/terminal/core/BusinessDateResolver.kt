package com.venkoi.terminal.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Resolves the logical business date for a given instant based on restaurant configuration.
 */
class BusinessDateResolver @Inject constructor() {

    /**
     * Resolves the business date.
     * 
     * @param instant The UTC instant of the event (e.g., sale completion).
     * @param zoneId The restaurant's local timezone.
     * @param cutoff The business day cutoff time (e.g., 04:00).
     */
    fun resolve(
        instant: Instant,
        zoneId: ZoneId,
        cutoff: LocalTime
    ): LocalDate {
        val zonedDateTime = instant.atZone(zoneId)
        val localTime = zonedDateTime.toLocalTime()
        val localDate = zonedDateTime.toLocalDate()
        
        return if (localTime.isBefore(cutoff)) {
            localDate.minusDays(1)
        } else {
            localDate
        }
    }
}
