package com.venkoi.terminal.domain.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.service.MenuImportService
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SaleRepositoryIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var saleRepository: SaleRepository

    @Inject
    lateinit var menuRepository: MenuRepository

    @Inject
    lateinit var importService: MenuImportService

    @Inject
    lateinit var menuDao: com.venkoi.terminal.data.local.database.MenuDao

    @Inject
    lateinit var terminalDao: com.venkoi.terminal.data.local.database.TerminalDao

    @Inject
    lateinit var saleDao: com.venkoi.terminal.data.local.database.SaleDao

    private val menuV1 = """
    {
      "schemaVersion": 1,
      "restaurant": {
        "restaurantId": "rest-1", "restaurantName": "B", "timezone": "UTC", "currency": "USD", "businessDayCutoff": "04:00"
      },
      "menu": {
        "menuId": "menu-1", "publicationRevision": 1, "publishedAtUtc": "2026-08-15T21:00:00Z", "defaultCashDiscountPercent": "10"
      },
      "categories": [ { "id": "cat-1", "name": "C1", "displayOrder": 1 } ],
      "menuItems": [
        { "id": "item-1", "categoryId": "cat-1", "name": "Burger", "active": true, "displayOrder": 1, "regularPrice": "1500", "cashDiscountMode": "APPLY_DEFAULT", "commercialRevision": 20, "consumptionRevision": 5 }
      ]
    }
    """.trimIndent()

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            terminalDao.clear()
            menuDao.clearRestaurantConfig()
            menuDao.clearPublishedMenu()
            menuDao.clearCategories()
            menuDao.clearMenuItems()
            saleDao.clearSaleLines()
            saleDao.clearSales()
        }
    }

    private suspend fun provision() {
        val parseResult = importService.parseAndValidate(menuV1) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", parseResult)
    }

    @Test
    fun testMultipleSalesExistIndependently() = runBlocking {
        provision()
        val id1 = saleRepository.createSale("Mesa 4")
        val id2 = saleRepository.createSale("Mesa 7")

        val sales = saleRepository.observeOpenSales().first()
        assertEquals(2, sales.size)
        assertTrue(sales.any { it.tableLabel == "Mesa 4" })
        assertTrue(sales.any { it.tableLabel == "Mesa 7" })
    }

    @Test
    fun testDiscardSale() = runBlocking {
        provision()
        val id1 = saleRepository.createSale("Mesa 4")
        saleRepository.discardSale(id1)

        val sales = saleRepository.observeOpenSales().first()
        assertTrue(sales.isEmpty())
    }

    @Test
    fun testSnapshotPreservationOnMenuUpdate() = runBlocking {
        provision()
        val saleId = saleRepository.createSale("Mesa 4")
        saleRepository.addItem(saleId, "item-1")
        saleRepository.changeLinePricingMode(saleId, saleRepository.observeSaleLines(saleId).first().first().lineId, PricingMode.CASH)

        val initialLines = saleRepository.observeSaleLines(saleId).first()
        val initialLine = initialLines.first()
        assertEquals(0, BigDecimal("1500").compareTo(initialLine.regularUnitPriceSnapshot.amount))
        assertEquals(20, initialLine.commercialRevision)
        assertEquals(0, BigDecimal("10").compareTo(initialLine.cashDiscountPolicyPercentSnapshot))
        assertEquals(0, BigDecimal("1350").compareTo(initialLine.lineTotal.amount))

        // Update menu: Price 1700, Discount 15%, Revision 21
        val menuV2 = menuV1
            .replace("\"publicationRevision\": 1", "\"publicationRevision\": 2")
            .replace("\"defaultCashDiscountPercent\": \"10\"", "\"defaultCashDiscountPercent\": \"15\"")
            .replace("\"regularPrice\": \"1500\"", "\"regularPrice\": \"1700\"")
            .replace("\"commercialRevision\": 20", "\"commercialRevision\": 21")

        val parseResult = importService.parseAndValidate(menuV2) as MenuPackageImportResult.Success
        importService.importMenu(parseResult)

        // Verify existing line still uses V1 snapshots
        val linesAfterUpdate = saleRepository.observeSaleLines(saleId).first()
        val lineAfterUpdate = linesAfterUpdate.first()
        assertEquals(0, BigDecimal("1500").compareTo(lineAfterUpdate.regularUnitPriceSnapshot.amount))
        assertEquals(20, lineAfterUpdate.commercialRevision)
        assertEquals(0, BigDecimal("10").compareTo(lineAfterUpdate.cashDiscountPolicyPercentSnapshot))
        assertEquals(0, BigDecimal("1350").compareTo(lineAfterUpdate.lineTotal.amount))

        // Change quantity of old line - should STILL use V1 snapshots
        saleRepository.updateLineQuantity(saleId, lineAfterUpdate.lineId, BigDecimal("2"))
        val lineAfterQtyChange = saleRepository.observeSaleLines(saleId).first().first()
        assertEquals(0, BigDecimal("1500").compareTo(lineAfterQtyChange.regularUnitPriceSnapshot.amount))
        assertEquals(0, BigDecimal("10").compareTo(lineAfterQtyChange.cashDiscountPolicyPercentSnapshot))
        assertEquals(0, BigDecimal("2700").compareTo(lineAfterQtyChange.lineTotal.amount))
    }

    @Test
    fun testSaleLifecycleFlow() = runBlocking {
        provision()
        val saleId = saleRepository.createSale("Mesa 4")
        saleRepository.addItem(saleId, "item-1")
        
        // 1. Complete
        val result = saleRepository.completeSale(saleId)
        assertEquals(SaleCompletionResult.Success, result)
        
        val sale = saleRepository.observeSale(saleId).first()
        assertEquals(SaleStatus.COMPLETED, sale?.status)
        assertEquals(1, sale?.revision)
        assertNotNull(sale?.completedAtUtc)
        assertNotNull(sale?.businessDate)
        
        // Verify it disappeared from open
        val openSales = saleRepository.observeOpenSales().first()
        assertTrue(openSales.none { it.saleId == saleId })
        
        // Verify it appeared in history
        val historySales = saleRepository.observeHistorySales().first()
        assertTrue(historySales.any { it.saleId == saleId })
        
        // 2. Void
        val voidResult = saleRepository.voidSale(saleId)
        assertEquals(VoidResult.Success, voidResult)
        
        val voidedSale = saleRepository.observeSale(saleId).first()
        assertEquals(SaleStatus.VOIDED, voidedSale?.status)
        assertEquals(2, voidedSale?.revision)
        assertNotNull(voidedSale?.voidedAtUtc)
        assertEquals(sale?.completedAtUtc, voidedSale?.completedAtUtc) // Should preserve completion time
    }

    @Test
    fun testCannotMutateCompletedSale() = runBlocking {
        provision()
        val saleId = saleRepository.createSale("Mesa 4")
        saleRepository.addItem(saleId, "item-1")
        val lineId = saleRepository.observeSaleLines(saleId).first().first().lineId
        
        saleRepository.completeSale(saleId)
        
        // Try to mutate
        saleRepository.addItem(saleId, "item-1")
        saleRepository.updateLineQuantity(saleId, lineId, BigDecimal("10"))
        saleRepository.updateSaleLabel(saleId, "Changed")
        saleRepository.discardSale(saleId)
        
        val sale = saleRepository.observeSale(saleId).first()
        assertEquals(SaleStatus.COMPLETED, sale?.status)
        assertEquals("Mesa 4", sale?.tableLabel)
        
        val lines = saleRepository.observeSaleLines(saleId).first()
        assertEquals(1, lines.size)
        assertEquals(0, BigDecimal("1").compareTo(lines.first().quantity))
    }

    @Test
    fun testEmptySaleCannotComplete() = runBlocking {
        provision()
        val saleId = saleRepository.createSale("Mesa 4")
        val result = saleRepository.completeSale(saleId)
        assertEquals(SaleCompletionResult.EmptySale, result)
    }

    @Test
    fun testVoidIdempotency() = runBlocking {
        provision()
        val saleId = saleRepository.createSale("Mesa 4")
        saleRepository.addItem(saleId, "item-1")
        saleRepository.completeSale(saleId)
        
        saleRepository.voidSale(saleId)
        val voidedAtFirst = saleRepository.observeSale(saleId).first()?.voidedAtUtc
        
        // Void again
        val secondResult = saleRepository.voidSale(saleId)
        assertEquals(VoidResult.AlreadyVoided, secondResult)
        
        val voidedAtSecond = saleRepository.observeSale(saleId).first()?.voidedAtUtc
        assertEquals(voidedAtFirst, voidedAtSecond)
        assertEquals(2, saleRepository.observeSale(saleId).first()?.revision)
    }
}
