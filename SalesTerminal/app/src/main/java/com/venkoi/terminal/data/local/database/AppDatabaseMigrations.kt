package com.venkoi.terminal.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds nullable provenance to legacy sales without rewriting or dropping any sale data. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN menuId TEXT")
        db.execSQL("ALTER TABLE sales ADD COLUMN publicationRevision INTEGER")
    }
}
