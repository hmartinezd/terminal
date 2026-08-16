package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.OpenOrder
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.PricingMode
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface OrderRepository {
    fun observeOpenOrders(): Flow<List<OpenOrder>>
    fun observeOrder(saleId: SaleId): Flow<OpenOrder?>
    fun observeOrderLines(saleId: SaleId): Flow<List<OpenOrderLine>>
    
    suspend fun createOrder(tableLabel: String? = null): SaleId
    suspend fun updateOrderLabel(saleId: SaleId, label: String?)
    suspend fun addItem(saleId: SaleId, menuItemId: String)
    suspend fun updateLineQuantity(saleId: SaleId, lineId: LineId, newQuantity: BigDecimal)
    suspend fun changeLinePricingMode(saleId: SaleId, lineId: LineId, pricingMode: PricingMode)
    suspend fun removeLine(saleId: SaleId, lineId: LineId)
    suspend fun discardOrder(saleId: SaleId)
}
