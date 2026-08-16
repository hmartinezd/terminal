package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.Money
import com.venkoi.terminal.integration.menu.CashDiscountMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

data class RestaurantConfiguration(
    val restaurantId: String,
    val restaurantName: String,
    val timezone: ZoneId,
    val currency: Currency,
    val businessDayCutoff: LocalTime
)

data class PublishedMenu(
    val menuId: String,
    val publicationRevision: Int,
    val publishedAtUtc: Instant,
    val defaultCashDiscountPercent: java.math.BigDecimal,
    val importTimestamp: Instant? = null
)

data class MenuCategory(
    val id: String,
    val name: String,
    val displayOrder: Int
)

data class MenuItem(
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
