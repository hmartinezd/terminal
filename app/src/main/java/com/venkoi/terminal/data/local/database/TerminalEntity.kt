package com.venkoi.terminal.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import java.time.Instant

@Entity(tableName = "terminal")
data class TerminalEntity(
    @PrimaryKey val terminalId: TerminalId,
    val restaurantId: RestaurantId,
    val timestamp: Instant
)
