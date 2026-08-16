package com.venkoi.terminal.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Exact-decimal representation of money.
 * Based on BigDecimal to ensure precision.
 * Rounding is explicit and should be performed at commercial boundaries.
 */
@Serializable(with = MoneySerializer::class)
data class Money(val amount: BigDecimal) : Comparable<Money> {

    constructor(amountStr: String) : this(BigDecimal(amountStr))
    constructor(amountLong: Long) : this(BigDecimal(amountLong))

    operator fun plus(other: Money): Money = Money(this.amount.add(other.amount))
    operator fun minus(other: Money): Money = Money(this.amount.subtract(other.amount))
    operator fun times(multiplier: Int): Money = Money(this.amount.multiply(BigDecimal(multiplier)))
    operator fun times(multiplier: BigDecimal): Money = Money(this.amount.multiply(multiplier))
    
    /**
     * Divides and rounds to the specified scale and mode.
     * Division must always specify a rounding mode to avoid ArithmeticException.
     */
    fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode): Money =
        Money(this.amount.divide(divisor, scale, roundingMode))

    fun divide(divisor: Int, scale: Int, roundingMode: RoundingMode): Money =
        divide(BigDecimal(divisor), scale, roundingMode)

    operator fun unaryMinus(): Money = Money(this.amount.negate())

    /**
     * Explicitly rounds the money to the requested scale.
     */
    fun round(scale: Int, roundingMode: RoundingMode): Money =
        Money(this.amount.setScale(scale, roundingMode))

    override fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)
    override fun toString(): String = amount.toPlainString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO)

        fun fromString(value: String): Money {
            // Fails clearly on invalid input as per requirements.
            return Money(BigDecimal(value))
        }
    }
}

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Money {
        return Money.fromString(decoder.decodeString())
    }
}
