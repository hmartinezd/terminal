package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface SaleRepository {
    fun observeOpenSales(): Flow<List<Sale>>
    fun observeHistorySales(): Flow<List<Sale>>
    fun observeSale(saleId: SaleId): Flow<Sale?>
    fun observeSaleLines(saleId: SaleId): Flow<List<SaleLine>>
    
    suspend fun createSale(tableLabel: String? = null): SaleId
    suspend fun updateSaleLabel(saleId: SaleId, label: String?)
    suspend fun addItem(saleId: SaleId, menuItemId: String)
    suspend fun updateLineQuantity(saleId: SaleId, lineId: LineId, newQuantity: BigDecimal)
    suspend fun changeLinePricingMode(saleId: SaleId, lineId: LineId, pricingMode: PricingMode)
    suspend fun removeLine(saleId: SaleId, lineId: LineId)
    suspend fun discardSale(saleId: SaleId)
    
    suspend fun completeSale(saleId: SaleId): SaleCompletionResult
    suspend fun voidSale(saleId: SaleId): VoidResult
}

sealed class SaleCompletionResult {
    object Success : SaleCompletionResult()
    object NotFound : SaleCompletionResult()
    object NotOpen : SaleCompletionResult()
    object EmptySale : SaleCompletionResult()
    object InvalidQuantity : SaleCompletionResult()
    data class Failure(val message: String) : SaleCompletionResult()
}

sealed class VoidResult {
    object Success : VoidResult()
    object NotFound : VoidResult()
    object NotCompleted : VoidResult()
    object AlreadyVoided : VoidResult()
}
