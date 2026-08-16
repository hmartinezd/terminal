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

        // Take OLD line, switch CASH -> TRANSFER -> CASH
        val oldLineId = lineAfterQtyChange.lineId
        orderRepository.changeLinePricingMode(saleId, oldLineId, PricingMode.TRANSFER)
        val lineTransfer = orderRepository.observeOrderLines(saleId).first().find { it.lineId == oldLineId }
        assertEquals(false, lineTransfer?.cashDiscountApplied)
        assertEquals(0, BigDecimal("3000").compareTo(lineTransfer?.lineTotal?.amount)) // 1500 * 2

        orderRepository.changeLinePricingMode(saleId, oldLineId, PricingMode.CASH)
        val lineCashBack = orderRepository.observeOrderLines(saleId).first().find { it.lineId == oldLineId }
        assertEquals(true, lineCashBack?.cashDiscountApplied)
        assertEquals(0, BigDecimal("10").compareTo(lineCashBack?.cashDiscountPercent)) // Still 10%
        assertEquals(0, BigDecimal("2700").compareTo(lineCashBack?.lineTotal?.amount))
    }

    @Test
    fun testCashAndTransferCoexistence() = runBlocking {
        provision()
        val saleId = orderRepository.createOrder("Mesa 4")
        
        // Add one TRANSFER
        orderRepository.addItem(saleId, "item-1")
        
        // Add one CASH (item-1 has cashDiscountMode = APPLY_DEFAULT)
        // We need to add it, then change mode.addItem currently defaults to TRANSFER.
        orderRepository.addItem(saleId, "item-1")
        val linesAfterTwoAdds = orderRepository.observeOrderLines(saleId).first()
        // Should be merged as TRANSFER qty 2
        assertEquals(1, linesAfterTwoAdds.size)
        assertEquals(BigDecimal("2"), linesAfterTwoAdds.first().quantity)

        // Add item-1 again, but then split one off by changing its mode? 
        // Actually the repo should merge if identical. To have coexistence, we must change mode of some quantity.
        // But our repo changes mode for the WHOLE line.
        // Wait, "The same product can be split across CASH and TRANSFER lines" 
        // This implies if I have a line and I change mode, it shouldn't necessarily merge if another exists?
        // If I have 2 TRANSFER and I want 1 CASH, I might need to decrease one and add another.
        // Or if I change mode and an equivalent exists, it merges.
        
        // Let's test adding a line, changing mode, then adding another.
        val lineId1 = linesAfterTwoAdds.first().lineId
        orderRepository.updateLineQuantity(saleId, lineId1, BigDecimal.ONE) // Now 1 TRANSFER
        
        // Add another, it will merge into the TRANSFER one.
        orderRepository.addItem(saleId, "item-1")
        val lineId2 = orderRepository.observeOrderLines(saleId).first().first().lineId
        
        // Change one to CASH
        // To do this without merging back, we'd need them to be different.
        // Ah, if I change lineId2 to CASH, it will NOT merge with itself. 
        // If no other CASH exists, it just becomes a CASH line.
        orderRepository.changeLinePricingMode(saleId, lineId2, PricingMode.CASH)
        
        // Now add another TRANSFER. It should merge with the TRANSFER line? 
        // Actually I only have ONE line right now (it was qty 2, I changed its mode).
        // Wait, if I have 1 line qty 2 TRANSFER and I change it to CASH, I have 1 line qty 2 CASH.
        
        // To get TWO lines, I need to have them in different modes.
        orderRepository.addItem(saleId, "item-1") // Adds a TRANSFER line.
        val lines = orderRepository.observeOrderLines(saleId).first()
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.pricingMode == PricingMode.CASH })
        assertTrue(lines.any { it.pricingMode == PricingMode.TRANSFER })
    }

    @Test
    fun testCrossOrderMutationProtection() = runBlocking {
        provision()
        val id4 = orderRepository.createOrder("Mesa 4")
        val id7 = orderRepository.createOrder("Mesa 7")
        
        orderRepository.addItem(id7, "item-1")
        val lineId7 = orderRepository.observeOrderLines(id7).first().first().lineId
        
        // Try to remove Mesa 7's line using Mesa 4's saleId
        orderRepository.removeLine(id4, lineId7)
        assertEquals(1, orderRepository.observeOrderLines(id7).first().size)
        
        // Try to change quantity
        orderRepository.updateLineQuantity(id4, lineId7, BigDecimal("5"))
        assertEquals(0, BigDecimal("1").compareTo(orderRepository.observeOrderLines(id7).first().first().quantity))
        
        // Try to change pricing mode
        orderRepository.changeLinePricingMode(id4, lineId7, PricingMode.CASH)
        assertEquals(PricingMode.TRANSFER, orderRepository.observeOrderLines(id7).first().first().pricingMode)
    }

    @Test
    fun testIdentityStability() = runBlocking {
        provision()
        val id1 = orderRepository.createOrder("Mesa 4")
        val id2 = orderRepository.createOrder("Mesa 7")
        assertNotEquals(id1, id2)
        
        orderRepository.addItem(id1, "item-1")
        val lineId = orderRepository.observeOrderLines(id1).first().first().lineId
        
        orderRepository.updateLineQuantity(id1, lineId, BigDecimal("2"))
        assertEquals(lineId, orderRepository.observeOrderLines(id1).first().first().lineId)
        
        orderRepository.changeLinePricingMode(id1, lineId, PricingMode.CASH)
        assertEquals(lineId, orderRepository.observeOrderLines(id1).first().first().lineId)
        
        orderRepository.updateOrderLabel(id1, "Mesa 4 Modified")
        assertEquals(id1, orderRepository.observeOpenOrders().first().find { it.tableLabel?.contains("Modified") == true }?.saleId)
    }

    @Test
    fun testDiscardedOrderImmutability() = runBlocking {
        provision()
        val saleId = orderRepository.createOrder("Mesa 4")
        orderRepository.addItem(saleId, "item-1")
        val lineId = orderRepository.observeOrderLines(saleId).first().first().lineId
        
        orderRepository.discardOrder(saleId)
        
        // Try to mutate
        orderRepository.updateOrderLabel(saleId, "Discarded but changed")
        orderRepository.updateLineQuantity(saleId, lineId, BigDecimal("10"))
        orderRepository.changeLinePricingMode(saleId, lineId, PricingMode.CASH)
        orderRepository.removeLine(saleId, lineId)
        
        // Verify via DAO directly since repo observation filters out non-OPEN
        val entity = orderDao.observeOrder(saleId).first()
        assertEquals("Mesa 4", entity?.tableLabel)
        assertEquals(OrderStatus.DISCARDED, entity?.status)
        
        val lines = orderDao.observeOrderLines(saleId).first()
        assertEquals(1, lines.size)
        assertEquals(0, BigDecimal("1").compareTo(lines.first().quantity))
        assertEquals(PricingMode.TRANSFER, lines.first().pricingMode)
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
