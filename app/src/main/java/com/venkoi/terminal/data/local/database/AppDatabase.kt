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
        OpenOrderEntity::class,
        OpenOrderLineEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(TerminalTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun terminalDao(): TerminalDao
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
}
