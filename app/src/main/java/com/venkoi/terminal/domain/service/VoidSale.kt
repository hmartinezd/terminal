package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.local.database.SaleDao
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.repository.VoidResult
import javax.inject.Inject

class VoidSale @Inject constructor(
    private val saleDao: SaleDao,
    private val clock: Clock
) {
    suspend fun execute(saleId: SaleId): VoidResult {
        val saleEntity = saleDao.getSaleSync(saleId) ?: return VoidResult.NotFound
        
        if (saleEntity.status == SaleStatus.VOIDED) {
            return VoidResult.AlreadyVoided
        }
        
        if (saleEntity.status != SaleStatus.COMPLETED) {
            return VoidResult.NotCompleted
        }

        val voidedSale = saleEntity.copy(
            status = SaleStatus.VOIDED,
            revision = 2,
            voidedAtUtc = clock.now(),
            updatedAtUtc = clock.now()
        )

        saleDao.insertSale(voidedSale)

        return VoidResult.Success
    }
}
