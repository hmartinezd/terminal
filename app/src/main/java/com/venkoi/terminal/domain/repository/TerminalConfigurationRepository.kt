package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.domain.model.TerminalConfiguration
import kotlinx.coroutines.flow.Flow

interface TerminalConfigurationRepository {
    suspend fun getConfiguration(): TerminalConfiguration?
    fun observeConfiguration(): Flow<TerminalConfiguration?>
    suspend fun saveConfiguration(configuration: TerminalConfiguration)
    suspend fun clearConfiguration()
}
