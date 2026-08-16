package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.BusinessDateResolver
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

class ResolveCurrentReportBusinessDateTest {
    @Test fun `before cutoff resolves previous business date for initial and today actions`() {
        val clock = object : Clock {
            override fun now() = Instant.parse("2026-08-15T07:30:00Z") // 03:30 Havana
        }
        val service = ResolveCurrentReportBusinessDate(clock, BusinessDateResolver())
        val configuration = RestaurantConfiguration(
            "restaurant", "Cafe", ZoneId.of("America/Havana"), Currency.getInstance("USD"), LocalTime.of(4, 0)
        )
        assertEquals(LocalDate.parse("2026-08-14"), service.resolve(configuration))
        assertEquals(LocalDate.parse("2026-08-14"), service.resolve(configuration))
    }
}
