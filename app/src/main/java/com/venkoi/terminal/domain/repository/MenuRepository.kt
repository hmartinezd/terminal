package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import kotlinx.coroutines.flow.Flow

interface MenuRepository {
    fun observeRestaurantConfiguration(): Flow<RestaurantConfiguration?>
    fun observePublishedMenu(): Flow<PublishedMenu?>
    fun observeCategories(): Flow<List<MenuCategory>>
    fun observeMenuItems(): Flow<List<MenuItem>>
    fun observeActiveMenuItems(): Flow<List<MenuItem>>

    suspend fun installMenu(
        restaurant: RestaurantConfiguration,
        menu: PublishedMenu,
        categories: List<MenuCategory>,
        items: List<MenuItem>
    )

    suspend fun getPublishedMenu(): PublishedMenu?
    suspend fun getRestaurantConfiguration(): RestaurantConfiguration?
}
