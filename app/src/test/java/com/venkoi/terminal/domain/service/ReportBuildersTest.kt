package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ReportBuildersTest {
    private val date = LocalDate.parse("2026-08-10")

    @Test fun `money report uses persisted lines and isolates voids and currencies`() {
        val sales = listOf(
            sale("a", SaleStatus.COMPLETED, "USD", line("a", "burger", "Burger", "2", PricingMode.CASH, "18.00", "2.00")),
            sale("b", SaleStatus.COMPLETED, "USD", line("b", "juice", "Juice", "1", PricingMode.TRANSFER, "5.00")),
            sale("c", SaleStatus.VOIDED, "USD", line("c", "cake", "Cake", "1", PricingMode.TRANSFER, "15.00")),
            sale("d", SaleStatus.COMPLETED, "CUP", line("d", "coffee", "Coffee", "1", PricingMode.CASH, "50.00")),
            sale("e", SaleStatus.OPEN, "USD", line("e", "x", "Ignored", "1", PricingMode.CASH, "99.00"))
        )

        val report = BuildDailyMoneyReport().build(date, sales)
        assertEquals(2, report.currencySections.size)
        val usd = report.currencySections.single { it.currencyCode == "USD" }
        assertEquals(2, usd.validSaleCount)
        assertEquals(1, usd.voidedSaleCount)
        assertMoney("18.00", usd.cashTotal)
        assertMoney("5.00", usd.transferTotal)
        assertMoney("23.00", usd.grandTotal)
        assertMoney("2.00", usd.cashDiscounts)
        assertMoney("15.00", usd.voidedAmount)
    }

    @Test fun `product report groups revisions by id and snapshot name but excludes voids`() {
        val first = line("a", "burger", "Burger", "1", PricingMode.CASH, "10.00", revision = 1)
        val repriced = line("b", "burger", "Burger", "2", PricingMode.CASH, "24.00", revision = 2)
        val renamed = line("c", "burger", "Classic Burger", "1", PricingMode.CASH, "14.00", revision = 3)
        val voided = line("d", "burger", "Burger", "9", PricingMode.CASH, "90.00")
        val report = BuildProductReport().build(date, listOf(
            sale("a", SaleStatus.COMPLETED, "USD", first),
            sale("b", SaleStatus.COMPLETED, "USD", repriced),
            sale("c", SaleStatus.COMPLETED, "USD", renamed),
            sale("d", SaleStatus.VOIDED, "USD", voided)
        ))
        val rows = report.currencySections.single().rows
        assertEquals(listOf("Burger", "Classic Burger"), rows.map { it.itemNameSnapshot })
        assertEquals(BigDecimal("3"), rows[0].quantity)
        assertMoney("34.00", rows[0].amount)
    }

    private fun sale(id: String, status: SaleStatus, currency: String, vararg lines: SaleLine) = SaleWithLines(
        Sale(SaleId(id), TerminalId("terminal"), Instant.EPOCH, Instant.EPOCH, null, status, 1,
            Instant.EPOCH, if (status == SaleStatus.VOIDED) Instant.EPOCH.plusSeconds(99) else null,
            date, currency, 2), lines.toList()
    )

    private fun line(
        sale: String, item: String, name: String, quantity: String, mode: PricingMode,
        total: String, discount: String = "0", revision: Int = 1
    ) = SaleLine(
        LineId("$sale-$item-$revision"), SaleId(sale), item, revision, 1, name, BigDecimal(quantity),
        Money(total), CashDiscountMode.APPLY_DEFAULT, BigDecimal.TEN, mode,
        discount != "0", BigDecimal.TEN, Money(discount), Money(total), Money(total)
    )

    private fun assertMoney(expected: String, actual: Money) =
        assertEquals(0, BigDecimal(expected).compareTo(actual.amount))
}
