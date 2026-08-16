package com.venkoi.terminal.core.di

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.RealClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindClock(realClock: RealClock): Clock
}
