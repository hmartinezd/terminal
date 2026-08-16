package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.Money
import java.math.BigDecimal
import java.time.LocalDate

data class DailyMoneyReport(
    val businessDate: LocalDate,
    val currencySections: List<DailyMoneyCurrencySection>
)

data class DailyMoneyCurrencySection(
    val currencyCode: String,
    val currencyScale: Int,
    val validSaleCount: Int,
    val voidedSaleCount: Int,
    val cashTotal: Money,
    val transferTotal: Money,
    val grandTotal: Money,
    val cashDiscounts: Money,
    val voidedAmount: Money
)

data class ProductReport(
    val businessDate: LocalDate,
    val currencySections: List<ProductReportCurrencySection>
)

data class ProductReportCurrencySection(
    val currencyCode: String,
    val currencyScale: Int,
    val rows: List<ProductReportRow>
)

data class ProductReportRow(
    val menuItemId: String,
    val itemNameSnapshot: String,
    val quantity: BigDecimal,
    val amount: Money
)
