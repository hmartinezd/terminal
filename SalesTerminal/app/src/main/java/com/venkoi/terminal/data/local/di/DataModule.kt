package com.venkoi.terminal.data.local.di

import android.content.Context
import androidx.room.Room
import com.venkoi.terminal.BuildConfig
import com.venkoi.terminal.data.local.database.AppDatabase
import com.venkoi.terminal.data.local.database.MenuDao
import com.venkoi.terminal.data.local.database.ReportDao
import com.venkoi.terminal.data.local.database.TerminalDao
import com.venkoi.terminal.data.local.database.ExportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sales_terminal.db"
        )
        // Development installs predate the production baseline and may reset. A distributed
        // release must fail instead of silently destroying restaurant data when a migration is absent.
        if (BuildConfig.DEBUG) builder.fallbackToDestructiveMigration(dropAllTables = true)
        return builder.build()
    }

    @Provides
    fun provideTerminalDao(database: AppDatabase): TerminalDao {
        return database.terminalDao()
    }

    @Provides
    fun provideMenuDao(database: AppDatabase): MenuDao {
        return database.menuDao()
    }

    @Provides
    fun provideSaleDao(database: AppDatabase): com.venkoi.terminal.data.local.database.SaleDao {
        return database.saleDao()
    }

    @Provides
    fun provideReportDao(database: AppDatabase): ReportDao {
        return database.reportDao()
    }

    @Provides
    fun provideExportDao(database: AppDatabase): ExportDao = database.exportDao()
}
