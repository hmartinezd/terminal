package com.venkoi.terminal.core

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun now(): Instant
}

@Singleton
class RealClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
}
