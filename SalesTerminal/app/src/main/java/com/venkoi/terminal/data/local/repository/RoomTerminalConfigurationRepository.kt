package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.data.local.database.CategoryEntity
import com.venkoi.terminal.data.local.database.MenuDao
import com.venkoi.terminal.data.local.database.MenuItemEntity
import com.venkoi.terminal.data.local.database.PublishedMenuEntity
import com.venkoi.terminal.data.local.database.RestaurantConfigEntity
import com.venkoi.terminal.data.local.database.TerminalDao
import com.venkoi.terminal.data.local.database.TerminalEntity
import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.model.TerminalConfiguration
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTerminalConfigurationRepository @Inject constructor(
    private val terminalDao: TerminalDao,
    private val menuDao: MenuDao,
    private val clock: Clock
) : TerminalConfigurationRepository {

    override suspend fun getConfiguration(): TerminalConfiguration? {
        return terminalDao.getTerminalConfiguration()?.toDomain()
    }

    override fun observeConfiguration(): Flow<TerminalConfiguration?> {
        return terminalDao.observeTerminalConfiguration().map { it?.toDomain() }
    }

    override suspend fun saveConfiguration(configuration: TerminalConfiguration) {
        terminalDao.saveTerminalConfiguration(configuration.toEntity())
    }

    override suspend fun clearConfiguration() {
        terminalDao.clear()
    }

    override suspend fun provisionTerminal(
        configuration: TerminalConfiguration,
        restaurant: RestaurantConfiguration,
        menu: PublishedMenu,
        categories: List<MenuCategory>,
        items: List<MenuItem>
    ) {
        menuDao.provisionTerminal(
            terminal = configuration.toEntity(),
            restaurant = restaurant.toEntity(),
            menu = menu.toEntity(clock.now()),
            categories = categories.map { it.toEntity() },
            items = items.map { it.toEntity() }
        )
    }

    private fun TerminalEntity.toDomain(): TerminalConfiguration {
        return TerminalConfiguration(
            terminalId = terminalId,
            restaurantId = restaurantId,
            terminalName = terminalName,
            createdAt = createdAt
        )
    }

    private fun TerminalConfiguration.toEntity(): TerminalEntity {
        return TerminalEntity(
            terminalId = terminalId,
            restaurantId = restaurantId,
            terminalName = terminalName,
            createdAt = createdAt
        )
    }

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
