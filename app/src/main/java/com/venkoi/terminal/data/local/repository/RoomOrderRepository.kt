package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.local.database.OpenOrderEntity
import com.venkoi.terminal.data.local.database.OpenOrderLineEntity
import com.venkoi.terminal.data.local.database.OrderDao
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.OpenOrder
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.OrderStatus
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.OrderRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.CalculateLinePricing
import com.venkoi.terminal.domain.service.CurrencyRoundingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

class RoomOrderRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val terminalConfigRepository: TerminalConfigurationRepository,
    private val menuRepository: MenuRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) : OrderRepository {

    override fun observeOpenOrders(): Flow<List<OpenOrder>> =
        orderDao.observeOpenOrders().map { entities -> entities.map { it.toDomain() } }

    override fun observeOrder(saleId: SaleId): Flow<OpenOrder?> =
        orderDao.observeOrder(saleId).map { it?.toDomain() }

    override fun observeOrderLines(saleId: SaleId): Flow<List<OpenOrderLine>> =
        orderDao.observeOrderLines(saleId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createOrder(tableLabel: String?): SaleId {
        val config = terminalConfigRepository.getConfiguration()
            ?: throw IllegalStateException("Terminal not configured")
        
        val saleId = SaleId(idGenerator.nextId())
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
        if (orderEntity.status != OrderStatus.OPEN) return

        val updated = orderEntity.toDomain().copy(tableLabel = label, updatedAtUtc = clock.now())
        orderDao.insertOrder(OpenOrderEntity.fromDomain(updated))
    }

    override suspend fun addItem(saleId: SaleId, menuItemId: String) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        if (orderEntity.status != OrderStatus.OPEN) return

        val item = menuRepository.getMenuItem(menuItemId) ?: return
        if (!item.active) return

        val menu = menuRepository.getPublishedMenu() ?: return
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = CurrencyRoundingPolicy.scaleFor(restaurant.currency)

        val discountPolicyPercent = if (item.cashDiscountMode == CashDiscountMode.APPLY_DEFAULT) {
            menu.defaultCashDiscountPercent
        } else {
            BigDecimal.ZERO
        }

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = item.regularPrice,
            quantity = BigDecimal.ONE,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountPolicyPercent = discountPolicyPercent,
            currencyScale = currencyScale
        )

        val line = OpenOrderLine(
            lineId = LineId(idGenerator.nextId()),
            saleId = saleId,
            menuItemId = item.id,
            commercialRevision = item.commercialRevision,
            consumptionRevision = item.consumptionRevision,
            itemNameSnapshot = item.name,
            quantity = BigDecimal.ONE,
            regularUnitPriceSnapshot = item.regularPrice,
            cashDiscountModeSnapshot = item.cashDiscountMode,
            cashDiscountPolicyPercentSnapshot = discountPolicyPercent,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercent = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )

        val existingLines = orderDao.observeOrderLines(saleId).firstOrNull() ?: emptyList()
        val equivalentLine = existingLines.find {
            it.menuItemId == line.menuItemId &&
            it.commercialRevision == line.commercialRevision &&
            it.consumptionRevision == line.consumptionRevision &&
            it.regularUnitPriceSnapshot.amount.compareTo(line.regularUnitPriceSnapshot.amount) == 0 &&
            it.pricingMode == line.pricingMode &&
            it.cashDiscountModeSnapshot == line.cashDiscountModeSnapshot &&
            it.cashDiscountPolicyPercentSnapshot.compareTo(line.cashDiscountPolicyPercentSnapshot) == 0
        }

        if (equivalentLine != null) {
            updateLineQuantity(saleId, equivalentLine.toDomain().lineId, equivalentLine.quantity.add(BigDecimal.ONE))
        } else {
            val updatedOrder = orderEntity.toDomain().copy(updatedAtUtc = clock.now())
            orderDao.updateLinesAndOrder(
                OpenOrderEntity.fromDomain(updatedOrder),
                listOf(OpenOrderLineEntity.fromDomain(line))
            )
        }
    }

    override suspend fun updateLineQuantity(saleId: SaleId, lineId: LineId, newQuantity: BigDecimal) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        if (orderEntity.status != OrderStatus.OPEN) return

        val lines = orderDao.observeOrderLines(saleId).firstOrNull() ?: return
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        
        // Safety: line must belong to order
        if (lineEntity.saleId != saleId) return

        if (newQuantity <= BigDecimal.ZERO) {
            removeLine(saleId, lineId)
            return
        }

        val line = lineEntity.toDomain()
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = CurrencyRoundingPolicy.scaleFor(restaurant.currency)

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = newQuantity,
            pricingMode = line.pricingMode,
            cashDiscountPolicyPercent = line.cashDiscountPolicyPercentSnapshot,
            currencyScale = currencyScale
        )
        
        val updatedLine = line.copy(
            quantity = newQuantity,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercent = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )
        
        val updatedOrder = orderEntity.toDomain().copy(updatedAtUtc = clock.now())
        orderDao.updateLinesAndOrder(
            OpenOrderEntity.fromDomain(updatedOrder),
            listOf(OpenOrderLineEntity.fromDomain(updatedLine))
        )
    }

    override suspend fun changeLinePricingMode(saleId: SaleId, lineId: LineId, pricingMode: PricingMode) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        if (orderEntity.status != OrderStatus.OPEN) return

        val lines = orderDao.observeOrderLines(saleId).firstOrNull() ?: return
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        
        // Safety: line must belong to order
        if (lineEntity.saleId != saleId) return

        val line = lineEntity.toDomain()
        if (line.pricingMode == pricingMode) return

        val restaurant = menuRepository.getRestaurantConfiguration() ?: return
        val currencyScale = CurrencyRoundingPolicy.scaleFor(restaurant.currency)

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = line.quantity,
            pricingMode = pricingMode,
            cashDiscountPolicyPercent = line.cashDiscountPolicyPercentSnapshot,
            currencyScale = currencyScale
        )
        
        val updatedLine = line.copy(
            pricingMode = pricingMode,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercent = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )

        val otherEquivalent = lines.find {
            it.lineId != lineId &&
            it.menuItemId == line.menuItemId &&
            it.commercialRevision == line.commercialRevision &&
            it.consumptionRevision == line.consumptionRevision &&
            it.regularUnitPriceSnapshot.amount.compareTo(line.regularUnitPriceSnapshot.amount) == 0 &&
            it.pricingMode == pricingMode &&
            it.cashDiscountModeSnapshot == line.cashDiscountModeSnapshot &&
            it.cashDiscountPolicyPercentSnapshot.compareTo(line.cashDiscountPolicyPercentSnapshot) == 0
        }

        val updatedOrder = orderEntity.toDomain().copy(updatedAtUtc = clock.now())
        if (otherEquivalent != null) {
            val mergedLine = otherEquivalent.toDomain().copy(
                quantity = otherEquivalent.quantity.add(line.quantity),
                // Recalculate merged line
                // ... but since they are equivalent, we can just sum quantity and pricing will follow linearly? 
                // Better to recalculate properly.
            )
            val mergedPricing = CalculateLinePricing.calculate(
                regularUnitPrice = mergedLine.regularUnitPriceSnapshot,
                quantity = mergedLine.quantity,
                pricingMode = mergedLine.pricingMode,
                cashDiscountPolicyPercent = mergedLine.cashDiscountPolicyPercentSnapshot,
                currencyScale = currencyScale
            )
            val finalMergedLine = mergedLine.copy(
                cashDiscountApplied = mergedPricing.cashDiscountApplied,
                cashDiscountPercent = mergedPricing.cashDiscountPercent,
                cashDiscountAmount = mergedPricing.cashDiscountAmount,
                finalUnitPrice = mergedPricing.finalUnitPrice,
                lineTotal = mergedPricing.lineTotal
            )

            orderDao.mergeLinesAndOrder(
                saleId = saleId,
                lineIdToRemove = lineId,
                lineEntityToUpdate = OpenOrderLineEntity.fromDomain(finalMergedLine),
                orderEntity = OpenOrderEntity.fromDomain(updatedOrder)
            )
        } else {
            orderDao.updateLinesAndOrder(
                OpenOrderEntity.fromDomain(updatedOrder),
                listOf(OpenOrderLineEntity.fromDomain(updatedLine))
            )
        }
    }

    override suspend fun removeLine(saleId: SaleId, lineId: LineId) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        if (orderEntity.status != OrderStatus.OPEN) return
        
        // Safety: line must belong to order
        val lines = orderDao.observeOrderLines(saleId).firstOrNull() ?: return
        if (lines.none { it.lineId == lineId }) return

        val updatedOrder = orderEntity.toDomain().copy(updatedAtUtc = clock.now())
        orderDao.removeLineAndUpdateOrder(saleId, lineId, OpenOrderEntity.fromDomain(updatedOrder))
    }

    override suspend fun discardOrder(saleId: SaleId) {
        val orderEntity = orderDao.observeOrder(saleId).firstOrNull() ?: return
        if (orderEntity.status != OrderStatus.OPEN) return
        
        orderDao.discardOrder(saleId, clock.now())
    }
}
