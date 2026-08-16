package com.venkoi.terminal.licensing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class TrustedTimeStoreTest {
    private class MemoryPersistence : SecurityStatePersistence {
        var storedPayload: String? = null
        var storedMac: String? = null
        override fun payload() = storedPayload
        override fun mac() = storedMac
        override fun write(payload: String, mac: String) { storedPayload = payload; storedMac = mac }
    }

    private class FakeAuthenticator : SecurityStateAuthenticator {
        var exists = false
        override fun keyExists() = exists
        override fun createKey() { exists = true }
        override fun mac(payload: String): ByteArray {
            check(exists)
            return MessageDigest.getInstance("SHA-256")
                .digest(("test-key\u0000" + payload).toByteArray(StandardCharsets.UTF_8))
        }
    }

    private class MutableTime(var wall: Instant, var elapsed: Long) : WallTimeSource, ElapsedRealtimeSource {
        override fun now() = wall
        override fun nowMillis() = elapsed
    }

    private fun store(time: MutableTime, persistence: MemoryPersistence = MemoryPersistence(), auth: FakeAuthenticator = FakeAuthenticator()) =
        Triple(TrustedTimeStore(persistence, auth, time, time), persistence, auth)

    @Test fun `normal trusted time progression`() {
        val time = MutableTime(Instant.parse("2026-01-01T10:00:00Z"), 1_000)
        val (store) = store(time)
        assertEquals(time.wall, store.observe().now)
        time.wall = Instant.parse("2026-01-01T10:30:00Z"); time.elapsed += 1_800_000
        val result = store.observe()
        assertEquals(time.wall, result.now)
        assertNull(result.error)
    }

    @Test fun `persisted wall rollback is detected and floor never moves backward`() {
        val time = MutableTime(Instant.parse("2026-01-01T12:00:00Z"), 1_000)
        val (store) = store(time)
        store.observe()
        time.wall = Instant.parse("2026-01-01T10:00:00Z")
        val result = store.observe()
        assertEquals(LicenseState.CLOCK_ROLLBACK_DETECTED, result.error)
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), result.now)
    }

    @Test fun `monotonic session estimate detects wall rollback`() {
        val time = MutableTime(Instant.parse("2026-01-01T12:00:00Z"), 1_000)
        val (store) = store(time)
        store.observe()
        time.wall = Instant.parse("2026-01-01T11:00:00Z"); time.elapsed = 3_601_000
        assertEquals(LicenseState.CLOCK_ROLLBACK_DETECTED, store.observe().error)
    }

    @Test fun `whole security state is authenticated`() {
        val time = MutableTime(Instant.parse("2026-01-01T10:00:00Z"), 1_000)
        val (store, persistence) = store(time)
        store.observe(); store.acceptLicense(time.wall, 5)
        assertEquals(5, store.securityState()!!.highestAcceptedLicenseSequence)
        val originalMac = persistence.storedMac
        persistence.storedPayload = persistence.storedPayload!!.replace("\n5", "\n6")
        assertEquals(LicenseState.LOCAL_SECURITY_STATE_INVALID, store.securityStateError())
        persistence.storedPayload = "1\n2027-01-01T10:00:00Z\n5"; persistence.storedMac = originalMac
        assertEquals(LicenseState.LOCAL_SECURITY_STATE_INVALID, store.securityStateError())
    }

    @Test fun `deleting state while key remains is invalid not fresh`() {
        val time = MutableTime(Instant.parse("2026-01-01T10:00:00Z"), 1_000)
        val (store, persistence, auth) = store(time)
        store.observe()
        persistence.storedPayload = null; persistence.storedMac = null
        assertEquals(true, auth.exists)
        assertEquals(LicenseState.LOCAL_SECURITY_STATE_INVALID, store.observe().error)
    }

    @Test fun `first initialization creates key intentionally`() {
        val time = MutableTime(Instant.parse("2026-01-01T10:00:00Z"), 1_000)
        val (store, _, auth) = store(time)
        assertEquals(false, auth.exists)
        assertNull(store.observe().error)
        assertEquals(true, auth.exists)
    }

    @Test fun `live selling guard does not use cached valid snapshot`() {
        runBlocking {
            var now = Instant.parse("2026-01-01T09:00:00Z")
            val payload = LicensePayloadV1(productCode = LICENSE_PRODUCT_CODE, licenseId = "L", licenseSequence = 1,
                restaurantId = "R", terminalId = "T", deviceKeyId = "D", planCode = "P",
                issuedAtUtc = "2025-12-01T08:00:00Z", expiresAtUtc = "2026-01-10T10:00:00Z",
                graceUntilUtc = "2026-01-10T11:00:00Z")
            val license = SignedLicenseV1(payload, "sig")
            val evaluate = { EvaluateLicense.evaluate(license, true, "R", "T", "D", now) }
            val cached = evaluate()
            assertEquals(LicenseState.VALID, cached.state)
            CurrentLicenseAuthorizer.requireAuthorized(evaluate)
            now = Instant.parse("2026-01-10T10:30:00Z")
            CurrentLicenseAuthorizer.requireAuthorized(evaluate)
            now = Instant.parse("2026-01-10T11:00:00Z")
            assertEquals(LicenseState.VALID, cached.state)
            assertThrows(SellingNotAuthorizedException::class.java) {
                runBlocking { CurrentLicenseAuthorizer.requireAuthorized(evaluate) }
            }
        }
    }
}
