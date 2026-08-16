package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.DailyMoneyCurrencySection
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import java.time.LocalDate
import javax.inject.Inject

class BuildDailyMoneyReport @Inject constructor() {

    fun build(businessDate: LocalDate, salesWithLines: List<SaleWithLines>): DailyMoneyReport {
        val sections = salesWithLines
            .filter { it.sale.status == SaleStatus.COMPLETED || it.sale.status == SaleStatus.VOIDED }
            .groupBy { it.sale.currencyCodeSnapshot to it.sale.currencyScaleSnapshot }
            .map { (currency, sales) ->
                val currencyCode = currency.first
                val currencyScale = currency.second
                
                var validSaleCount = 0
                var voidedSaleCount = 0
                var cashTotal = Money.ZERO
                var transferTotal = Money.ZERO
                var cashDiscounts = Money.ZERO
                var voidedAmount = Money.ZERO

                sales.forEach { item ->
                    when (item.sale.status) {
                        SaleStatus.COMPLETED -> {
                            validSaleCount++
                            item.lines.forEach { line ->
                                when (line.pricingMode) {
                                    PricingMode.CASH -> cashTotal += line.lineTotal
                                    PricingMode.TRANSFER -> transferTotal += line.lineTotal
                                }
                                cashDiscounts += line.cashDiscountAmount
                            }
                        }
                        SaleStatus.VOIDED -> {
                            voidedSaleCount++
                            item.lines.forEach { line ->
                                voidedAmount += line.lineTotal
                            }
                        }
                        else -> {} // OPEN, DISCARDED excluded by query
                    }
                }

                DailyMoneyCurrencySection(
                    currencyCode = currencyCode,
                    currencyScale = currencyScale,
                    validSaleCount = validSaleCount,
                    voidedSaleCount = voidedSaleCount,
                    cashTotal = cashTotal,
                    transferTotal = transferTotal,
                    grandTotal = cashTotal + transferTotal,
                    cashDiscounts = cashDiscounts,
                    voidedAmount = voidedAmount
                )
            }
            .sortedWith(compareBy({ it.currencyCode }, { it.currencyScale }))

        return DailyMoneyReport(
            businessDate = businessDate,
            currencySections = sections
        )
    }
}
