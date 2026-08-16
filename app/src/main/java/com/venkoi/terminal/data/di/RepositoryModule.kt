package com.venkoi.terminal.data.di

import com.venkoi.terminal.data.local.repository.RoomTerminalConfigurationRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTerminalConfigurationRepository(
        impl: RoomTerminalConfigurationRepository
    ): TerminalConfigurationRepository
}
