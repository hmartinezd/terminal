package com.venkoi.terminal.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList()
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To7PreservesAllRowsAndLeavesLegacyProvenanceNull() = runBlocking {
        val databaseName = "migration-5-7-${System.nanoTime()}"
        try {
            helper.createDatabase(databaseName, 5).apply {
                execSQL("INSERT INTO terminal_configuration VALUES (0, ?, ?, ?, ?)", arrayOf("terminal-5", "restaurant-5", "Terminal", "2026-08-01T00:00:00Z"))
                execSQL("INSERT INTO restaurant_configuration VALUES (0, ?, ?, ?, ?, ?)", arrayOf("restaurant-5", "Restaurant", "UTC", "USD", "04:00"))
                execSQL("INSERT INTO published_menu VALUES (0, ?, ?, ?, ?, ?)", arrayOf("menu-5", 7, "2026-08-01T00:00:00Z", "10", "2026-08-01T00:01:00Z"))
                execSQL("INSERT INTO menu_categories VALUES (?, ?, ?)", arrayOf("category-5", "Category", 1))
                execSQL("INSERT INTO menu_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf("item-5", "category-5", "Item", 1, 1, "10.00", "NONE", 4, 8))
                insertSale("sale-open-5", "OPEN", null, null, null, null)
                insertSale("sale-completed-5", "COMPLETED", 1, "2026-08-02T18:00:00Z", null, "2026-08-02")
                insertLine("sale-open-5", "line-open-5")
                insertLine("sale-completed-5", "line-completed-5")
                execSQL("INSERT INTO sale_export_state VALUES (?, ?, ?, ?)", arrayOf("sale-completed-5", 1, "2026-08-03T00:00:00Z", "legacy-batch"))
                close()
            }

            val migrated = helper.runMigrationsAndValidate(databaseName, 7, true, MIGRATION_5_7)
            migrated.query("SELECT COUNT(*) FROM terminal_configuration").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM restaurant_configuration").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM published_menu").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM menu_categories").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM menu_items").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM sales").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM sale_lines").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            migrated.query("SELECT COUNT(*) FROM sale_export_state").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            migrated.query("SELECT saleId, status, revision, businessDate, currencyCodeSnapshot, restaurantIdSnapshot, menuIdSnapshot, publicationRevisionSnapshot FROM sales ORDER BY saleId").use {
                assertTrue(it.moveToFirst())
                assertEquals("sale-completed-5", it.getString(0))
                assertEquals("COMPLETED", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertEquals("2026-08-02", it.getString(3))
                assertEquals("USD", it.getString(4))
                assertTrue(it.isNull(5) && it.isNull(6) && it.isNull(7))
                assertTrue(it.moveToNext())
                assertEquals("sale-open-5", it.getString(0))
                assertTrue(it.isNull(5) && it.isNull(6) && it.isNull(7))
            }
            migrated.query("SELECT lineId, saleId, quantity, regularUnitPriceSnapshot FROM sale_lines ORDER BY lineId").use {
                assertTrue(it.moveToFirst())
                assertEquals("line-completed-5", it.getString(0))
                assertEquals("sale-completed-5", it.getString(1))
                assertEquals("1", it.getString(2))
                assertEquals("10.00", it.getString(3))
                assertTrue(it.moveToNext())
                assertEquals("line-open-5", it.getString(0))
            }
            migrated.query("SELECT lastExportedRevision, lastExportBatchId FROM sale_export_state WHERE saleId = 'sale-completed-5'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
                assertEquals("legacy-batch", it.getString(1))
            }
            migrated.query("PRAGMA table_info(sales)").use {
                val columns = buildSet {
                    while (it.moveToNext()) add(it.getString(1))
                }
                assertTrue(columns.containsAll(setOf("restaurantIdSnapshot", "menuIdSnapshot", "publicationRevisionSnapshot")))
            }
            migrated.close()

            val reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(MIGRATION_5_7, MIGRATION_6_7)
                .build()
            assertEquals("sale-completed-5", reopened.saleDao().getSaleSync(com.venkoi.terminal.core.SaleId("sale-completed-5"))?.saleId?.value)
            assertEquals(1, reopened.exportDao().getExportState(com.venkoi.terminal.core.SaleId("sale-completed-5"))?.lastExportedRevision)
            reopened.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSale(
        id: String,
        status: String,
        revision: Int?,
        completedAt: String?,
        voidedAt: String?,
        businessDate: String?
    ) {
        execSQL(
            "INSERT INTO sales VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(id, "terminal-5", "2026-08-02T17:59:00Z", completedAt ?: "2026-08-02T17:00:00Z", "Table", status, revision, completedAt, voidedAt, businessDate, "USD", 2)
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLine(saleId: String, lineId: String) {
        execSQL(
            """INSERT INTO sale_lines (lineId, saleId, menuItemId, commercialRevision, consumptionRevision,
                itemNameSnapshot, quantity, regularUnitPriceSnapshot, cashDiscountModeSnapshot,
                cashDiscountPolicyPercentSnapshot, pricingMode, cashDiscountApplied, cashDiscountPercent,
                cashDiscountAmount, finalUnitPrice, lineTotal, displayOrder)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            arrayOf(lineId, saleId, "item-5", 4, 8, "Item", "1", "10.00", "NONE", "0", "TRANSFER", 0, "0", "0", "10.00", "10.00", 0)
        )
    }
}
