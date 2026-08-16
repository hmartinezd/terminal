package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.data.local.database.TerminalDao
import com.venkoi.terminal.data.local.database.TerminalEntity
import com.venkoi.terminal.domain.model.TerminalConfiguration
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTerminalConfigurationRepository @Inject constructor(
    private val terminalDao: TerminalDao
) : TerminalConfigurationRepository {

    override suspend fun getConfiguration(): TerminalConfiguration? {
        return terminalDao.getTerminalConfiguration()?.toDomain()
    }

    override fun observeConfiguration(): Flow<TerminalConfiguration?> {
        return terminalDao.observeTerminalConfiguration().map { it?.toDomain() }
    }

    override suspend fun saveConfiguration(configuration: TerminalConfiguration) {
        terminalDao.saveTerminalConfiguration(configuration.toEntity())
    }

    override suspend fun clearConfiguration() {
        terminalDao.clear()
    }

    private fun TerminalEntity.toDomain(): TerminalConfiguration {
        return TerminalConfiguration(
            terminalId = terminalId,
            restaurantId = restaurantId,
            terminalName = terminalName,
            createdAt = createdAt
        )
    }

    private fun TerminalConfiguration.toEntity(): TerminalEntity {
        return TerminalEntity(
            terminalId = terminalId,
            restaurantId = restaurantId,
            terminalName = terminalName,
            createdAt = createdAt
        )
    }
}
