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
        assertEquals(0, BigDecimal("10.00").compareTo(success.menu.defaultCashDiscountPercent))
        assertEquals(1, success.categories.size)
        assertEquals(1, success.items.size)
    }

    @Test
    fun `empty input fails`() {
        val result = parser.parse("")
        assertTrue(result is MenuPackageImportResult.Failure.UnreadableInput)
    }

    @Test
    fun `malformed json fails`() {
        val result = parser.parse("{ invalid json }")
        assertTrue(result is MenuPackageImportResult.Failure.MalformedJson)
    }

    @Test
    fun `non-object json root fails`() {
        val result = parser.parse("[]")
        assertTrue(result is MenuPackageImportResult.Failure.DeserializationFailure)
        
        val result2 = parser.parse("\"hello\"")
        assertTrue(result2 is MenuPackageImportResult.Failure.DeserializationFailure)
    }

    @Test
    fun `missing schemaVersion fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"schemaVersion\": 1,", "")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.MissingSchemaVersion)
    }

    @Test
    fun `invalid schemaVersion type fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.MissingSchemaVersion)
    }

    @Test
    fun `unsupported schema version fails`() {
        val raw = readFixture("menu_package_v1_unsupported_version.json")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.UnsupportedSchemaVersion)
        assertEquals(2, (result as MenuPackageImportResult.Failure.UnsupportedSchemaVersion).version)
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
    fun `invalid publishedAtUtc fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("2026-08-15T21:00:00Z", "not-a-date")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `discount out of range fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("10.00", "101.00")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        
        val raw2 = readFixture("menu_package_v1_valid.json").replace("10.00", "-1.00")
        val result2 = parser.parse(raw2)
        assertTrue(result2 is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `negative price fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("1500.00", "-10.00")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `duplicate menu item ID fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"menuItems\": [", "\"menuItems\": [ {\"id\": \"item-1\", \"categoryId\": \"cat-1\", \"name\": \"I1\", \"active\": true, \"displayOrder\": 1, \"regularPrice\": \"100\", \"cashDiscountMode\": \"APPLY_DEFAULT\", \"commercialRevision\": 1, \"consumptionRevision\": 1}, ")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("Duplicate MenuItem ID"))
    }

    @Test
    fun `menu item referencing missing category fails`() {
        // Only replace categoryId in menuItems, keeping categories intact
        val raw = readFixture("menu_package_v1_valid.json").replace("\"categoryId\": \"cat-1\"", "\"categoryId\": \"missing-cat\"")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("references missing category"))
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

    @Test
    fun `invalid business-day cutoff fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("04:00", "invalid-time")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("Invalid businessDayCutoff"))
    }

    @Test
    fun `invalid enum value fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("APPLY_DEFAULT", "INVALID_ENUM")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.DeserializationFailure)
    }

    @Test
    fun `publicationRevision zero or negative fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"publicationRevision\": 58", "\"publicationRevision\": 0")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("publicationRevision must be positive"))

        val raw2 = readFixture("menu_package_v1_valid.json").replace("\"publicationRevision\": 58", "\"publicationRevision\": -1")
        val result2 = parser.parse(raw2)
        assertTrue(result2 is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `commercialRevision zero or negative fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"commercialRevision\": 20", "\"commercialRevision\": 0")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("commercialRevision must be positive"))

        val raw2 = readFixture("menu_package_v1_valid.json").replace("\"commercialRevision\": 20", "\"commercialRevision\": -1")
        val result2 = parser.parse(raw2)
        assertTrue(result2 is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    @Test
    fun `consumptionRevision zero or negative fails`() {
        val raw = readFixture("menu_package_v1_valid.json").replace("\"consumptionRevision\": 5", "\"consumptionRevision\": 0")
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Failure.SemanticValidationError)
        assertTrue((result as MenuPackageImportResult.Failure.SemanticValidationError).message.contains("consumptionRevision must be positive"))

        val raw2 = readFixture("menu_package_v1_valid.json").replace("\"consumptionRevision\": 5", "\"consumptionRevision\": -1")
        val result2 = parser.parse(raw2)
        assertTrue(result2 is MenuPackageImportResult.Failure.SemanticValidationError)
    }

    private fun readFixture(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream("fixtures/$name").bufferedReader().readText()
    }
}
