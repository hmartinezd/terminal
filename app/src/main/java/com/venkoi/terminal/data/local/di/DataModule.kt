package com.venkoi.terminal.data.local.di

import android.content.Context
import androidx.room.Room
import com.venkoi.terminal.data.local.database.AppDatabase
import com.venkoi.terminal.data.local.database.MenuDao
import com.venkoi.terminal.data.local.database.TerminalDao
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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sales_terminal.db"
        ).build()
    }

    @Provides
    fun provideTerminalDao(database: AppDatabase): TerminalDao {
        return database.terminalDao()
    }

    @Provides
    fun provideMenuDao(database: AppDatabase): MenuDao {
        return database.menuDao()
    }
}
