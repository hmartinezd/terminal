package com.venkoi.terminal.data.di

import com.venkoi.terminal.data.local.repository.RoomMenuRepository
import com.venkoi.terminal.data.local.repository.RoomReportRepository
import com.venkoi.terminal.data.local.repository.RoomTerminalConfigurationRepository
import com.venkoi.terminal.data.local.repository.RoomSaleRepository
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.ReportRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.repository.SalesExportRepository
import com.venkoi.terminal.data.local.repository.RoomSalesExportRepository
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

    @Binds
    @Singleton
    abstract fun bindMenuRepository(
        impl: RoomMenuRepository
    ): MenuRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(
        impl: RoomSaleRepository
    ): SaleRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        impl: RoomReportRepository
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindSalesExportRepository(impl: RoomSalesExportRepository): SalesExportRepository
}
