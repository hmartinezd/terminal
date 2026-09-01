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

class BuildSalesExportTest {
    private val builder = BuildSalesExport()
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    @Test
    fun `actual Terminal export has the canonical golden structure and literals`() {
        val export = builder(
            restaurantId = "restaurant-1",
            terminalId = "terminal-1",
            exportId = "export-1",
            generatedAt = Instant.parse("2026-08-16T23:00:00Z"),
            sales = listOf(completedSale(), voidedSale())
        )
        val actual = json.parseToJsonElement(json.encodeToString(export)).jsonObject
        val golden = json.parseToJsonElement(goldenFixture()).jsonObject

        assertEquals(golden, actual)
        assertEquals(golden.keys, actual.keys)
        assertEquals(SALES_EXPORT_FORMAT, actual["format"]!!.jsonPrimitive.content)
        assertEquals(1, actual["formatVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals(golden["transactions"]!!.jsonArray.size, actual["transactions"]!!.jsonArray.size)
        assertEquals(golden["transactions"]!!.jsonArray[0].jsonObject.keys,
            actual["transactions"]!!.jsonArray[0].jsonObject.keys)
        assertEquals(golden["transactions"]!!.jsonArray[0].jsonObject["lines"]!!.jsonArray[0].jsonObject.keys,
            actual["transactions"]!!.jsonArray[0].jsonObject["lines"]!!.jsonArray[0].jsonObject.keys)
        assertEquals("COMPLETED", actual["transactions"]!!.jsonArray[0].jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("VOIDED", actual["transactions"]!!.jsonArray[1].jsonObject["status"]!!.jsonPrimitive.content)

        actual["transactions"]!!.jsonArray.forEach { transaction ->
            transaction.jsonObject["lines"]!!.jsonArray.forEach { line ->
                val jsonLine = line.jsonObject
                listOf("quantity", "unitPrice", "gross", "discount", "net").forEach { key ->
                    assertTrue("$key must be a JSON string", jsonLine[key]!!.jsonPrimitive.isString)
                }
                assertTrue(jsonLine["commercialRevision"]!!.jsonPrimitive.content.toInt() > 0)
                assertTrue(jsonLine["consumptionRevision"]!!.jsonPrimitive.content.toInt() > 0)
            }
        }
    }

    @Test
    fun `void keeps historical lines and uses the void timestamp as closedAt`() {
        val transaction = builder("restaurant-1", "terminal-1", "export-1",
            Instant.parse("2026-08-16T23:00:00Z"), listOf(voidedSale())).transactions.single()
        assertEquals("VOIDED", transaction.status.name)
        assertEquals("2026-08-16T20:12:00Z", transaction.closedAt)
        assertEquals(1, transaction.lines.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `existing sale without captured provenance is not re-exported`() {
        val sale = completedSale().copy(sale = completedSale().sale.copy(
            restaurantIdSnapshot = null,
            menuIdSnapshot = null,
            publicationRevisionSnapshot = null
        ))
        builder("restaurant-1", "terminal-1", "export-1", Instant.EPOCH, listOf(sale))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sales from different immutable publications are not mixed`() {
        val otherPublication = completedSale().copy(
            sale = completedSale().sale.copy(menuIdSnapshot = "menu-2")
        )
        builder("restaurant-1", "terminal-1", "export-1", Instant.EPOCH,
            listOf(completedSale(), otherPublication))
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
