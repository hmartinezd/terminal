package com.venkoi.terminal.domain.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.OrderStatus
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
class OrderRepositoryIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var orderRepository: OrderRepository

    @Inject
    lateinit var menuRepository: MenuRepository

    @Inject
    lateinit var importService: MenuImportService

    @Inject
    lateinit var menuDao: com.venkoi.terminal.data.local.database.MenuDao

    @Inject
    lateinit var terminalDao: com.venkoi.terminal.data.local.database.TerminalDao

    @Inject
    lateinit var orderDao: com.venkoi.terminal.data.local.database.OrderDao

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
            orderDao.clearOrderLines()
            orderDao.clearOrders()
        }
    }

    private suspend fun provision() {
        val parseResult = importService.parseAndValidate(menuV1) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", parseResult)
    }

    @Test
    fun testMultipleOrdersExistIndependently() = runBlocking {
        provision()
        val id1 = orderRepository.createOrder("Mesa 4")
        val id2 = orderRepository.createOrder("Mesa 7")

        val orders = orderRepository.observeOpenOrders().first()
        assertEquals(2, orders.size)
        assertTrue(orders.any { it.tableLabel == "Mesa 4" })
        assertTrue(orders.any { it.tableLabel == "Mesa 7" })
    }

    @Test
    fun testDiscardOrder() = runBlocking {
        provision()
        val id1 = orderRepository.createOrder("Mesa 4")
        orderRepository.discardOrder(id1)

        val orders = orderRepository.observeOpenOrders().first()
        assertTrue(orders.isEmpty())
    }

    @Test
    fun testSnapshotPreservationOnMenuUpdate() = runBlocking {
        provision()
        val saleId = orderRepository.createOrder("Mesa 4")
        orderRepository.addItem(saleId, "item-1")
        orderRepository.changeLinePricingMode(saleId, orderRepository.observeOrderLines(saleId).first().first().lineId, PricingMode.CASH)

        val initialLines = orderRepository.observeOrderLines(saleId).first()
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
        val linesAfterUpdate = orderRepository.observeOrderLines(saleId).first()
        val lineAfterUpdate = linesAfterUpdate.first()
        assertEquals(0, BigDecimal("1500").compareTo(lineAfterUpdate.regularUnitPriceSnapshot.amount))
        assertEquals(20, lineAfterUpdate.commercialRevision)
        assertEquals(0, BigDecimal("10").compareTo(lineAfterUpdate.cashDiscountPolicyPercentSnapshot))
        assertEquals(0, BigDecimal("1350").compareTo(lineAfterUpdate.lineTotal.amount))

        // Change quantity of old line - should STILL use V1 snapshots
        orderRepository.updateLineQuantity(saleId, lineAfterUpdate.lineId, BigDecimal("2"))
        val lineAfterQtyChange = orderRepository.observeOrderLines(saleId).first().first()
        assertEquals(0, BigDecimal("1500").compareTo(lineAfterQtyChange.regularUnitPriceSnapshot.amount))
        assertEquals(0, BigDecimal("10").compareTo(lineAfterQtyChange.cashDiscountPolicyPercentSnapshot))
        assertEquals(0, BigDecimal("2700").compareTo(lineAfterQtyChange.lineTotal.amount))

        // Add same item again - should create a NEW line with V2 snapshots
        orderRepository.addItem(saleId, "item-1")
        val allLines = orderRepository.observeOrderLines(saleId).first()
        assertEquals(2, allLines.size)
        
        val newLine = allLines.find { it.commercialRevision == 21 }
        assertNotNull(newLine)
        assertEquals(0, BigDecimal("1700").compareTo(newLine?.regularUnitPriceSnapshot?.amount))
        assertEquals(0, BigDecimal("15").compareTo(newLine?.cashDiscountPolicyPercentSnapshot))
    }

    @Test
    fun testMutationProtectionForDiscardedOrder() = runBlocking {
        provision()
        val saleId = orderRepository.createOrder("Mesa 4")
        orderRepository.discardOrder(saleId)

        // Try adding item to discarded order
        orderRepository.addItem(saleId, "item-1")
        val lines = orderRepository.observeOrderLines(saleId).first()
        assertTrue(lines.isEmpty())
    }

    @Test
    fun testInactiveItemRejection() = runBlocking {
        provision()
        // Make item-1 inactive and increment revision
        val inactiveMenu = menuV1
            .replace("\"active\": true", "\"active\": false")
            .replace("\"publicationRevision\": 1", "\"publicationRevision\": 2")
        val parseResult = importService.parseAndValidate(inactiveMenu) as MenuPackageImportResult.Success
        // We need to override the previous provision or just import
        importService.importMenu(parseResult) 

        val saleId = orderRepository.createOrder("Mesa 4")
        orderRepository.addItem(saleId, "item-1")
        
        val lines = orderRepository.observeOrderLines(saleId).first()
        assertTrue(lines.isEmpty())
    }
}
