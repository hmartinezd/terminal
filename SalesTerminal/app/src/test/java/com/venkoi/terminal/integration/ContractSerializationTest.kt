package com.venkoi.terminal.integration

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.integration.menu.*
import com.venkoi.terminal.integration.sales.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun testSalesExportV1RoundTrip() {
        val salesExport = SalesExportV1(
            format = SALES_EXPORT_FORMAT,
            formatVersion = 1,
            exportId = "e1",
            terminalId = "t1",
            restaurantId = "r1",
            generatedAt = "2026-08-15T12:00:00Z",
            businessDate = "2026-08-15",
            menuId = "m1",
            publicationRevision = 1,
            currency = "USD",
            transactions = listOf(
                SalesExportTransactionV1(
                    transactionId = "s1",
                    openedAt = "2026-08-15T10:00:00Z",
                    closedAt = "2026-08-15T10:30:00Z",
                    status = SalesExportStatus.COMPLETED,
                    lines = listOf(
                        SalesExportLineV1(
                            saleLineId = "l1",
                            sellableItemId = "i1",
                            displayNameSnapshot = "Item1",
                            quantity = "1",
                            unitPrice = "10.00",
                            gross = "10.00",
                            discount = "0",
                            net = "10.00",
                            commercialRevision = 1,
                            consumptionRevision = 1
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(SalesExportV1.serializer(), salesExport)
        val decoded = json.decodeFromString(SalesExportV1.serializer(), encoded)

        assertEquals(salesExport, decoded)
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
    fun testLoadCanonicalSalesExportGoldenFixture() {
        val fixture = File("src/test/resources/contracts/sales-export-v1.golden.json").readText()
        val decoded = json.decodeFromString(SalesExportV1.serializer(), fixture)

        assertEquals(SALES_EXPORT_FORMAT, decoded.format)
        assertEquals(1, decoded.formatVersion)
        assertEquals("restaurant-1", decoded.restaurantId)
        assertEquals("menu-1", decoded.menuId)
        assertEquals(3, decoded.publicationRevision)
        assertEquals(listOf(SalesExportStatus.COMPLETED, SalesExportStatus.VOIDED),
            decoded.transactions.map { it.status })
        assertEquals("2", decoded.transactions[0].lines[0].quantity)
        assertEquals(4, decoded.transactions[0].lines[0].commercialRevision)
        assertEquals(7, decoded.transactions[0].lines[0].consumptionRevision)
    }

    @Test
    fun testMainBranchV1GoldenFixtureHasSameFieldsButDifferentFormat() {
        val old = Json.parseToJsonElement(getFixtureFile("sales-export-v1-golden.json")).jsonObject
        assertEquals("cuentame-sales-export", old.getValue("format").jsonPrimitive.content)
        // This test compares historical fields without broadening the live Redesign/V2 format.
        val document = Json.decodeFromJsonElement<SalesExportV1>(
            JsonObject(old + ("format" to JsonPrimitive(SALES_EXPORT_FORMAT)))
        )
        assertEquals(SALES_EXPORT_FORMAT, document.format)
        assertEquals(1, document.formatVersion)
        assertEquals("export-1", document.exportId)
        assertEquals("menu-1", document.menuId)
        assertEquals(3, document.publicationRevision)
        assertEquals(listOf(SalesExportStatus.COMPLETED, SalesExportStatus.VOIDED),
            document.transactions.map { it.status })
        assertEquals("25.22", document.transactions.first().lines.first().net)
    }

    private fun getFixtureFile(name: String): String {
        val path = "src/test/resources/fixtures/$name"
        return File(path).readText()
    }
}
