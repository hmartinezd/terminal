package com.venkoi.terminal.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds nullable provenance columns so existing sale rows remain readable and untouched. */
val MIGRATION_5_7 = object : Migration(5, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sales ADD COLUMN restaurantIdSnapshot TEXT")
        database.execSQL("ALTER TABLE sales ADD COLUMN menuIdSnapshot TEXT")
        database.execSQL("ALTER TABLE sales ADD COLUMN publicationRevisionSnapshot INTEGER")
    }
}


// Both released-line candidates used version 6, but stored sales provenance differently.
private const val MAIN_V6_IDENTITY = "669533755c39abf4c0ec22deb2d414e3"
private const val REDESIGN_V6_IDENTITY = "2b79f1dd8d2b2d48508819a845e8d08a"

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val identity = database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
            check(it.moveToFirst()) { "Terminal schema 6 has no Room identity" }
            it.getString(0)
        }
        when (identity) {
            // Version 7 keeps the Redesign schema. Room still validates the complete schema.
            REDESIGN_V6_IDENTITY -> Unit
            MAIN_V6_IDENTITY -> migrateMainSaleProvenance(database)
            else -> error("Unrecognized Terminal schema 6 identity; database was not reset")
        }
    }
}

private fun migrateMainSaleProvenance(database: SupportSQLiteDatabase) {
    // Rebuild only the sales table. The version-6 schemas have no foreign keys to sales,
    // so sale_lines and sale_export_state retain their original contents and identities.
    // Do not infer a restaurant from today's device configuration for historical sales.
    database.execSQL(
        """
        CREATE TABLE sales_v7 (
            saleId TEXT NOT NULL PRIMARY KEY,
            terminalId TEXT NOT NULL,
            openedAtUtc TEXT NOT NULL,
            updatedAtUtc TEXT NOT NULL,
            tableLabel TEXT,
            status TEXT NOT NULL,
            revision INTEGER,
            completedAtUtc TEXT,
            voidedAtUtc TEXT,
            businessDate TEXT,
            currencyCodeSnapshot TEXT NOT NULL,
            currencyScaleSnapshot INTEGER NOT NULL,
            restaurantIdSnapshot TEXT,
            menuIdSnapshot TEXT,
            publicationRevisionSnapshot INTEGER
        )
        """.trimIndent()
    )
    database.execSQL(
        """
        INSERT INTO sales_v7 (
            saleId, terminalId, openedAtUtc, updatedAtUtc, tableLabel, status, revision,
            completedAtUtc, voidedAtUtc, businessDate, currencyCodeSnapshot, currencyScaleSnapshot,
            restaurantIdSnapshot, menuIdSnapshot, publicationRevisionSnapshot
        )
        SELECT saleId, terminalId, openedAtUtc, updatedAtUtc, tableLabel, status, revision,
            completedAtUtc, voidedAtUtc, businessDate, currencyCodeSnapshot, currencyScaleSnapshot,
            NULL, menuId, publicationRevision
        FROM sales
        """.trimIndent()
    )
    database.execSQL("DROP TABLE sales")
    database.execSQL("ALTER TABLE sales_v7 RENAME TO sales")
}
