package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.ProductReportCurrencySection
import com.venkoi.terminal.domain.model.ProductReportRow
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class BuildProductReport @Inject constructor() {

    fun build(businessDate: LocalDate, salesWithLines: List<SaleWithLines>): ProductReport {
        val completedSales = salesWithLines.filter { it.sale.status == SaleStatus.COMPLETED }
        
        val sections = completedSales
            .groupBy { it.sale.currencyCodeSnapshot to it.sale.currencyScaleSnapshot }
            .map { (currency, sales) ->
                val currencyCode = currency.first
                val currencyScale = currency.second
                
                val rows = sales.flatMap { it.lines }
                    .groupBy { line ->
                        // Grouping by menuItemId + name to handle historical name changes correctly
                        // but also grouping by currency to keep rows in their section.
                        line.menuItemId to line.itemNameSnapshot
                    }
                    .map { (key, lines) ->
                        ProductReportRow(
                            menuItemId = key.first,
                            itemNameSnapshot = key.second,
                            quantity = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.quantity },
                            amount = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }
                        )
                    }
                    .sortedWith(compareByDescending<ProductReportRow> { it.quantity }.thenBy { it.itemNameSnapshot })

                ProductReportCurrencySection(
                    currencyCode = currencyCode,
                    currencyScale = currencyScale,
                    rows = rows
                )
            }
            .sortedWith(compareBy({ it.currencyCode }, { it.currencyScale }))

        return ProductReport(
            businessDate = businessDate,
            currencySections = sections
        )
    }
}
