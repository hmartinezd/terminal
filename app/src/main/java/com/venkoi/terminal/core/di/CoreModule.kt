package com.venkoi.terminal.core.di

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.core.RealClock
import com.venkoi.terminal.core.UUIDGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindClock(realClock: RealClock): Clock

    @Binds
    @Singleton
    abstract fun bindIdGenerator(uuidGenerator: UUIDGenerator): IdGenerator

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                encodeDefaults = true
                coerceInputValues = false
            }
        }

        @Provides
        @Singleton
        fun provideMenuPackageParser(json: Json): com.venkoi.terminal.integration.menu.MenuPackageParser {
            return com.venkoi.terminal.integration.menu.MenuPackageParser(json)
        }
    }
}
