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
 * Standardizes on 2 decimal places with HALF_UP rounding.
 */
@Serializable(with = MoneySerializer::class)
data class Money(val amount: BigDecimal) : Comparable<Money> {

    constructor(amountStr: String) : this(BigDecimal(amountStr).setScale(DECIMAL_PLACES, ROUNDING_MODE))
    constructor(amountLong: Long) : this(BigDecimal(amountLong).setScale(DECIMAL_PLACES, ROUNDING_MODE))

    operator fun plus(other: Money): Money = from(this.amount.add(other.amount))
    operator fun minus(other: Money): Money = from(this.amount.subtract(other.amount))
    operator fun times(multiplier: Int): Money = from(this.amount.multiply(BigDecimal(multiplier)))
    operator fun times(multiplier: BigDecimal): Money = from(this.amount.multiply(multiplier))
    operator fun div(divisor: Int): Money = from(this.amount.divide(BigDecimal(divisor), ROUNDING_MODE))
    operator fun div(divisor: BigDecimal): Money = from(this.amount.divide(divisor, ROUNDING_MODE))
    
    operator fun unaryMinus(): Money = from(this.amount.negate())
    
    // For discount calculations
    fun multiply(percent: BigDecimal): Money = 
        from(this.amount.multiply(percent).divide(ONE_HUNDRED, ROUNDING_MODE))

    override fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)
    override fun toString(): String = amount.toPlainString()

    companion object {
        const val DECIMAL_PLACES = 2
        val ROUNDING_MODE = RoundingMode.HALF_UP
        private val ONE_HUNDRED = BigDecimal("100")

        val ZERO = from(BigDecimal.ZERO)

        fun from(amount: BigDecimal): Money {
            return Money(amount.setScale(DECIMAL_PLACES, ROUNDING_MODE))
        }

        fun fromString(value: String): Money {
            return try {
                from(BigDecimal(value))
            } catch (e: Exception) {
                ZERO
            }
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
