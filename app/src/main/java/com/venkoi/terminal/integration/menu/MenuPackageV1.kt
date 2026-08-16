package com.venkoi.terminal.integration.menu

import com.venkoi.terminal.core.Money
import kotlinx.serialization.Serializable

@Serializable
enum class CashDiscountMode {
    APPLY_DEFAULT,
    NONE
}

@Serializable
data class MenuPackageV1(
    val schemaVersion: Int = 1,
    val restaurant: RestaurantDto,
    val menu: MenuDto,
    val categories: List<CategoryDto>,
    val menuItems: List<MenuItemDto>
) {
    init {
        require(schemaVersion == 1) { "Unsupported MenuPackage schema version: $schemaVersion" }
    }
}

@Serializable
data class RestaurantDto(
    val restaurantId: String,
    val restaurantName: String,
    val timezone: String,
    val currency: String,
    val businessDayCutoff: String // e.g., "04:00"
)

@Serializable
data class MenuDto(
    val menuId: String,
    val publicationRevision: Int,
    val publishedAtUtc: String,
    val defaultCashDiscountPercent: String
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val displayOrder: Int
)

@Serializable
data class MenuItemDto(
    val id: String,
    val categoryId: String,
    val name: String,
    val active: Boolean,
    val displayOrder: Int,
    val regularPrice: Money,
    val cashDiscountMode: CashDiscountMode,
    val commercialRevision: Int,
    val consumptionRevision: Int
)
