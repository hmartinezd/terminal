package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.local.database.SaleDao
import com.venkoi.terminal.data.local.database.SaleEntity
import com.venkoi.terminal.data.local.database.SaleLineEntity
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import com.venkoi.terminal.domain.repository.VoidResult
import com.venkoi.terminal.domain.service.CalculateLinePricing
import com.venkoi.terminal.domain.service.CurrencyRoundingPolicy
import com.venkoi.terminal.domain.service.CompleteSale
import com.venkoi.terminal.domain.service.VoidSale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import com.venkoi.terminal.licensing.LicenseManager

class RoomSaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val terminalConfigRepository: TerminalConfigurationRepository,
    private val menuRepository: MenuRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val completeSaleService: CompleteSale,
    private val voidSaleService: VoidSale,
    private val licenseManager: LicenseManager
) : SaleRepository {

    override fun observeOpenSales(): Flow<List<Sale>> =
        saleDao.observeOpenSales().map { entities -> entities.map { it.toDomain() } }

    override fun observeHistorySales(): Flow<List<Sale>> =
        saleDao.observeHistorySales().map { entities -> entities.map { it.toDomain() } }

    override fun observeSale(saleId: SaleId): Flow<Sale?> =
        saleDao.observeSale(saleId).map { it?.toDomain() }

    override fun observeSaleLines(saleId: SaleId): Flow<List<SaleLine>> =
        saleDao.observeSaleLines(saleId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createSale(tableLabel: String?): SaleId {
        licenseManager.requireSelling()
        val config = terminalConfigRepository.getConfiguration()
            ?: throw IllegalStateException("Terminal not configured")
        
        val restaurant = menuRepository.getRestaurantConfiguration()
            ?: throw IllegalStateException("Restaurant not configured")

        val saleId = SaleId(idGenerator.nextId())
        val now = clock.now()
        val sale = Sale(
            saleId = saleId,
            terminalId = config.terminalId,
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = tableLabel,
            status = SaleStatus.OPEN,
            currencyCodeSnapshot = restaurant.currency.currencyCode,
            currencyScaleSnapshot = CurrencyRoundingPolicy.scaleFor(restaurant.currency)
        )
        saleDao.insertSale(SaleEntity.fromDomain(sale))
        return saleId
    }

    override suspend fun updateSaleLabel(saleId: SaleId, label: String?) {
        licenseManager.requireSelling()
        saleDao.updateSaleLabelGuarded(saleId, label, clock.now())
    }

    override suspend fun addItem(saleId: SaleId, menuItemId: String) {
        licenseManager.requireSelling()
        val saleEntity = saleDao.getSaleSync(saleId) ?: return
        if (saleEntity.status != SaleStatus.OPEN) return

        val item = menuRepository.getMenuItem(menuItemId) ?: return
        if (!item.active) return

        val menu = menuRepository.getPublishedMenu() ?: return
        
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
            currencyScale = saleEntity.currencyScaleSnapshot
        )

        val line = SaleLine(
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

        val existingLines = saleDao.getSaleLinesSync(saleId)
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
            saleDao.updateLinesAndSale(
                saleId = saleId,
                lines = listOf(SaleLineEntity.fromDomain(line)),
                updatedAt = clock.now()
            )
        }
    }

    override suspend fun updateLineQuantity(saleId: SaleId, lineId: LineId, newQuantity: BigDecimal) {
        licenseManager.requireSelling()
        val saleEntity = saleDao.getSaleSync(saleId) ?: return
        if (saleEntity.status != SaleStatus.OPEN) return

        val lines = saleDao.getSaleLinesSync(saleId)
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        
        if (lineEntity.saleId != saleId) return

        if (newQuantity <= BigDecimal.ZERO) {
            removeLine(saleId, lineId)
            return
        }

        val line = lineEntity.toDomain()

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = newQuantity,
            pricingMode = line.pricingMode,
            cashDiscountPolicyPercent = line.cashDiscountPolicyPercentSnapshot,
            currencyScale = saleEntity.currencyScaleSnapshot
        )
        
        val updatedLine = line.copy(
            quantity = newQuantity,
            cashDiscountApplied = pricing.cashDiscountApplied,
            cashDiscountPercent = pricing.cashDiscountPercent,
            cashDiscountAmount = pricing.cashDiscountAmount,
            finalUnitPrice = pricing.finalUnitPrice,
            lineTotal = pricing.lineTotal
        )
        
        saleDao.updateLinesAndSale(
            saleId = saleId,
            lines = listOf(SaleLineEntity.fromDomain(updatedLine)),
            updatedAt = clock.now()
        )
    }

    override suspend fun changeLinePricingMode(saleId: SaleId, lineId: LineId, pricingMode: PricingMode) {
        licenseManager.requireSelling()
        val saleEntity = saleDao.getSaleSync(saleId) ?: return
        if (saleEntity.status != SaleStatus.OPEN) return

        val lines = saleDao.getSaleLinesSync(saleId)
        val lineEntity = lines.find { it.lineId == lineId } ?: return
        
        if (lineEntity.saleId != saleId) return

        val line = lineEntity.toDomain()
        if (line.pricingMode == pricingMode) return

        val pricing = CalculateLinePricing.calculate(
            regularUnitPrice = line.regularUnitPriceSnapshot,
            quantity = line.quantity,
            pricingMode = pricingMode,
            cashDiscountPolicyPercent = line.cashDiscountPolicyPercentSnapshot,
            currencyScale = saleEntity.currencyScaleSnapshot
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

        if (otherEquivalent != null) {
            val mergedLine = otherEquivalent.toDomain().copy(
                quantity = otherEquivalent.quantity.add(line.quantity),
            )
            val mergedPricing = CalculateLinePricing.calculate(
                regularUnitPrice = mergedLine.regularUnitPriceSnapshot,
                quantity = mergedLine.quantity,
                pricingMode = mergedLine.pricingMode,
                cashDiscountPolicyPercent = mergedLine.cashDiscountPolicyPercentSnapshot,
                currencyScale = saleEntity.currencyScaleSnapshot
            )
            val finalMergedLine = mergedLine.copy(
                cashDiscountApplied = mergedPricing.cashDiscountApplied,
                cashDiscountPercent = mergedPricing.cashDiscountPercent,
                cashDiscountAmount = mergedPricing.cashDiscountAmount,
                finalUnitPrice = mergedPricing.finalUnitPrice,
                lineTotal = mergedPricing.lineTotal
            )

            saleDao.mergeLinesAndSale(
                saleId = saleId,
                lineIdToRemove = lineId,
                lineEntityToUpdate = SaleLineEntity.fromDomain(finalMergedLine),
                updatedAt = clock.now()
            )
        } else {
            saleDao.updateLinesAndSale(
                saleId = saleId,
                lines = listOf(SaleLineEntity.fromDomain(updatedLine)),
                updatedAt = clock.now()
            )
        }
    }

    override suspend fun removeLine(saleId: SaleId, lineId: LineId) {
        licenseManager.requireSelling()
        saleDao.removeLineAndUpdateSale(saleId, lineId, clock.now())
    }

    override suspend fun discardSale(saleId: SaleId) {
        saleDao.discardSaleGuarded(saleId, clock.now())
    }

    override suspend fun completeSale(saleId: SaleId): SaleCompletionResult {
        licenseManager.requireSelling()
        return completeSaleService.execute(saleId)
    }

    override suspend fun voidSale(saleId: SaleId): VoidResult {
        return voidSaleService.execute(saleId)
    }
}
