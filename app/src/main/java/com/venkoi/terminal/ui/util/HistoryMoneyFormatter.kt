package com.venkoi.terminal.ui.util

import com.venkoi.terminal.core.Money
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object HistoryMoneyFormatter {
    fun format(
        money: Money,
        currencyCode: String,
        scale: Int,
        locale: Locale = Locale.getDefault()
    ): String {
        val amount = money.amount.setScale(scale, RoundingMode.HALF_UP)
        
        // Use default locale for formatting separators (dots/commas)
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = scale
            maximumFractionDigits = scale
        }
        
        return "${nf.format(amount)} $currencyCode"
    }
}
