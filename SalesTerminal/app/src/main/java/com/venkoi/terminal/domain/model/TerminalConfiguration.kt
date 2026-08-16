package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import java.time.Instant

data class TerminalConfiguration(
    val terminalId: TerminalId,
    val restaurantId: RestaurantId,
    val terminalName: String?,
    val createdAt: Instant
)
