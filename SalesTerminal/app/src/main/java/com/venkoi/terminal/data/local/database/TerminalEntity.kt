package com.venkoi.terminal.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import java.time.Instant

@Entity(tableName = "terminal_configuration")
data class TerminalEntity(
    /**
     * Constant primary key to enforce the invariant that only one terminal 
     * configuration can exist per installation.
     */
    @PrimaryKey val id: Int = 0,
    val terminalId: TerminalId,
    val restaurantId: RestaurantId,
    val terminalName: String?,
    val createdAt: Instant
)
