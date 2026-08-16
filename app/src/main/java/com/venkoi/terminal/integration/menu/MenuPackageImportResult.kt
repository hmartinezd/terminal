package com.venkoi.terminal.integration.menu

import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration

sealed class MenuPackageImportResult {
    data class Success(
        val restaurant: RestaurantConfiguration,
        val menu: PublishedMenu,
        val categories: List<MenuCategory>,
        val items: List<MenuItem>
    ) : MenuPackageImportResult()

    sealed class Failure : MenuPackageImportResult() {
        object UnreadableInput : Failure()
        object MalformedJson : Failure()
        object MissingSchemaVersion : Failure()
        data class UnsupportedSchemaVersion(val version: Int) : Failure()
        object DeserializationFailure : Failure()
        data class SemanticValidationError(val message: String) : Failure()
    }
}
