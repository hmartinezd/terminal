package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.BusinessDateResolver
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import java.time.LocalDate
import javax.inject.Inject

class ResolveCurrentReportBusinessDate @Inject constructor(
    private val clock: Clock,
    private val resolver: BusinessDateResolver
) {
    fun resolve(configuration: RestaurantConfiguration): LocalDate = resolver.resolve(
        clock.now(), configuration.timezone, configuration.businessDayCutoff
    )
}
