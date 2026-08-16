package com.venkoi.terminal.integration

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.integration.menu.*
import com.venkoi.terminal.integration.sales.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ContractSerializationTest {

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    @Test
    fun testMenuPackageV1RoundTrip() {
        val menuPackage = MenuPackageV1(
            restaurant = RestaurantDto("r1", "Resto", "America/Havana", "CUP", "04:00"),
            menu = MenuDto("m1", 1, "2026-08-15T00:00:00Z", "10.00"),
            categories = listOf(CategoryDto("c1", "Cat1", 1)),
            menuItems = listOf(
                MenuItemDto("i1", "c1", "Item1", true, 1, Money("10.00"), CashDiscountMode.NONE, 1, 1)
            )
        )

        val encoded = json.encodeToString(MenuPackageV1.serializer(), menuPackage)
        val decoded = json.decodeFromString(MenuPackageV1.serializer(), encoded)

        assertEquals(menuPackage, decoded)
    }

    @Test
    fun testSalesBatchV1RoundTrip() {
        val salesBatch = SalesBatchV1(
            restaurantId = "r1",
            terminalId = "t1",
            batchId = "b1",
            exportedAtUtc = "2026-08-15T12:00:00Z",
            sales = listOf(
                SaleDto(
                    saleId = "s1",
                    revision = 1,
                    status = SaleStatus.COMPLETED,
                    openedAtUtc = "2026-08-15T10:00:00Z",
                    completedAtUtc = "2026-08-15T10:30:00Z",
                    voidedAtUtc = null,
                    businessDate = "2026-08-15",
                    tableNumber = "5",
                    lines = listOf(
                        SaleLineDto(
                            lineId = "l1",
                            menuItemId = "i1",
                            commercialRevision = 1,
                            consumptionRevision = 1,
                            itemNameSnapshot = "Item1",
                            quantity = "1",
                            regularUnitPriceSnapshot = Money("10.00"),
                            pricingMode = PricingMode.CASH,
                            cashDiscountApplied = false,
                            cashDiscountPercentSnapshot = "0",
                            cashDiscountAmountSnapshot = Money.ZERO,
                            finalUnitPriceSnapshot = Money("10.00"),
                            lineTotal = Money("10.00")
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(SalesBatchV1.serializer(), salesBatch)
        val decoded = json.decodeFromString(SalesBatchV1.serializer(), encoded)

        assertEquals(salesBatch, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnsupportedMenuPackageVersion() {
        // Structurally valid but unsupported version
        val jsonStr = """
            {
                "schemaVersion": 99,
                "restaurant": {
                    "restaurantId": "r1",
                    "restaurantName": "n",
                    "timezone": "UTC",
                    "currency": "CUP",
                    "businessDayCutoff": "04:00"
                },
                "menu": {
                    "menuId": "m1",
                    "publicationRevision": 1,
                    "publishedAtUtc": "t",
                    "defaultCashDiscountPercent": "0"
                },
                "categories": [],
                "menuItems": []
            }
        """.trimIndent()
        json.decodeFromString(MenuPackageV1.serializer(), jsonStr)
    }

    @Test
    fun testLoadMenuPackageFixture() {
        val fixture = getFixtureFile("menu_package_v1_valid.json")
        val decoded = json.decodeFromString(MenuPackageV1.serializer(), fixture)
        
        assertEquals(1, decoded.schemaVersion)
        assertEquals("rest-123", decoded.restaurant.restaurantId)
        assertEquals("America/Havana", decoded.restaurant.timezone)
        assertEquals("CUP", decoded.restaurant.currency)
        assertEquals("04:00", decoded.restaurant.businessDayCutoff)
        assertEquals(58, decoded.menu.publicationRevision)
        assertEquals(Money("1500.00"), decoded.menuItems[0].regularPrice)
        assertEquals(CashDiscountMode.APPLY_DEFAULT, decoded.menuItems[0].cashDiscountMode)
        assertEquals(20, decoded.menuItems[0].commercialRevision)
        assertEquals(5, decoded.menuItems[0].consumptionRevision)
    }

    @Test
    fun testLoadSalesBatchFixture() {
        val fixture = getFixtureFile("sales_batch_v1_valid.json")
        val decoded = json.decodeFromString(SalesBatchV1.serializer(), fixture)
        
        assertEquals(1, decoded.schemaVersion)
        assertEquals("term-789", decoded.terminalId)
        assertEquals("sale-001", decoded.sales[0].saleId)
        assertEquals(1, decoded.sales[0].revision)
        assertEquals(SaleStatus.COMPLETED, decoded.sales[0].status)
        assertEquals("2026-08-15", decoded.sales[0].businessDate)
        assertEquals(PricingMode.CASH, decoded.sales[0].lines[0].pricingMode)
        assertEquals(20, decoded.sales[0].lines[0].commercialRevision)
        assertEquals(5, decoded.sales[0].lines[0].consumptionRevision)
        assertEquals(Money("1500.00"), decoded.sales[0].lines[0].regularUnitPriceSnapshot)
        assertEquals(Money("2700.00"), decoded.sales[0].lines[0].lineTotal)
    }

    private fun getFixtureFile(name: String): String {
        val path = "src/test/resources/fixtures/$name"
        return File(path).readText()
    }
}
