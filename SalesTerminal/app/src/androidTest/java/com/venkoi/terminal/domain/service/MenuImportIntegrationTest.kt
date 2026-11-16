package com.venkoi.terminal.domain.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MenuImportIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var importService: MenuImportService

    @Inject
    lateinit var terminalRepository: TerminalConfigurationRepository

    @Inject
    lateinit var menuRepository: MenuRepository

    @Inject
    lateinit var menuDao: com.venkoi.terminal.data.local.database.MenuDao

    @Inject
    lateinit var terminalDao: com.venkoi.terminal.data.local.database.TerminalDao

    @Inject
    lateinit var saleDao: com.venkoi.terminal.data.local.database.SaleDao

    @Inject
    lateinit var saleRepository: com.venkoi.terminal.domain.repository.SaleRepository

    private val validJson = """
    {
      "schemaVersion": 1,
      "restaurant": {
        "restaurantId": "rest-1", "restaurantName": "Bistro", "timezone": "America/Havana", "currency": "CUP", "businessDayCutoff": "04:00"
      },
      "menu": {
        "menuId": "menu-1", "publicationRevision": 10, "publishedAtUtc": "2026-08-15T21:00:00Z", "defaultCashDiscountPercent": "10"
      },
      "categories": [
        { "id": "cat-1", "name": "C1", "displayOrder": 1 }
      ],
      "menuItems": [
        { "id": "item-1", "categoryId": "cat-1", "name": "I1", "active": true, "displayOrder": 1, "regularPrice": "100", "cashDiscountMode": "APPLY_DEFAULT", "commercialRevision": 1, "consumptionRevision": 1 }
      ]
    }
    """.trimIndent()

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            saleDao.clearSaleLines()
            saleDao.clearSales()
            terminalDao.clear()
            menuDao.clearRestaurantConfig()
            menuDao.clearPublishedMenu()
            menuDao.clearCategories()
            menuDao.clearMenuItems()
        }
    }

    @Test
    fun testFirstProvisioning() = runBlocking {
        val parseResult = importService.parseAndValidate(validJson)
        assertTrue(parseResult is MenuPackageImportResult.Success)

        val status = importService.provisionTerminal("Terminal 1", parseResult as MenuPackageImportResult.Success)
        assertTrue(status is MenuImportStatus.Success)

        val config = terminalRepository.getConfiguration()
        assertNotNull(config)
        assertEquals("rest-1", config?.restaurantId?.value)
        assertEquals("Terminal 1", config?.terminalName)

        val menu = menuRepository.getPublishedMenu()
        assertNotNull(menu)
        assertEquals(10, menu?.publicationRevision)
    }

    @Test
    fun testImportNewRevision() = runBlocking {
        // Provision first
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Import revision 11
        val newJson = validJson.replace("\"publicationRevision\": 10", "\"publicationRevision\": 11")
        val secondResult = importService.parseAndValidate(newJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Success)
        assertEquals(11, menuRepository.getPublishedMenu()?.publicationRevision)
    }

    @Test
    fun testRejectStaleRevision() = runBlocking {
        // Provision revision 10
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Try revision 9
        val staleJson = validJson.replace("\"publicationRevision\": 10", "\"publicationRevision\": 9")
        val secondResult = importService.parseAndValidate(staleJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("stale"))
        assertEquals(10, menuRepository.getPublishedMenu()?.publicationRevision)
    }

    @Test
    fun testRejectDifferentRestaurant() = runBlocking {
        // Provision restaurant rest-1
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Try restaurant rest-2
        val otherJson = validJson.replace("\"restaurantId\": \"rest-1\"", "\"restaurantId\": \"rest-2\"")
        val secondResult = importService.parseAndValidate(otherJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("different restaurant"))
    }

    @Test
    fun testSameRevisionSameContentIsNoOp() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        val status = importService.importMenu(firstResult)
        assertTrue(status is MenuImportStatus.Success)
    }

    @Test
    fun testSameRevisionDifferentContentIsFailure() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Same revision 10, but change item name
        val conflictingJson = validJson.replace("\"name\": \"I1\"", "\"name\": \"I1-Changed\"")
        val secondResult = importService.parseAndValidate(conflictingJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("Conflict"))
    }

    @Test
    fun testProvisionOnlyOnce() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        val status = importService.provisionTerminal("T2", firstResult)
        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("already provisioned"))
    }

    @Test
    fun testSameRevisionEquivalentDecimalFormattingIsNoOp() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Change "100" to "100.00"
        val equivalentJson = validJson.replace("\"regularPrice\": \"100\"", "\"regularPrice\": \"100.00\"")
        val secondResult = importService.parseAndValidate(equivalentJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Success)
    }

    @Test
    fun testSameRevisionChangedRestaurantConfigIsConflict() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)

        // Change restaurant name but keep same revision
        val changedJson = validJson.replace("\"restaurantName\": \"Bistro\"", "\"restaurantName\": \"Bistro Modified\"")
        val secondResult = importService.parseAndValidate(changedJson) as MenuPackageImportResult.Success
        val status = importService.importMenu(secondResult)

        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("Conflict"))
    }

    @Test
    fun testTerminalIdRemainsStable() = runBlocking {
        val firstResult = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", firstResult)
        val initialId = terminalRepository.getConfiguration()?.terminalId

        val newJson = validJson.replace("\"publicationRevision\": 10", "\"publicationRevision\": 11")
        val secondResult = importService.parseAndValidate(newJson) as MenuPackageImportResult.Success
        importService.importMenu(secondResult)

        val afterId = terminalRepository.getConfiguration()?.terminalId
        assertEquals(initialId, afterId)
    }
    @Test
    fun testSwitchMenuAcceptsLowerAndEqualRevision() = runBlocking {
        val first = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", first)
        val terminal = terminalRepository.getConfiguration()
        val lower = first.copy(menu = first.menu.copy(menuId = "menu-2", publicationRevision = 1))
        assertTrue(importService.importMenu(lower) is MenuImportStatus.Success)
        assertEquals(lower.menu.menuId, menuRepository.getPublishedMenu()?.menuId)
        assertEquals(1, menuRepository.getPublishedMenu()?.publicationRevision)
        val equal = lower.copy(menu = lower.menu.copy(menuId = "menu-3"))
        assertTrue(importService.importMenu(equal) is MenuImportStatus.Success)
        assertEquals("menu-3", menuRepository.getPublishedMenu()?.menuId)
        assertEquals(terminal, terminalRepository.getConfiguration())
    }

    @Test
    fun testSwitchMenuBlockedWithOpenOrderThenPreservesClosedSale() = runBlocking {
        val first = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", first)
        val terminal = requireNotNull(terminalRepository.getConfiguration())
        val sale = com.venkoi.terminal.domain.model.Sale(
            saleId = com.venkoi.terminal.core.SaleId("menu-switch-order"),
            terminalId = terminal.terminalId,
            openedAtUtc = java.time.Instant.EPOCH,
            updatedAtUtc = java.time.Instant.EPOCH,
            tableLabel = null,
            status = com.venkoi.terminal.domain.model.SaleStatus.OPEN,
            currencyCodeSnapshot = "CUP", currencyScaleSnapshot = 2,
            restaurantIdSnapshot = "rest-1", menuIdSnapshot = "menu-1",
            publicationRevisionSnapshot = 10
        )
        saleDao.insertSale(com.venkoi.terminal.data.local.database.SaleEntity.fromDomain(sale))
        val incoming = first.copy(menu = first.menu.copy(menuId = "menu-2", publicationRevision = 1))
        val beforeMenu = menuRepository.getPublishedMenu()
        val beforeItems = menuRepository.observeMenuItems().first()
        val status = importService.importMenu(incoming)
        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("open orders"))
        assertEquals(beforeMenu, menuRepository.getPublishedMenu())
        assertEquals(beforeItems, menuRepository.observeMenuItems().first())
        assertEquals(sale, saleDao.getSaleSync(sale.saleId)?.toDomain())
        val closed = sale.copy(status = com.venkoi.terminal.domain.model.SaleStatus.COMPLETED,
            revision = 1, completedAtUtc = java.time.Instant.EPOCH)
        saleDao.insertSale(com.venkoi.terminal.data.local.database.SaleEntity.fromDomain(closed))
        assertTrue(importService.importMenu(incoming) is MenuImportStatus.Success)
        assertEquals(closed, saleDao.getSaleSync(sale.saleId)?.toDomain())
        assertEquals(terminal, terminalRepository.getConfiguration())
    }

    @Test
    fun testConcurrentOrderCreationAndMenuSwitchKeepMatchingSnapshots() = runBlocking {
        val first = importService.parseAndValidate(validJson) as MenuPackageImportResult.Success
        importService.provisionTerminal("T1", first)
        repeat(10) { iteration ->
            saleDao.clearSaleLines()
            saleDao.clearSales()
            assertTrue(importService.importMenu(first) is MenuImportStatus.Success)
            val incoming = first.copy(menu = first.menu.copy(menuId = "menu-race-$iteration", publicationRevision = 1))
            val start = CompletableDeferred<Unit>()
            val order = async(Dispatchers.Default) { start.await(); saleRepository.createSale(null) }
            val update = async(Dispatchers.Default) { start.await(); importService.importMenu(incoming) }
            start.complete(Unit)
            val orderId = order.await()
            val status = update.await()
            val stored = requireNotNull(saleDao.getSaleSync(orderId))
            val installed = requireNotNull(menuRepository.getPublishedMenu())
            assertEquals(installed.menuId, stored.menuIdSnapshot)
            assertEquals(installed.publicationRevision, stored.publicationRevisionSnapshot)
            if (status is MenuImportStatus.Success) {
                assertEquals(incoming.menu.menuId, installed.menuId)
            } else {
                assertTrue((status as MenuImportStatus.Failure).message.contains("open orders"))
                assertEquals(first.menu.menuId, installed.menuId)
            }
        }
    }

}

