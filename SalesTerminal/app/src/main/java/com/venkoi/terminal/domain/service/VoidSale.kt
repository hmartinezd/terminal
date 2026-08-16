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
        val now = clock.now()
        val saleEntity = saleDao.getSaleSync(saleId) ?: return VoidResult.NotFound
        
        if (saleEntity.status == SaleStatus.VOIDED) {
            return VoidResult.AlreadyVoided
        }
        
        if (saleEntity.status != SaleStatus.COMPLETED) {
            return VoidResult.NotCompleted
        }

        val affected = saleDao.voidSaleGuarded(
            saleId = saleId,
            voidedAt = now,
            updatedAt = now
        )

        return if (affected > 0) {
            VoidResult.Success
        } else {
            // Re-read to see if it was already voided by someone else
            val reRead = saleDao.getSaleSync(saleId)
            if (reRead?.status == SaleStatus.VOIDED) {
                VoidResult.AlreadyVoided
            } else {
                VoidResult.NotCompleted
            }
        }
    }
}
