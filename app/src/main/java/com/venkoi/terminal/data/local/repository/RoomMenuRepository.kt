package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.data.local.database.CategoryEntity
import com.venkoi.terminal.data.local.database.MenuDao
import com.venkoi.terminal.data.local.database.MenuItemEntity
import com.venkoi.terminal.data.local.database.PublishedMenuEntity
import com.venkoi.terminal.data.local.database.RestaurantConfigEntity
import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.repository.MenuRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomMenuRepository @Inject constructor(
    private val menuDao: MenuDao,
    private val clock: Clock
) : MenuRepository {

    override fun observeRestaurantConfiguration(): Flow<RestaurantConfiguration?> {
        return menuDao.observeRestaurantConfig().map { it?.toDomain() }
    }

    override fun observePublishedMenu(): Flow<PublishedMenu?> {
        return menuDao.observePublishedMenu().map { it?.toDomain() }
    }

    override fun observeCategories(): Flow<List<MenuCategory>> {
        return menuDao.observeCategories().map { list -> list.map { it.toDomain() } }
    }

    override fun observeMenuItems(): Flow<List<MenuItem>> {
        return menuDao.observeMenuItems().map { list -> list.map { it.toDomain() } }
    }

    override fun observeActiveMenuItems(): Flow<List<MenuItem>> {
        return menuDao.observeActiveMenuItems().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun installMenu(
        restaurant: RestaurantConfiguration,
        menu: PublishedMenu,
        categories: List<MenuCategory>,
        items: List<MenuItem>
    ) {
        menuDao.installMenu(
            restaurant = restaurant.toEntity(),
            menu = menu.toEntity(clock.now()),
            categories = categories.map { it.toEntity() },
            items = items.map { it.toEntity() }
        )
    }

    override suspend fun getPublishedMenu(): PublishedMenu? {
        return menuDao.getPublishedMenu()?.toDomain()
    }

    override suspend fun getRestaurantConfiguration(): RestaurantConfiguration? {
        return menuDao.getRestaurantConfig()?.toDomain()
    }

    private fun RestaurantConfigEntity.toDomain() = RestaurantConfiguration(
        restaurantId = restaurantId,
        restaurantName = restaurantName,
        timezone = timezone,
        currency = currency,
        businessDayCutoff = businessDayCutoff
    )

    private fun PublishedMenuEntity.toDomain() = PublishedMenu(
        menuId = menuId,
        publicationRevision = publicationRevision,
        publishedAtUtc = publishedAtUtc,
        defaultCashDiscountPercent = defaultCashDiscountPercent,
        importTimestamp = importTimestamp
    )

    private fun CategoryEntity.toDomain() = MenuCategory(
        id = id,
        name = name,
        displayOrder = displayOrder
    )

    private fun MenuItemEntity.toDomain() = MenuItem(
        id = id,
        categoryId = categoryId,
        name = name,
        active = active,
        displayOrder = displayOrder,
        regularPrice = regularPrice,
        cashDiscountMode = cashDiscountMode,
        commercialRevision = commercialRevision,
        consumptionRevision = consumptionRevision
    )

    private fun RestaurantConfiguration.toEntity() = RestaurantConfigEntity(
        restaurantId = restaurantId,
        restaurantName = restaurantName,
        timezone = timezone,
        currency = currency,
        businessDayCutoff = businessDayCutoff
    )

    private fun PublishedMenu.toEntity(importTimestamp: java.time.Instant) = PublishedMenuEntity(
        menuId = menuId,
        publicationRevision = publicationRevision,
        publishedAtUtc = publishedAtUtc,
        defaultCashDiscountPercent = defaultCashDiscountPercent,
        importTimestamp = importTimestamp
    )

    private fun MenuCategory.toEntity() = CategoryEntity(
        id = id,
        name = name,
        displayOrder = displayOrder
    )

    private fun MenuItem.toEntity() = MenuItemEntity(
        id = id,
        categoryId = categoryId,
        name = name,
        active = active,
        displayOrder = displayOrder,
        regularPrice = regularPrice,
        cashDiscountMode = cashDiscountMode,
        commercialRevision = commercialRevision,
        consumptionRevision = consumptionRevision
    )
}
