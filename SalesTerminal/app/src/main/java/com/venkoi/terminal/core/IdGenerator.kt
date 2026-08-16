package com.venkoi.terminal.core

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface IdGenerator {
    fun nextId(): String
}

@Singleton
class UUIDGenerator @Inject constructor() : IdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
