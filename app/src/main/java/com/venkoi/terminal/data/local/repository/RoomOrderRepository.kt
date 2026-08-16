package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.local.database.OpenOrderEntity
import com.venkoi.terminal.data.local.database.OpenOrderLineEntity
import com.venkoi.terminal.data.local.database.OrderDao
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.OpenOrder
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.OrderStatus
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.OrderRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.CalculateLinePricing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

class RoomOrderRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val terminalConfigRepository: TerminalConfigurationRepository,
    private val menuRepository: MenuRepository,
    private val clock: Clock
) : OrderRepository {

    override fun observeOpenOrders(): Flow<List<OpenOrder>> =
        orderDao.observeOpenOrders().map { entities -> entities.map { it.toDomain() } }

    override fun observeOrder(saleId: SaleId): Flow<OpenOrder?> =
        orderDao.observeOrder(saleId).map { it?.toDomain() }

    override fun observeOrderLines(saleId: SaleId): Flow<List<OpenOrderLine>> =
        orderDao.observeOrderLines(saleId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createOrder(tableLabel: String?): SaleId {
        val config = terminalConfigRepository.observeConfiguration().firstOrNull() 
            ?: throw IllegalStateException("Terminal not configured")
        
        val saleId = SaleId()
        val now = clock.now()
        val order = OpenOrder(
            saleId = saleId,
            terminalId = config.terminalId,
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = tableLabel,
            status = OrderStatus.OPEN
        )
        orderDao.insertOrder(OpenOrderEntity.fromDomain(order))
        return saleId
    }

    override suspend fun updateOrderLabel(saleId: SaleId, label: String?) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        val updated = orderEntity.toDomain().copy(tableLabel = label, updatedAtUtc = clock.now())
        orderDao.insertOrder(OpenOrderEntity.fromDomain(updated))
    }

    override suspend fun addItem(saleId: SaleId, item: MenuItem) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        val menu = menuRepository.getPublishedMenu() ?: return
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = restaurant.currency.defaultFractionDigits

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = item.regularPrice,
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountMode = item.cashDiscountMode,
            restaurantDefaultCashDiscountPercent = menu.defaultCashDiscountPercent,
            currencyScale = currencyScale
        )

        val line = OpenOrderLine(
            lineId = LineId(),
            saleId = saleId,
            menuItemId = item.id,
            commercialRevision = item.commercialRevision,
            consumptionRevision = item.consumptionRevision,
            itemNameSnapshot = item.name,
            quantity = BigDecimal.ONE,
            regularUnitPriceSnapshot = item.regularPrice,
            cashDiscountModeSnapshot = item.cashDiscountMode,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercentSnapshot = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )

        val existingLines = orderDao.observeOrderLines(saleId).firstOrNull() ?: emptyList()
        val equivalentLine = existingLines.find {
            it.menuItemId == line.menuItemId &&
            it.commercialRevision == line.commercialRevision &&
            it.consumptionRevision == line.consumptionRevision &&
            it.regularUnitPriceSnapshot == line.regularUnitPriceSnapshot &&
            it.pricingMode == line.pricingMode &&
            it.cashDiscountModeSnapshot == line.cashDiscountModeSnapshot &&
            it.cashDiscountPercentSnapshot == line.cashDiscountPercentSnapshot
        }

        if (equivalentLine != null) {
            updateLineQuantity(saleId, equivalentLine.lineId, equivalentLine.quantity.add(BigDecimal.ONE))
        } else {
            orderDao.insertOrderLines(listOf(OpenOrderLineEntity.fromDomain(line)))
            orderDao.insertOrder(OpenOrderEntity.fromDomain(orderEntity.toDomain().copy(updatedAtUtc = clock.now())))
        }
    }

    override suspend fun updateLineQuantity(saleId: SaleId, lineId: LineId, newQuantity: BigDecimal) {
        if (newQuantity <= BigDecimal.ZERO) {
            removeLine(saleId, lineId)
            return
        }

        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        val lines = orderDao.observeOrderLines(saleId).firstOrNull() ?: return
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        val line = lineEntity.toDomain()
        val menu = menuRepository.getPublishedMenu() ?: return
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = restaurant.currency.defaultFractionDigits

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = newQuantity,
            pricingMode = line.pricingMode,
            cashDiscountMode = line.cashDiscountModeSnapshot,
            restaurantDefaultCashDiscountPercent = menu.defaultCashDiscountPercent,
            currencyScale = currencyScale
        )
        
        val updatedLine = line.copy(
            quantity = newQuantity,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercentSnapshot = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )
        
        orderDao.insertOrderLines(listOf(OpenOrderLineEntity.fromDomain(updatedLine)))
        orderDao.insertOrder(OpenOrderEntity.fromDomain(orderEntity.toDomain().copy(updatedAtUtc = clock.now())))
    }

    override suspend fun changeLinePricingMode(saleId: SaleId, lineId: LineId, pricingMode: PricingMode) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        val lines = orderDao.observeOrderLines(saleId).firstOrNull() ?: return
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        val line = lineEntity.toDomain()
        
        if (line.pricingMode == pricingMode) return

        val menu = menuRepository.getPublishedMenu() ?: return
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = restaurant.currency.defaultFractionDigits

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = line.quantity,
            pricingMode = pricingMode,
            cashDiscountMode = line.cashDiscountModeSnapshot,
            restaurantDefaultCashDiscountPercent = menu.defaultCashDiscountPercent,
            currencyScale = currencyScale
        )
        
        val updatedLine = line.copy(
            pricingMode = pricingMode,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercentSnapshot = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )

        val otherEquivalent = lines.find {
            it.lineId != lineId &&
            it.menuItemId == line.menuItemId &&
            it.commercialRevision == line.commercialRevision &&
            it.consumptionRevision == line.consumptionRevision &&
            it.regularUnitPriceSnapshot == line.regularUnitPriceSnapshot &&
            it.pricingMode == pricingMode &&
            it.cashDiscountModeSnapshot == line.cashDiscountModeSnapshot &&
            it.cashDiscountPercentSnapshot == pricing.cashDiscountPercent
        }

        if (otherEquivalent != null) {
            orderDao.deleteOrderLine(lineId)
            updateLineQuantity(saleId, otherEquivalent.lineId, otherEquivalent.quantity.add(line.quantity))
        } else {
            orderDao.insertOrderLines(listOf(OpenOrderLineEntity.fromDomain(updatedLine)))
            orderDao.insertOrder(OpenOrderEntity.fromDomain(orderEntity.toDomain().copy(updatedAtUtc = clock.now())))
        }
    }

    override suspend fun removeLine(saleId: SaleId, lineId: LineId) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        orderDao.deleteOrderLine(lineId)
        orderDao.insertOrder(OpenOrderEntity.fromDomain(orderEntity.toDomain().copy(updatedAtUtc = clock.now())))
    }

    override suspend fun discardOrder(saleId: SaleId) {
        orderDao.discardOrder(saleId, clock.now())
    }
}
