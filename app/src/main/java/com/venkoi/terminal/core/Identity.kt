package com.venkoi.terminal.core

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class RestaurantId(val value: String) {
    constructor(uuid: UUID = UUID.randomUUID()) : this(uuid.toString())
}

@Serializable
@JvmInline
value class TerminalId(val value: String) {
    constructor(uuid: UUID = UUID.randomUUID()) : this(uuid.toString())
}

@Serializable
@JvmInline
value class SaleId(val value: String) {
    constructor(uuid: UUID = UUID.randomUUID()) : this(uuid.toString())
}

@Serializable
@JvmInline
value class LineId(val value: String) {
    constructor(uuid: UUID = UUID.randomUUID()) : this(uuid.toString())
}
