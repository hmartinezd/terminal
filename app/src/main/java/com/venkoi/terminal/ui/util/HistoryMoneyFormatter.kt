package com.venkoi.terminal.ui.util

import com.venkoi.terminal.core.Money
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object HistoryMoneyFormatter {
    fun format(
        money: Money,
        currencyCode: String,
        scale: Int
    ): String {
        val amount = money.amount.setScale(scale, RoundingMode.HALF_UP)
        // We use a simple format: "1,500.00 CUP"
        // In a real app, we might use NumberFormat with the specific currency.
        // For now, let's keep it consistent with the requirement: "1,500 CUP"
        
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = scale
            maximumFractionDigits = scale
        }
        
        return "${nf.format(amount)} $currencyCode"
    }
}
