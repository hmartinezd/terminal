package com.venkoi.terminal.integration

import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import com.venkoi.terminal.integration.menu.MenuPackageParser
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Exact producer fixture, not an independently hand-written consumer example. */
class MenuPackageProducerContractTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = false }
    private val parser = MenuPackageParser(json)
    private fun golden() = requireNotNull(javaClass.getResource("/contracts/menu-package-v1-golden.json")).readText()
    private fun mutateSection(section: String, field: String, value: JsonElement): String {
        val root = json.parseToJsonElement(golden()).jsonObject
        return JsonObject(root + (section to JsonObject(root.getValue(section).jsonObject + (field to value)))).toString()
    }
    @Test fun `back office and Serve V1 golden is accepted with exact identity and revisions`() {
        val root = json.parseToJsonElement(golden()).jsonObject
        val result = parser.parse(golden())
        assertTrue(result is MenuPackageImportResult.Success)
        val success = result as MenuPackageImportResult.Success
        assertEquals(root.getValue("menu").jsonObject.getValue("menuId").jsonPrimitive.content, success.menu.menuId)
        assertEquals(root.getValue("menu").jsonObject.getValue("publicationRevision").jsonPrimitive.int, success.menu.publicationRevision)
        assertEquals(root.getValue("menuItems").jsonArray.size, success.items.size)
        assertEquals(root.getValue("categories").jsonArray.size, success.categories.size)
    }
    @Test fun `real back office builder output preserves menu item identity money and revisions`() {
        val raw = requireNotNull(javaClass.getResource("/contracts/back-office-generated-v1.json")).readText()
        val result = parser.parse(raw)
        assertTrue(result is MenuPackageImportResult.Success)
        val parsed = result as MenuPackageImportResult.Success
        assertEquals("f2300000-0000-4000-8000-000000000002", parsed.menu.menuId)
        assertEquals(5, parsed.menu.publicationRevision)
        val item = parsed.items.single()
        assertEquals("f2300000-0000-4000-8000-000000000005", item.id)
        assertEquals("f2300000-0000-4000-8000-000000000003", item.categoryId)
        assertEquals(0, java.math.BigDecimal("12.50").compareTo(item.regularPrice.amount))
        assertEquals(4, item.commercialRevision)
        assertEquals(8, item.consumptionRevision)
    }
    @Test fun `fresh producer outputs are accepted when cross repository gate supplies them`() {
        val input = System.getenv("MENU_CONTRACT_INPUT_DIR") ?: return
        val directory = java.io.File(input)
        assertTrue("Producer fixture directory does not exist: $input", directory.isDirectory)
        val files = listOf("serve-generated-v1.json", "back-office-generated-v1.json")
            .map { java.io.File(directory, it) }
        for (file in files) {
            assertTrue("Missing fresh producer output ${file.name}", file.isFile)
            assertTrue("Rejected producer output ${file.name}", parser.parse(file.readText()) is MenuPackageImportResult.Success)
        }
    }
    @Test fun `publication revision is positive signed Int32`() {
        assertTrue(parser.parse(mutateSection("menu", "publicationRevision", JsonPrimitive(Int.MAX_VALUE))) is MenuPackageImportResult.Success)
        for (invalid in listOf(0L, -1L, 2147483648L)) {
            assertTrue(parser.parse(mutateSection("menu", "publicationRevision", JsonPrimitive(invalid))) is MenuPackageImportResult.Failure)
        }
    }
    @Test fun `duplicate exported category and item IDs are rejected`() {
        val root = json.parseToJsonElement(golden()).jsonObject
        for (key in listOf("categories", "menuItems")) {
            val rows = root.getValue(key).jsonArray
            assertTrue(rows.isNotEmpty())
            val bad = JsonObject(root + (key to JsonArray(rows + rows.first()))).toString()
            assertTrue(parser.parse(bad) is MenuPackageImportResult.Failure.SemanticValidationError)
        }
    }
    @Test fun `item revisions also enforce positive signed Int32`() {
        val root = json.parseToJsonElement(golden()).jsonObject
        val rows = root.getValue("menuItems").jsonArray
        for (key in listOf("commercialRevision", "consumptionRevision")) {
            for (revision in listOf(0L, 2147483648L)) {
                val changed = JsonObject(rows.first().jsonObject + (key to JsonPrimitive(revision)))
                val bad = JsonObject(root + ("menuItems" to JsonArray(listOf(changed) + rows.drop(1)))).toString()
                assertTrue(parser.parse(bad) is MenuPackageImportResult.Failure)
            }
        }
    }
    @Test fun `unknown additive metadata allowed but future schema rejected`() {
        val root = json.parseToJsonElement(golden()).jsonObject
        assertTrue(parser.parse(JsonObject(root + ("producerBuild" to JsonPrimitive("test"))).toString()) is MenuPackageImportResult.Success)
        assertTrue(parser.parse(JsonObject(root + ("schemaVersion" to JsonPrimitive(2))).toString()) is MenuPackageImportResult.Failure.UnsupportedSchemaVersion)
    }
}
