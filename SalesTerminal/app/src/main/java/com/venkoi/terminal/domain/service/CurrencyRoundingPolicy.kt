package com.venkoi.terminal.domain.service

import java.math.RoundingMode
import java.util.Currency

object CurrencyRoundingPolicy {
    val roundingMode = RoundingMode.HALF_UP

    fun scaleFor(currency: Currency): Int {
        val scale = currency.defaultFractionDigits
        if (scale < 0) {
            throw IllegalArgumentException("Currency ${currency.currencyCode} has no usable precision")
        }
        return scale
    }
}
