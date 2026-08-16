package com.venkoi.terminal.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class BusinessDateResolver @Inject constructor(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val cutoffTime = LocalTime.of(4, 0)

    fun resolve(instant: Instant): LocalDate {
        val zonedDateTime = instant.atZone(zoneId)
        val localTime = zonedDateTime.toLocalTime()
        val localDate = zonedDateTime.toLocalDate()
        
        return if (localTime.isBefore(cutoffTime)) {
            localDate.minusDays(1)
        } else {
            localDate
        }
    }
}
