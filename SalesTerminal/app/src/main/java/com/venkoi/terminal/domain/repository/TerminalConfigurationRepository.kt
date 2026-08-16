package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.model.TerminalConfiguration
import kotlinx.coroutines.flow.Flow

interface TerminalConfigurationRepository {
    suspend fun getConfiguration(): TerminalConfiguration?
    fun observeConfiguration(): Flow<TerminalConfiguration?>
    suspend fun saveConfiguration(configuration: TerminalConfiguration)
    suspend fun clearConfiguration()

    suspend fun provisionTerminal(
        configuration: TerminalConfiguration,
        restaurant: RestaurantConfiguration,
        menu: PublishedMenu,
        categories: List<MenuCategory>,
        items: List<MenuItem>
    )
}
