package com.venkoi.terminal.integration

import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import com.venkoi.terminal.integration.menu.MenuPackageParser
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DemoMenuPackageValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }
    private val parser = MenuPackageParser(json)

    @Test
    fun `validate demo v1 and v2`() {
        val v1Raw = readDemoFile("demo_v1.json")
        val v2Raw = readDemoFile("demo_v2.json")

        val result1 = parser.parse(v1Raw)
        val result2 = parser.parse(v2Raw)

        assertTrue("Demo V1 should be valid", result1 is MenuPackageImportResult.Success)
        assertTrue("Demo V2 should be valid", result2 is MenuPackageImportResult.Success)

        val s1 = result1 as MenuPackageImportResult.Success
        val s2 = result2 as MenuPackageImportResult.Success

        assertEquals("Same restaurantId", s1.restaurant.restaurantId, s2.restaurant.restaurantId)
        assertEquals("Same menuId", s1.menu.menuId, s2.menu.menuId)
        assertTrue("V2 revision > V1 revision", s2.menu.publicationRevision > s1.menu.publicationRevision)

        // Verify intentional changes
        assertNotEquals("Default discount changed", 
            0, s1.menu.defaultCashDiscountPercent.compareTo(s2.menu.defaultCashDiscountPercent))
        
        val burger1 = s1.items.find { it.id == "item-burger" }
        val burger2 = s2.items.find { it.id == "item-burger" }
        assertNotNull(burger1)
        assertNotNull(burger2)
        assertNotEquals("Burger price changed", 0, burger1?.regularPrice?.amount?.compareTo(burger2?.regularPrice?.amount))
        assertNotEquals("Burger commercial revision changed", burger1?.commercialRevision, burger2?.commercialRevision)

        val chicken1 = s1.items.find { it.id == "item-chicken" }
        val chicken2 = s2.items.find { it.id == "item-chicken" }
        assertNotEquals("Chicken consumption revision changed", chicken1?.consumptionRevision, chicken2?.consumptionRevision)

        val pasta1 = s1.items.find { it.id == "item-pasta" }
        val pasta2 = s2.items.find { it.id == "item-pasta" }
        assertTrue("Pasta was active in V1", pasta1?.active == true)
        assertFalse("Pasta is inactive in V2", pasta2?.active == true)
        
        assertTrue("V2 has a new item", s2.items.any { it.id == "item-new-shake" })
    }

    private fun readDemoFile(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream("demo/$name").bufferedReader().readText()
    }
}
