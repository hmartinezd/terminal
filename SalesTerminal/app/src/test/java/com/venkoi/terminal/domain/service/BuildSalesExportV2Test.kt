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
import com.venkoi.terminal.integration.sales.SALES_EXPORT_FORMAT
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BuildSalesExportV2Test {
    private val builder = BuildSalesExport()
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    private fun export(sales: List<SaleWithLines>) = builder.buildV2(
        "restaurant-1", "terminal-1", "export-1", Instant.parse("2026-08-16T23:00:00Z"), sales
    )

    private fun mixedSale() = completedSale().let { sale ->
        sale.copy(lines = sale.lines.mapIndexed { index, line ->
            line.copy(pricingMode = if (index == 0) PricingMode.CASH else PricingMode.TRANSFER)
        })
    }

    @Test fun `V2 actual producer matches golden and round trips without lost decimals`() {
        val value = export(listOf(voidedSale(), mixedSale()))
        val actual = json.parseToJsonElement(json.encodeToString(value))
        val expected = json.parseToJsonElement(requireNotNull(javaClass.getResource("/contracts/sales-export-v2.golden.json")).readText())
        assertEquals(expected, actual)
        assertEquals(value, json.decodeFromString<com.venkoi.terminal.integration.sales.SalesExportV2>(json.encodeToString(value)))
        assertEquals(2, value.formatVersion)
        assertEquals(listOf("CASH", "TRANSFER"), value.transactions.first().lines.map { it.pricingMode })
    }

    @Test fun `zero discount cash remains cash and is not inferred from discount`() {
        val sale = completedSale().let { original -> original.copy(lines = original.lines.takeLast(1).map { it.copy(pricingMode = PricingMode.CASH) }) }
        val line = export(listOf(sale)).transactions.single().lines.single()
        assertEquals("CASH", line.pricingMode)
        assertEquals("0", line.discount)
    }

    @Test fun `V2 totals by mode match daily Terminal report excluding voided lines`() {
        val sales = listOf(mixedSale(), voidedSale())
        val value = export(sales)
        val totals = value.transactions.filter { it.status.name == "COMPLETED" }
            .flatMap { it.lines }.groupBy { it.pricingMode }.mapValues { (_, lines) -> lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(BigDecimal(line.net)) } }
        val report = BuildDailyMoneyReport().build(LocalDate.parse("2026-08-16"), sales).currencySections.single()
        assertEquals(0, report.cashTotal.amount.compareTo(totals.getValue("CASH")))
        assertEquals(0, report.transferTotal.amount.compareTo(totals.getValue("TRANSFER")))
        assertEquals(1, report.voidedSaleCount)
        val void = value.transactions.last()
        assertEquals("VOIDED", void.status.name)
        assertEquals("2026-08-16T20:12:00Z", void.closedAt)
        assertEquals("TRANSFER", void.lines.single().pricingMode)
    }

    @Test fun `V2 preserves all V1 fields and default V1 remains strict legacy golden`() {
        val sales = listOf(completedSale(), voidedSale())
        val v1 = json.parseToJsonElement(json.encodeToString(builder("restaurant-1", "terminal-1", "export-1", Instant.parse("2026-08-16T23:00:00Z"), sales)))
        assertEquals(json.parseToJsonElement(goldenFixture()), v1)
        val v2 = json.parseToJsonElement(json.encodeToString(export(sales))).jsonObject
        val stripped = kotlinx.serialization.json.JsonObject(v2.toMutableMap().apply {
            put("formatVersion", kotlinx.serialization.json.JsonPrimitive(1))
            put("transactions", kotlinx.serialization.json.JsonArray(v2.getValue("transactions").jsonArray.map { tx ->
                kotlinx.serialization.json.JsonObject(tx.jsonObject.toMutableMap().apply {
                    put("lines", kotlinx.serialization.json.JsonArray(tx.jsonObject.getValue("lines").jsonArray.map { line ->
                        kotlinx.serialization.json.JsonObject(line.jsonObject.filterKeys { it != "pricingMode" })
                    }))
                })
            }))
        })
        assertEquals(v1, stripped)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses mixed currencies`() {
        export(listOf(completedSale(), voidedSale().let { it.copy(sale = it.sale.copy(currencyCodeSnapshot = "EUR")) }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses invalid net arithmetic`() {
        export(listOf(completedSale().let { it.copy(lines = it.lines.map { line -> line.copy(lineTotal = Money("0")) }) }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses missing immutable provenance`() {
        export(listOf(completedSale().let { it.copy(sale = it.sale.copy(menuIdSnapshot = null)) }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses duplicate identity rather than guessing pricing mode`() {
        export(listOf(completedSale(), completedSale()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses duplicate line identities`() {
        export(listOf(completedSale().let { it.copy(lines = listOf(it.lines.first(), it.lines.first())) }))
    }

    @Test fun `V2 mode wire value is open string for future classifications`() {
        val value = export(listOf(mixedSale()))
        val text = json.encodeToString(value).replace("\"CASH\"", "\"CARD\"")
        val decoded = json.decodeFromString<com.venkoi.terminal.integration.sales.SalesExportV2>(text)
        assertEquals("CARD", decoded.transactions.single().lines.first().pricingMode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses empty classification code`() {
        export(listOf(mixedSale())).transactions.single().lines.first().copy(pricingMode = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 rejects incorrect version`() {
        export(listOf(mixedSale())).copy(formatVersion = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `V2 refuses reused line identity across distinct transactions`() {
        val first = completedSale()
        val second = voidedSale().let { sale -> sale.copy(lines = sale.lines.map { it.copy(lineId = first.lines.first().lineId) }) }
        export(listOf(first, second))
    }

    private fun completedSale(): SaleWithLines {
        val id = SaleId("sale-1")
        return SaleWithLines(
            sale = sale(id, SaleStatus.COMPLETED, "2026-08-16T18:40:00Z",
                "2026-08-16T18:47:00Z", null),
            lines = listOf(
                line(id, "line-1", "burger", "Burger", "2", "13", "0.78", "25.22", 4, 7),
                line(id, "line-2", "wings", "Wings", "1", "12", "0", "12", 2, 5)
            )
        )
    }

    private fun voidedSale(): SaleWithLines {
        val id = SaleId("sale-2")
        return SaleWithLines(
            sale = sale(id, SaleStatus.VOIDED, "2026-08-16T20:10:00Z",
                "2026-08-16T20:11:00Z", "2026-08-16T20:12:00Z"),
            lines = listOf(line(id, "line-3", "burger", "Burger", "1", "13", "0", "13", 4, 7))
        )
    }

    private fun sale(id: SaleId, status: SaleStatus, opened: String, completed: String,
                     voided: String?): Sale = Sale(
        saleId = id,
        terminalId = TerminalId("terminal-1"),
        openedAtUtc = Instant.parse(opened),
        updatedAtUtc = Instant.parse(voided ?: completed),
        tableLabel = null,
        status = status,
        revision = if (status == SaleStatus.VOIDED) 2 else 1,
        completedAtUtc = Instant.parse(completed),
        voidedAtUtc = voided?.let(Instant::parse),
        businessDate = LocalDate.parse("2026-08-16"),
        currencyCodeSnapshot = "USD",
        currencyScaleSnapshot = 2,
        restaurantIdSnapshot = "restaurant-1",
        menuIdSnapshot = "menu-1",
        publicationRevisionSnapshot = 3
    )

    private fun line(saleId: SaleId, id: String, itemId: String, name: String, quantity: String,
                     unitPrice: String, discount: String, net: String, commercial: Int,
                     consumption: Int): SaleLine {
        val amount = BigDecimal(quantity).multiply(BigDecimal(unitPrice))
        return SaleLine(
            lineId = LineId(id), saleId = saleId, menuItemId = itemId,
            commercialRevision = commercial, consumptionRevision = consumption,
            itemNameSnapshot = name, quantity = BigDecimal(quantity),
            regularUnitPriceSnapshot = Money(unitPrice), cashDiscountModeSnapshot = CashDiscountMode.NONE,
            cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO, pricingMode = PricingMode.TRANSFER,
            cashDiscountApplied = false, cashDiscountPercent = BigDecimal.ZERO,
            cashDiscountAmount = Money(discount), finalUnitPrice = Money(net), lineTotal = Money(net)
        ).also { check(amount.subtract(BigDecimal(discount)).compareTo(BigDecimal(net)) == 0) }
    }

    private fun goldenFixture(): String = requireNotNull(javaClass.getResource("/contracts/sales-export-v1.golden.json"))
        .readText().trim()
}
