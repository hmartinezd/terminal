package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.BusinessDateResolver
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.local.database.SaleDao
import com.venkoi.terminal.data.local.database.SaleEntity
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import java.math.BigDecimal
import javax.inject.Inject

class CompleteSale @Inject constructor(
    private val saleDao: SaleDao,
    private val menuRepository: MenuRepository,
    private val businessDateResolver: BusinessDateResolver,
    private val clock: Clock
) {
    suspend fun execute(saleId: SaleId): SaleCompletionResult {
        val saleEntity = saleDao.getSaleSync(saleId) ?: return SaleCompletionResult.NotFound
        if (saleEntity.status != SaleStatus.OPEN) return SaleCompletionResult.NotOpen

        val lines = saleDao.getSaleLinesSync(saleId)
        if (lines.isEmpty()) return SaleCompletionResult.EmptySale

        if (lines.any { it.quantity <= BigDecimal.ZERO }) {
            return SaleCompletionResult.InvalidQuantity
        }

        val restaurant = menuRepository.getRestaurantConfiguration()
            ?: return SaleCompletionResult.Failure("Restaurant configuration missing")

        val now = clock.now()
        val businessDate = businessDateResolver.resolve(
            instant = now,
            zoneId = restaurant.timezone,
            cutoff = restaurant.businessDayCutoff
        )

        val completedSale = saleEntity.copy(
            status = SaleStatus.COMPLETED,
            revision = 1,
            completedAtUtc = now,
            businessDate = businessDate,
            updatedAtUtc = now
        )

        saleDao.insertSale(completedSale)

        return SaleCompletionResult.Success
    }
}
