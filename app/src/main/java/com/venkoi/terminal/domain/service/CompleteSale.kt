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
        val now = clock.now()
        
        val restaurant = menuRepository.getRestaurantConfiguration()
            ?: return SaleCompletionResult.Failure("Restaurant configuration missing")

        val businessDate = businessDateResolver.resolve(
            instant = now,
            zoneId = restaurant.timezone,
            cutoff = restaurant.businessDayCutoff
        )

        val resultCode = saleDao.completeSaleValidated(
            saleId = saleId,
            completedAt = now,
            businessDate = businessDate,
            updatedAt = now
        )

        return when (resultCode) {
            0 -> SaleCompletionResult.Success
            1 -> SaleCompletionResult.NotFound
            2 -> SaleCompletionResult.NotOpen
            3 -> SaleCompletionResult.EmptySale
            4 -> SaleCompletionResult.InvalidQuantity
            else -> SaleCompletionResult.Failure("Unknown error")
        }
    }
}
