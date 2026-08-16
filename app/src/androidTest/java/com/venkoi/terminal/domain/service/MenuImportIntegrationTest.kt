package com.venkoi.terminal.domain.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
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
}

