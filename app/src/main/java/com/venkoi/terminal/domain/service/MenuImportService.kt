package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.TerminalConfiguration
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import com.venkoi.terminal.integration.menu.MenuPackageParser
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed class MenuImportStatus {
    object Success : MenuImportStatus()
    data class Failure(val message: String) : MenuImportStatus()
}

class MenuImportService @Inject constructor(
    private val parser: MenuPackageParser,
    private val menuRepository: MenuRepository,
    private val terminalRepository: TerminalConfigurationRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    fun parseAndValidate(rawJson: String): MenuPackageImportResult {
        return parser.parse(rawJson)
    }

    suspend fun provisionTerminal(
        terminalName: String?,
        validated: MenuPackageImportResult.Success
    ): MenuImportStatus {
        try {
            val terminalId = com.venkoi.terminal.core.TerminalId(idGenerator.nextId())
            val restaurantId = RestaurantId(validated.restaurant.restaurantId)

            val config = TerminalConfiguration(
                terminalId = terminalId,
                restaurantId = restaurantId,
                terminalName = terminalName?.takeIf { it.isNotBlank() },
                createdAt = clock.now()
            )

            terminalRepository.provisionTerminal(
                configuration = config,
                restaurant = validated.restaurant,
                menu = validated.menu,
                categories = validated.categories,
                items = validated.items
            )
            return MenuImportStatus.Success
        } catch (e: Exception) {
            return MenuImportStatus.Failure("Provisioning failed: ${e.message}")
        }
    }

    suspend fun importMenu(validated: MenuPackageImportResult.Success): MenuImportStatus {
        val config = terminalRepository.getConfiguration()
            ?: return MenuImportStatus.Failure("Terminal not provisioned")

        if (config.restaurantId.value != validated.restaurant.restaurantId) {
            return MenuImportStatus.Failure("Menu belongs to a different restaurant: ${validated.restaurant.restaurantName}")
        }

        val currentMenu = menuRepository.getPublishedMenu()
        if (currentMenu != null) {
            if (validated.menu.publicationRevision < currentMenu.publicationRevision) {
                return MenuImportStatus.Failure("Attempted to import a stale menu (Revision ${validated.menu.publicationRevision} < ${currentMenu.publicationRevision})")
            }
            if (validated.menu.publicationRevision == currentMenu.publicationRevision) {
                val currentCategories = menuRepository.observeCategories().first()
                val currentItems = menuRepository.observeMenuItems().first()
                
                if (isSemanticSame(currentMenu, currentCategories, currentItems, validated)) {
                    return MenuImportStatus.Success
                } else {
                    return MenuImportStatus.Failure("Conflict: Different menu content for the same revision (${validated.menu.publicationRevision})")
                }
            }
        }

        try {
            menuRepository.installMenu(
                restaurant = validated.restaurant,
                menu = validated.menu,
                categories = validated.categories,
                items = validated.items
            )
            return MenuImportStatus.Success
        } catch (e: Exception) {
            return MenuImportStatus.Failure("Import failed: ${e.message}")
        }
    }

    private fun isSemanticSame(
        currentMenu: PublishedMenu,
        currentCategories: List<MenuCategory>,
        currentItems: List<MenuItem>,
        new: MenuPackageImportResult.Success
    ): Boolean {
        if (currentMenu.menuId != new.menu.menuId) return false
        if (currentMenu.defaultCashDiscountPercent.compareTo(new.menu.defaultCashDiscountPercent) != 0) return false
        if (currentMenu.publishedAtUtc != new.menu.publishedAtUtc) return false

        if (currentCategories.size != new.categories.size) return false
        val newCategoriesMap = new.categories.associateBy { it.id }
        for (c in currentCategories) {
            val other = newCategoriesMap[c.id] ?: return false
            if (c.name != other.name) return false
            if (c.displayOrder != other.displayOrder) return false
        }

        if (currentItems.size != new.items.size) return false
        val newItemsMap = new.items.associateBy { it.id }
        for (i in currentItems) {
            val other = newItemsMap[i.id] ?: return false
            if (i.categoryId != other.categoryId) return false
            if (i.name != other.name) return false
            if (i.active != other.active) return false
            if (i.displayOrder != other.displayOrder) return false
            if (i.regularPrice != other.regularPrice) return false
            if (i.cashDiscountMode != other.cashDiscountMode) return false
            if (i.commercialRevision != other.commercialRevision) return false
            if (i.consumptionRevision != other.consumptionRevision) return false
        }

        return true
    }

    fun MenuPackageImportResult.Failure.toErrorMessage(): String = when (this) {
        MenuPackageImportResult.Failure.UnreadableInput -> "File is empty or unreadable"
        MenuPackageImportResult.Failure.MalformedJson -> "Invalid JSON format"
        MenuPackageImportResult.Failure.MissingSchemaVersion -> "Missing schema version"
        is MenuPackageImportResult.Failure.UnsupportedSchemaVersion -> "Unsupported schema version: $version"
        MenuPackageImportResult.Failure.DeserializationFailure -> "Failed to decode menu package"
        is MenuPackageImportResult.Failure.SemanticValidationError -> message
    }
}
