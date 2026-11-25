package com.venkoi.terminal.data.local.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesProvenanceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationFromMainV6RetainsRecordedProvenanceWithoutInventingRestaurant() = verifyUpgrade(mainLayout = true)

    @Test
    fun migrationFromRedesignV6RetainsAllProvenance() = verifyUpgrade(mainLayout = false)

    private fun verifyUpgrade(mainLayout: Boolean) = runBlocking {
        val name = "sales-provenance-v6-v7-${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            // Main had the same version-5 base and added these exact two columns in version 6.
            val startingVersion = if (mainLayout) 5 else 6
            helper.createDatabase(name, startingVersion).use { source ->
                val provenanceColumns: String
                val provenanceValues: String
                if (mainLayout) {
                    source.execSQL("ALTER TABLE sales ADD COLUMN menuId TEXT")
                    source.execSQL("ALTER TABLE sales ADD COLUMN publicationRevision INTEGER")
                    source.execSQL(
                        "UPDATE room_master_table SET identity_hash = '669533755c39abf4c0ec22deb2d414e3' WHERE id = 42"
                    )
                    source.version = 6
                    provenanceColumns = "menuId, publicationRevision"
                    provenanceValues = "'menu-from-sale', 7"
                } else {
                    provenanceColumns = "restaurantIdSnapshot, menuIdSnapshot, publicationRevisionSnapshot"
                    provenanceValues = "'restaurant-from-sale', 'menu-from-sale', 7"
                }
                insertCompletedSale(source, provenanceColumns, provenanceValues)
            }

            helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { migrated ->
                assertEquals(7, migrated.version)
                migrated.query("SELECT restaurantIdSnapshot, menuIdSnapshot, publicationRevisionSnapshot FROM sales").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(if (mainLayout) null else "restaurant-from-sale", it.getString(0))
                    assertEquals("menu-from-sale", it.getString(1))
                    assertEquals(7, it.getInt(2))
                    assertEquals(1, it.count)
                }
                migrated.query("SELECT saleId, lineTotal, pricingMode FROM sale_lines").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("sale-original", it.getString(0))
                    assertEquals("10.00", it.getString(1))
                    assertEquals("TRANSFER", it.getString(2))
                }
                migrated.query("SELECT saleId, lastExportedRevision, lastExportBatchId FROM sale_export_state").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("sale-original", it.getString(0))
                    assertEquals(1, it.getInt(1))
                    assertEquals("export-original", it.getString(2))
                }
                migrated.query("SELECT restaurantId FROM terminal_configuration").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("current-restaurant", it.getString(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun insertCompletedSale(source: SupportSQLiteDatabase, provenanceColumns: String, provenanceValues: String) {
        source.execSQL(
            """
            INSERT INTO sales (saleId, terminalId, openedAtUtc, updatedAtUtc, tableLabel, status,
                revision, completedAtUtc, voidedAtUtc, businessDate, currencyCodeSnapshot, currencyScaleSnapshot,
                $provenanceColumns)
            VALUES ('sale-original', 'terminal-original', '2026-08-16T18:00:00Z',
                '2026-08-16T18:10:00Z', 'Table 1', 'COMPLETED', 1, '2026-08-16T18:10:00Z',
                NULL, '2026-08-16', 'USD', 2, $provenanceValues)
            """.trimIndent()
        )
        source.execSQL(
            """
            INSERT INTO sale_lines (lineId, saleId, menuItemId, commercialRevision, consumptionRevision,
                itemNameSnapshot, quantity, regularUnitPriceSnapshot, cashDiscountModeSnapshot,
                cashDiscountPolicyPercentSnapshot, pricingMode, cashDiscountApplied, cashDiscountPercent,
                cashDiscountAmount, finalUnitPrice, lineTotal, displayOrder)
            VALUES ('line-original', 'sale-original', 'dish-1', 4, 8, 'Item', '1', '10.00', 'NONE',
                '0', 'TRANSFER', 0, '0', '0', '10.00', '10.00', 0)
            """.trimIndent()
        )
        source.execSQL(
            "INSERT INTO sale_export_state VALUES ('sale-original', 1, '2026-08-16T20:00:00Z', 'export-original')"
        )
        source.execSQL(
            "INSERT INTO terminal_configuration VALUES (0, 'terminal-original', 'current-restaurant', 'Front', '2026-08-17T00:00:00Z')"
        )
    }
}
