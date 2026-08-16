package com.venkoi.terminal.integration

import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import com.venkoi.terminal.integration.menu.MenuPackageParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MenuPackageValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }
    private val parser = MenuPackageParser(json)

    @Test
    fun `valid v1 package passes validation`() {
        val raw = readFixture("menu_package_v1_valid.json")
        val result = parser.parse(raw)
        
        assertTrue(result is MenuPackageImportResult.Success)
        val success = result as MenuPackageImportResult.Success
        assertEquals("rest-123", success.restaurant.restaurantId)
        assertEquals("America/Havana", success.restaurant.timezone.id)
        assertEquals("CUP", success.restaurant.currency.currencyCode)
        assertEquals(BigDecimal("10.00"), success.menu.defaultCashDiscountPercent)
        assertEquals(1, success.categories.size)
        assertEquals(1, success.items.size)
    }

    @Test
    fun `unsupported schema version fails`() {
        val raw = readFixture("menu_package_v1_unsupported_version.json")
        val result = parser.parse(raw)
        
        assertTrue(result is MenuPackageImportResult.Failure.UnsupportedSchemaVersion)
    }

    @Test
    fun `malformed json fails`() {
        val result = parser.parse("{ invalid json }")
        assertTrue(result is MenuPackageImportResult.Failure.MalformedJson)
    }

    @Test
    fun `invalid timezone fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("America/Havana", "Invalid/Timezone")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `invalid currency fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("CUP", "INVALID")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `negative price fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("1500.00", "-10.00")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `duplicate category ID fails`() {
        val raw = """
            {
              "schemaVersion": 1,
              "restaurant": {
                "restaurantId": "r1", "restaurantName": "N", "timezone": "UTC", "currency": "USD", "businessDayCutoff": "04:00"
              },
              "menu": {
                "menuId": "m1", "publicationRevision": 1, "publishedAtUtc": "2026-08-15T21:00:00Z", "defaultCashDiscountPercent": "0"
              },
              "categories": [
                { "id": "c1", "name": "C1", "displayOrder": 1 },
                { "id": "c1", "name": "C2", "displayOrder": 2 }
              ],
              "menuItems": []
            }
        """.trimIndent()
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("Duplicate category ID"))
    }

    private fun readFixture(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream("fixtures/$name").bufferedReader().readText()
    }
}
