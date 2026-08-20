package com.venkoi.terminal.licensing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.model.TerminalConfiguration
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import java.util.Currency
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicenseManagerRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { encodeDefaults = true }
    private val authority: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val time = MutableTime(Instant.parse("2030-01-01T00:00:00Z"), 1_000)
    private val persistence = MemoryPersistence()
    private val authenticator = FakeAuthenticator()
    private val trustedTime = TrustedTimeStore(persistence, authenticator, time, time)
    private val identityProvider = DeviceIdentityProvider()

    @Before fun clearInstalledLicense() {
        context.getSharedPreferences("installed_license_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() = clearInstalledLicense()

    @Test fun normalHigherSequenceRecoveryCompletesThroughLicenseManager() = runBlocking {
        initializeFutureFloor()
        install(signed(sequence = 1))
        val candidate = signed(sequence = 2)
        time.wall = CORRECTED_NOW

        val manager = manager()
        assertEquals(LicenseState.CLOCK_ROLLBACK_DETECTED, manager.evaluateCurrentLicense().state)
        assertEquals(LicenseImportResult.Accepted, manager.import(json.encodeToString(candidate)))
        assertEquals(LicenseState.VALID, manager.evaluateCurrentLicense().state)
        assertEquals(2, trustedTime.securityState()!!.highestAcceptedLicenseSequence)
        assertEquals(CORRECTED_NOW, trustedTime.securityState()!!.lastTrustedUtc)
        manager.requireSelling()
    }

    @Test fun exactInstalledCandidateResumesInterruptedRecoveryThenReturnsToDuplicate() = runBlocking {
        initializeFutureFloor()
        val candidate = signed(sequence = 2)
        install(candidate) // Simulates envelope commit followed by process interruption.
        time.wall = CORRECTED_NOW

        val manager = manager()
        assertEquals(LicenseState.CLOCK_ROLLBACK_DETECTED, manager.evaluateCurrentLicense().state)
        assertEquals(LicenseImportResult.Accepted, manager.import(json.encodeToString(candidate)))
        assertEquals(2, trustedTime.securityState()!!.highestAcceptedLicenseSequence)
        assertEquals(CORRECTED_NOW, trustedTime.securityState()!!.lastTrustedUtc)
        assertEquals(LicenseState.VALID, manager.evaluateCurrentLicense().state)

        val completedState = trustedTime.securityState()
        assertEquals(LicenseImportResult.Duplicate, manager.import(json.encodeToString(candidate)))
        assertEquals(completedState, trustedTime.securityState())
    }

    @Test fun conflictingSameSequenceNeverReanchors() = runBlocking {
        initializeFutureFloor()
        install(signed(sequence = 2))
        val conflict = signed(sequence = 2, plan = "OTHER")
        time.wall = CORRECTED_NOW
        val oldState = trustedTime.securityState()

        assertEquals(LicenseImportResult.SequenceConflict, manager().import(json.encodeToString(conflict)))
        assertEquals(oldState, trustedTime.securityState())
    }

    private fun initializeFutureFloor() {
        assertNull(trustedTime.observe().error)
        trustedTime.acceptLicense(time.wall, 1)
    }

    private fun manager() = LicenseManager(
        context = context,
        terminalRepository = FakeTerminalRepository(),
        menuRepository = FakeMenuRepository(),
        identityProvider = identityProvider,
        trustedTime = trustedTime,
        verifier = LicenseSignatureVerifier { authority.public },
        runtimePolicy = object : RuntimeLicensePolicy {
            override val developerAuthorization = false
            override fun appIntegrityValid() = true
        },
        clock = time,
        idGenerator = object : IdGenerator { override fun nextId() = "request" },
        json = json
    )

    private fun signed(sequence: Long, plan: String = "PILOT"): SignedLicenseV1 {
        val payload = LicensePayloadV1(
            productCode = LICENSE_PRODUCT_CODE,
            licenseId = "license-$sequence-$plan",
            licenseSequence = sequence,
            restaurantId = RESTAURANT_ID,
            terminalId = TERMINAL_ID,
            deviceKeyId = identityProvider.get().deviceKeyId,
            planCode = plan,
            issuedAtUtc = "2026-08-19T23:59:00Z",
            expiresAtUtc = "2026-09-20T00:00:00Z",
            graceUntilUtc = "2026-09-27T00:00:00Z"
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(authority.private)
            update(CanonicalLicenseEncoder.encode(payload))
            sign()
        }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signature))
    }

    private fun install(license: SignedLicenseV1) {
        assertTrue(context.getSharedPreferences("installed_license_v1", Context.MODE_PRIVATE).edit()
            .putString("envelope", json.encodeToString(license)).putLong("imported_at", 1L).commit())
    }

    private class MutableTime(var wall: Instant, var elapsed: Long) : Clock, WallTimeSource, ElapsedRealtimeSource {
        override fun now() = wall
        override fun nowMillis() = elapsed
    }

    private class MemoryPersistence : SecurityStatePersistence {
        var payload: String? = null
        var mac: String? = null
        override fun payload() = payload
        override fun mac() = mac
        override fun write(payload: String, mac: String) { this.payload = payload; this.mac = mac }
    }

    private class FakeAuthenticator : SecurityStateAuthenticator {
        var exists = false
        override fun keyExists() = exists
        override fun createKey() { exists = true }
        override fun mac(payload: String): ByteArray {
            check(exists)
            return MessageDigest.getInstance("SHA-256")
                .digest(("manager-test\u0000" + payload).toByteArray(StandardCharsets.UTF_8))
        }
    }

    private class FakeTerminalRepository : TerminalConfigurationRepository {
        private val value = TerminalConfiguration(TerminalId(TERMINAL_ID), RestaurantId(RESTAURANT_ID), "Test", CORRECTED_NOW)
        override suspend fun getConfiguration() = value
        override fun observeConfiguration() = flowOf(value)
        override suspend fun saveConfiguration(configuration: TerminalConfiguration) = Unit
        override suspend fun clearConfiguration() = Unit
        override suspend fun provisionTerminal(configuration: TerminalConfiguration, restaurant: RestaurantConfiguration,
            menu: PublishedMenu, categories: List<MenuCategory>, items: List<MenuItem>) = Unit
    }

    private class FakeMenuRepository : MenuRepository {
        private val restaurant = RestaurantConfiguration(RESTAURANT_ID, "Test", ZoneId.of("UTC"),
            Currency.getInstance("USD"), LocalTime.of(4, 0))
        override fun observeRestaurantConfiguration() = flowOf(restaurant)
        override fun observePublishedMenu() = flowOf<PublishedMenu?>(null)
        override fun observeCategories() = flowOf(emptyList<MenuCategory>())
        override fun observeMenuItems() = flowOf(emptyList<MenuItem>())
        override fun observeActiveMenuItems() = flowOf(emptyList<MenuItem>())
        override suspend fun installMenu(restaurant: RestaurantConfiguration, menu: PublishedMenu,
            categories: List<MenuCategory>, items: List<MenuItem>) = Unit
        override suspend fun getPublishedMenu(): PublishedMenu? = null
        override suspend fun getRestaurantConfiguration() = restaurant
        override suspend fun getMenuItem(id: String): MenuItem? = null
    }

    companion object {
        private const val RESTAURANT_ID = "restaurant-recovery"
        private const val TERMINAL_ID = "terminal-recovery"
        private val CORRECTED_NOW: Instant = Instant.parse("2026-08-20T00:00:00Z")
    }
}
