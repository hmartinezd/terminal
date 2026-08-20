package com.venkoi.terminal.licensing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.data.local.database.AppDatabase
import com.venkoi.terminal.data.local.database.SaleDao
import com.venkoi.terminal.data.local.repository.RoomSaleRepository
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.repository.VoidResult
import com.venkoi.terminal.domain.service.CompleteSale
import com.venkoi.terminal.domain.service.MenuImportService
import com.venkoi.terminal.domain.service.MenuImportStatus
import com.venkoi.terminal.domain.service.VoidSale
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SellingAuthorizationRepositoryIntegrationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var setupRepository: SaleRepository
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var terminalRepository: TerminalConfigurationRepository
    @Inject lateinit var menuRepository: MenuRepository
    @Inject lateinit var saleDao: SaleDao
    @Inject lateinit var clock: Clock
    @Inject lateinit var idGenerator: IdGenerator
    @Inject lateinit var completeSale: CompleteSale
    @Inject lateinit var voidSale: VoidSale
    @Inject lateinit var menuImport: MenuImportService

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { encodeDefaults = true }
    private val authority = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private lateinit var configuredTerminalId: String

    @Before fun setUp() {
        hiltRule.inject()
        runBlocking {
            context.getSharedPreferences("installed_license_v1", Context.MODE_PRIVATE).edit().clear().commit()
            database.clearAllTables()
            val parsed = menuImport.parseAndValidate(MENU) as MenuPackageImportResult.Success
            assertEquals(MenuImportStatus.Success, menuImport.provisionTerminal("T1", parsed))
            configuredTerminalId = terminalRepository.getConfiguration()!!.terminalId.value
        }
    }

    @Test fun expiredDeniesSevenMutationsWithoutWritesButAllowsCleanupAndReads() = runBlocking {
        verifyRestrictedMatrix(expired = true, SellingAuthorizationResult.DENIED_EXPIRED)
    }

    @Test fun rollbackDeniesSevenMutationsWithoutWritesButAllowsCleanupAndReads() = runBlocking {
        verifyRestrictedMatrix(expired = false, SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK)
    }

    @Test fun realMutationUsesFreshAuthorizationAfterPreviouslyValidSnapshot() = runBlocking {
        val mutableTime = MutableTime(Instant.parse("2026-08-20T00:00:00Z"), 1_000)
        val manager = manager(mutableTime)
        install(signed(expires = "2026-08-20T00:01:00Z", grace = "2026-08-20T00:02:00Z"))
        assertEquals(SellingAuthorizationResult.AUTHORIZED,
            LicensePolicy.sellingAuthorization(manager.evaluateCurrentLicense().state))
        val guarded = guarded(manager)
        val before = guarded.observeOpenSales().first()
        mutableTime.wall = Instant.parse("2026-08-20T00:03:00Z")

        denied(SellingAuthorizationResult.DENIED_EXPIRED) { guarded.createSale("race") }
        assertEquals(before, guarded.observeOpenSales().first())
    }

    private suspend fun verifyRestrictedMatrix(expired: Boolean, expected: SellingAuthorizationResult) {
        val saleId = setupRepository.createSale("Original")
        setupRepository.addItem(saleId, "item-1")
        val lineId = setupRepository.observeSaleLines(saleId).first().single().lineId
        val historicalId = setupRepository.createSale("Historical")
        setupRepository.addItem(historicalId, "item-1")
        assertEquals(SaleCompletionResult.Success, setupRepository.completeSale(historicalId))

        val mutableTime = MutableTime(
            if (expired) Instant.parse("2026-08-20T00:00:00Z") else Instant.parse("2030-01-01T00:00:00Z"), 1_000)
        val manager = manager(mutableTime)
        if (expired) {
            install(signed(issued = "2026-07-01T00:00:00Z", expires = "2026-08-01T00:00:00Z", grace = "2026-08-10T00:00:00Z"))
        } else {
            assertNull(manager.evaluateCurrentLicense().payload)
            managerTrustedTime(manager).acceptLicense(mutableTime.wall, 1)
            install(signed())
            mutableTime.wall = Instant.parse("2026-08-20T00:00:00Z")
        }
        assertEquals(if (expired) LicenseState.EXPIRED else LicenseState.CLOCK_ROLLBACK_DETECTED,
            manager.evaluateCurrentLicense().state)
        val guarded = guarded(manager)
        val saleBefore = guarded.observeSale(saleId).first()!!
        val linesBefore = guarded.observeSaleLines(saleId).first()
        val openBefore = guarded.observeOpenSales().first()

        denied(expected) { guarded.createSale("Denied") }
        denied(expected) { guarded.updateSaleLabel(saleId, "Changed") }
        denied(expected) { guarded.addItem(saleId, "item-1") }
        denied(expected) { guarded.updateLineQuantity(saleId, lineId, BigDecimal("2")) }
        denied(expected) { guarded.changeLinePricingMode(saleId, lineId, PricingMode.CASH) }
        denied(expected) { guarded.removeLine(saleId, lineId) }
        denied(expected) { guarded.completeSale(saleId) }

        assertEquals(openBefore, guarded.observeOpenSales().first())
        assertEquals(saleBefore, guarded.observeSale(saleId).first())
        assertEquals(linesBefore, guarded.observeSaleLines(saleId).first())
        assertNotNull(guarded.observeHistorySales().first().find { it.saleId == historicalId })

        guarded.discardSale(saleId)
        assertEquals(SaleStatus.DISCARDED, guarded.observeSale(saleId).first()!!.status)
        assertEquals(VoidResult.Success, guarded.voidSale(historicalId))
        assertEquals(SaleStatus.VOIDED, guarded.observeSale(historicalId).first()!!.status)
    }

    private fun guarded(manager: LicenseManager) = RoomSaleRepository(
        saleDao, terminalRepository, menuRepository, clock, idGenerator, completeSale, voidSale, manager)

    private fun manager(time: MutableTime): LicenseManager {
        val persistence = MemoryPersistence()
        val trusted = TrustedTimeStore(persistence, FakeAuthenticator(), time, time)
        lastTrustedTime = trusted
        return LicenseManager(context, terminalRepository, menuRepository, DeviceIdentityProvider(), trusted,
            LicenseSignatureVerifier { authority.public }, object : RuntimeLicensePolicy {
                override val developerAuthorization = false
                override fun appIntegrityValid() = true
            }, time, idGenerator, json)
    }

    private var lastTrustedTime: TrustedTimeStore? = null
    private fun managerTrustedTime(@Suppress("UNUSED_PARAMETER") manager: LicenseManager) = lastTrustedTime!!

    private fun install(license: SignedLicenseV1) {
        check(context.getSharedPreferences("installed_license_v1", Context.MODE_PRIVATE).edit()
            .putString("envelope", json.encodeToString(license)).putLong("imported_at", 1).commit())
    }

    private fun signed(
        issued: String = "2026-08-19T23:59:00Z",
        expires: String = "2026-09-20T00:00:00Z",
        grace: String = "2026-09-27T00:00:00Z"
    ): SignedLicenseV1 {
        val payload = LicensePayloadV1(productCode = LICENSE_PRODUCT_CODE, licenseId = "test", licenseSequence = 1,
            restaurantId = "rest-1", terminalId = configuredTerminalId, deviceKeyId = DeviceIdentityProvider().get().deviceKeyId,
            planCode = "PILOT", issuedAtUtc = issued, expiresAtUtc = expires, graceUntilUtc = grace)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(authority.private); update(CanonicalLicenseEncoder.encode(payload)); sign()
        }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signature))
    }

    private fun denied(expected: SellingAuthorizationResult, action: suspend () -> Unit) {
        val exception = assertThrows(SellingNotAuthorizedException::class.java) { runBlocking { action() } }
        assertEquals(expected, exception.result)
    }

    private class MutableTime(var wall: Instant, private var elapsed: Long) : Clock, WallTimeSource, ElapsedRealtimeSource {
        override fun now() = wall
        override fun nowMillis() = elapsed
    }
    private class MemoryPersistence : SecurityStatePersistence {
        var payload: String? = null; var mac: String? = null
        override fun payload() = payload; override fun mac() = mac
        override fun write(payload: String, mac: String) { this.payload = payload; this.mac = mac }
    }
    private class FakeAuthenticator : SecurityStateAuthenticator {
        private var exists = false
        override fun keyExists() = exists
        override fun createKey() { exists = true }
        override fun mac(payload: String): ByteArray {
            check(exists)
            return MessageDigest.getInstance("SHA-256").digest(("repo-test\u0000" + payload).toByteArray(StandardCharsets.UTF_8))
        }
    }

    companion object {
        private val MENU = """
            {"schemaVersion":1,"restaurant":{"restaurantId":"rest-1","restaurantName":"Test","timezone":"UTC","currency":"USD","businessDayCutoff":"04:00"},
            "menu":{"menuId":"menu-1","publicationRevision":1,"publishedAtUtc":"2026-08-15T00:00:00Z","defaultCashDiscountPercent":"10"},
            "categories":[{"id":"cat-1","name":"Food","displayOrder":1}],
            "menuItems":[{"id":"item-1","categoryId":"cat-1","name":"Burger","active":true,"displayOrder":1,"regularPrice":"15.00","cashDiscountMode":"APPLY_DEFAULT","commercialRevision":1,"consumptionRevision":1}]}
        """.trimIndent()
    }
}
