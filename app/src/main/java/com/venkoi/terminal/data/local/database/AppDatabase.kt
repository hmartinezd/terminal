package com.venkoi.terminal.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TerminalEntity::class,
        RestaurantConfigEntity::class,
        PublishedMenuEntity::class,
        CategoryEntity::class,
        MenuItemEntity::class,
        SaleEntity::class,
        SaleLineEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(TerminalTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun terminalDao(): TerminalDao
    abstract fun menuDao(): MenuDao
    abstract fun saleDao(): SaleDao
    abstract fun reportDao(): ReportDao
}
