package com.venkoi.terminal.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRestaurantConfig(config: RestaurantConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePublishedMenu(menu: PublishedMenuEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMenuItems(items: List<MenuItemEntity>)

    @Query("DELETE FROM menu_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM menu_items")
    suspend fun clearMenuItems()

    @Query("DELETE FROM restaurant_configuration")
    suspend fun clearRestaurantConfig()

    @Query("DELETE FROM published_menu")
    suspend fun clearPublishedMenu()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTerminalConfiguration(terminal: TerminalEntity)

    @Transaction
    suspend fun installMenu(
        restaurant: RestaurantConfigEntity,
        menu: PublishedMenuEntity,
        categories: List<CategoryEntity>,
        items: List<MenuItemEntity>
    ) {
        saveRestaurantConfig(restaurant)
        savePublishedMenu(menu)
        clearCategories()
        saveCategories(categories)
        clearMenuItems()
        saveMenuItems(items)
    }

    @Transaction
    suspend fun provisionTerminal(
        terminal: TerminalEntity,
        restaurant: RestaurantConfigEntity,
        menu: PublishedMenuEntity,
        categories: List<CategoryEntity>,
        items: List<MenuItemEntity>
    ) {
        saveTerminalConfiguration(terminal)
        installMenu(restaurant, menu, categories, items)
    }
    
    @Query("SELECT * FROM restaurant_configuration WHERE id = 0")
    fun observeRestaurantConfig(): Flow<RestaurantConfigEntity?>

    @Query("SELECT * FROM published_menu WHERE id = 0")
    fun observePublishedMenu(): Flow<PublishedMenuEntity?>

    @Query("SELECT * FROM menu_categories ORDER BY displayOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM menu_items ORDER BY displayOrder ASC")
    fun observeMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getMenuItem(id: String): MenuItemEntity?

    @Query("SELECT * FROM published_menu WHERE id = 0")
    suspend fun getPublishedMenu(): PublishedMenuEntity?

    @Query("SELECT * FROM restaurant_configuration WHERE id = 0")
    suspend fun getRestaurantConfig(): RestaurantConfigEntity?
}
