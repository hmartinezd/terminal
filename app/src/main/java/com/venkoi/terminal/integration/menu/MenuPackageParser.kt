package com.venkoi.terminal.integration.menu

import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

class MenuPackageParser(private val json: Json) {

    fun parse(rawJson: String): MenuPackageImportResult {
        if (rawJson.isBlank()) {
            return MenuPackageImportResult.Failure.UnreadableInput
        }

        val jsonElement = try {
            json.parseToJsonElement(rawJson)
        } catch (_: Exception) {
            return MenuPackageImportResult.Failure.MalformedJson
        }

        val schemaVersion = jsonElement.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return MenuPackageImportResult.Failure.MissingSchemaVersion

        if (schemaVersion != 1) {
            return MenuPackageImportResult.Failure.UnsupportedSchemaVersion(schemaVersion)
        }

        val dto = try {
            json.decodeFromJsonElement(MenuPackageV1.serializer(), jsonElement)
        } catch (e: Exception) {
            return MenuPackageImportResult.Failure.DeserializationFailure
        }

        return validateAndMap(dto)
    }

    private fun validateAndMap(dto: MenuPackageV1): MenuPackageImportResult {
        // Semantic validation
        try {
            if (dto.restaurant.restaurantId.isBlank()) return semanticError("restaurantId is blank")
            if (dto.restaurant.restaurantName.isBlank()) return semanticError("restaurantName is blank")
            val zoneId = try { ZoneId.of(dto.restaurant.timezone) } catch (e: Exception) { return semanticError("Invalid timezone: ${dto.restaurant.timezone}") }
            val currency = try { Currency.getInstance(dto.restaurant.currency) } catch (e: Exception) { return semanticError("Invalid currency: ${dto.restaurant.currency}") }
            val cutoff = try { LocalTime.parse(dto.restaurant.businessDayCutoff) } catch (e: Exception) { return semanticError("Invalid businessDayCutoff: ${dto.restaurant.businessDayCutoff}") }

            if (dto.menu.menuId.isBlank()) return semanticError("menuId is blank")
            if (dto.menu.publicationRevision <= 0) return semanticError("publicationRevision must be positive")
            val publishedAt = try { Instant.parse(dto.menu.publishedAtUtc) } catch (e: Exception) { return semanticError("Invalid publishedAtUtc: ${dto.menu.publishedAtUtc}") }
            val defaultDiscount = try { BigDecimal(dto.menu.defaultCashDiscountPercent) } catch (e: Exception) { return semanticError("Invalid defaultCashDiscountPercent: ${dto.menu.defaultCashDiscountPercent}") }
            if (defaultDiscount < BigDecimal.ZERO || defaultDiscount > BigDecimal("100")) return semanticError("defaultCashDiscountPercent out of range: $defaultDiscount")

            val categories = mutableListOf<MenuCategory>()
            val categoryIds = mutableSetOf<String>()
            for (c in dto.categories) {
                if (c.id.isBlank()) return semanticError("Category ID is blank")
                if (!categoryIds.add(c.id)) return semanticError("Duplicate category ID: ${c.id}")
                if (c.name.isBlank()) return semanticError("Category name is blank for ${c.id}")
                categories.add(MenuCategory(c.id, c.name, c.displayOrder))
            }

            val items = mutableListOf<MenuItem>()
            val itemIds = mutableSetOf<String>()
            for (i in dto.menuItems) {
                if (i.id.isBlank()) return semanticError("MenuItem ID is blank")
                if (!itemIds.add(i.id)) return semanticError("Duplicate MenuItem ID: ${i.id}")
                if (!categoryIds.contains(i.categoryId)) return semanticError("MenuItem ${i.id} references missing category: ${i.categoryId}")
                if (i.name.isBlank()) return semanticError("MenuItem name is blank for ${i.id}")
                if (i.regularPrice.amount < BigDecimal.ZERO) return semanticError("Negative price for MenuItem ${i.id}")
                if (i.commercialRevision < 0) return semanticError("Invalid commercialRevision for ${i.id}")
                if (i.consumptionRevision < 0) return semanticError("Invalid consumptionRevision for ${i.id}")

                items.add(
                    MenuItem(
                        id = i.id,
                        categoryId = i.categoryId,
                        name = i.name,
                        active = i.active,
                        displayOrder = i.displayOrder,
                        regularPrice = i.regularPrice,
                        cashDiscountMode = i.cashDiscountMode,
                        commercialRevision = i.commercialRevision,
                        consumptionRevision = i.consumptionRevision
                    )
                )
            }

            return MenuPackageImportResult.Success(
                restaurant = RestaurantConfiguration(
                    restaurantId = dto.restaurant.restaurantId,
                    restaurantName = dto.restaurant.restaurantName,
                    timezone = zoneId,
                    currency = currency,
                    businessDayCutoff = cutoff
                ),
                menu = PublishedMenu(
                    menuId = dto.menu.menuId,
                    publicationRevision = dto.menu.publicationRevision,
                    publishedAtUtc = publishedAt,
                    defaultCashDiscountPercent = defaultDiscount
                ),
                categories = categories,
                items = items
            )

        } catch (e: Exception) {
            return MenuPackageImportResult.Failure.SemanticValidationError(e.message ?: "Unknown validation error")
        }
    }

    private fun semanticError(message: String) = MenuPackageImportResult.Failure.SemanticValidationError(message)
}
