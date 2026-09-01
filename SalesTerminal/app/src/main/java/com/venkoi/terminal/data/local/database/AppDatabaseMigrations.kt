package com.venkoi.terminal.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds nullable provenance columns so existing sale rows remain readable and untouched. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sales ADD COLUMN restaurantIdSnapshot TEXT")
        database.execSQL("ALTER TABLE sales ADD COLUMN menuIdSnapshot TEXT")
        database.execSQL("ALTER TABLE sales ADD COLUMN publicationRevisionSnapshot INTEGER")
    }
}
