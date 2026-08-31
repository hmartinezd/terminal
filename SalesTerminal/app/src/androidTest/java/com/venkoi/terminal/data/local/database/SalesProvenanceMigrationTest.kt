package com.venkoi.terminal.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
    fun migration5To6PreservesLegacySaleAndAddsNullableProvenance() {
        helper.createDatabase("sales-provenance-migration", 5).apply {
            execSQL(
                """
                INSERT INTO sales(
                    saleId, terminalId, openedAtUtc, updatedAtUtc, tableLabel, status,
                    revision, completedAtUtc, voidedAtUtc, businessDate,
                    currencyCodeSnapshot, currencyScaleSnapshot
                ) VALUES (
                    'sale-legacy', 'terminal-1', '2026-08-16T18:00:00Z',
                    '2026-08-16T18:10:00Z', 'Table 1', 'COMPLETED', 1,
                    '2026-08-16T18:10:00Z', NULL, '2026-08-16', 'USD', 2
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            "sales-provenance-migration", 6, true, MIGRATION_5_6
        )
        migrated.query("SELECT * FROM sales WHERE saleId = 'sale-legacy'").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("terminal-1", cursor.getString(cursor.getColumnIndexOrThrow("terminalId")))
            assertEquals("COMPLETED", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals("USD", cursor.getString(cursor.getColumnIndexOrThrow("currencyCodeSnapshot")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("revision")))
            assertEquals(null, cursor.getString(cursor.getColumnIndexOrThrow("menuId")))
            assertEquals(true, cursor.isNull(cursor.getColumnIndexOrThrow("publicationRevision")))
        }
        migrated.close()
    }
}
