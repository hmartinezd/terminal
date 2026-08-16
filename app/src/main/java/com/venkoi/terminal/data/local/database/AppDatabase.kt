package com.venkoi.terminal.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TerminalEntity::class], version = 1, exportSchema = true)
@TypeConverters(TerminalTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun terminalDao(): TerminalDao
}
