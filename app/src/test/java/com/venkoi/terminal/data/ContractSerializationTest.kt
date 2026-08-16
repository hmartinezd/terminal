package com.venkoi.terminal.data

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.data.dto.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ContractSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMenuPackageV1RoundTrip() {
        val menuPackage = MenuPackageV1(
            restaurant = RestaurantDto("r1", "Resto", "UTC", "USD", "04:00"),
            menu = MenuDto("m1", 1, "2026-08-15T00:00:00Z", "0.00"),
            categories = listOf(CategoryDto("c1", "Cat1", 1)),
            menuItems = listOf(
                MenuItemDto("i1", "c1", "Item1", true, 1, Money("10.00"), "NONE", 1, 1)
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
                    status = "COMPLETED",
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
                            pricingMode = "CASH",
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
        val jsonStr = """{"schemaVersion": 2, "restaurant": {}, "menu": {}, "categories": [], "menuItems": []}"""
        json.decodeFromString(MenuPackageV1.serializer(), jsonStr)
    }
}
