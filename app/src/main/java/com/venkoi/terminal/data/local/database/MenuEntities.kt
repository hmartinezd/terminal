package com.venkoi.terminal.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.integration.menu.CashDiscountMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

@Entity(tableName = "restaurant_configuration")
data class RestaurantConfigEntity(
    @PrimaryKey val id: Int = 0, // Enforce single record
    val restaurantId: String,
    val restaurantName: String,
    val timezone: ZoneId,
    val currency: Currency,
    val businessDayCutoff: LocalTime
)

@Entity(tableName = "published_menu")
data class PublishedMenuEntity(
    @PrimaryKey val id: Int = 0, // Enforce single record
    val menuId: String,
    val publicationRevision: Int,
    val publishedAtUtc: Instant,
    val defaultCashDiscountPercent: BigDecimal,
    val importTimestamp: Instant
)

@Entity(tableName = "menu_categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayOrder: Int
)

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val active: Boolean,
    val displayOrder: Int,
    val regularPrice: Money,
    val cashDiscountMode: CashDiscountMode,
    val commercialRevision: Int,
    val consumptionRevision: Int
)
