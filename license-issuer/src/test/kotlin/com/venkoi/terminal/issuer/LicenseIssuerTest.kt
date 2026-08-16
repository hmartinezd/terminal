package com.venkoi.terminal.issuer

import com.venkoi.terminal.licensing.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LicenseIssuerTest {
    private val now = Instant.parse("2026-08-16T12:00:00Z")
    private val service = LicenseIssuerService(Clock.fixed(now, ZoneOffset.UTC))
    private fun pair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun request(device: String = "device-A") = ActivationRequestV1(restaurantId = "restaurant-1",
        terminalId = "terminal-1", deviceKeyId = device, generatedAtDeviceUtc = now.toString(), requestId = "request-1")
    private fun spec(sequence: Long) = IssueSpec("PILOT", sequence, now.plusSeconds(86400), now.plusSeconds(172800))

    @Test fun `Android activation fixture parses without identity drift`() {
        val text = requireNotNull(javaClass.getResource("/android_activation_request_v1.json")).readText()
        val parsed = ContractValidation.request(JsonWire.json.decodeFromString<ActivationRequestV1>(text))
        assertEquals("pilot-restaurant", parsed.restaurantId)
        assertEquals("front-counter-1", parsed.terminalId)
        assertEquals("android-device-fixture-key-id", parsed.deviceKeyId)
    }

    @Test fun `issuer output verifies with Android canonical verifier semantics`() {
        val pair = pair(); val license = service.issue(request(), spec(1), pair.private)
        assertTrue(AuthorityKeys.verify(license, pair.public))
        assertEquals(request().deviceKeyId, license.payload.deviceKeyId)
        val secondSignature = AuthorityKeys.sign(license.payload, pair.private)
        assertNotEquals(license.signatureBase64Url, secondSignature.signatureBase64Url)
        assertTrue(AuthorityKeys.verify(secondSignature, pair.public))
    }

    @Test fun `tamper wrong key and device copy fail`() {
        val pair = pair(); val license = service.issue(request(), spec(1), pair.private)
        val tampered = license.copy(payload = license.payload.copy(expiresAtUtc = now.plusSeconds(999999).toString()))
        assertFalse(AuthorityKeys.verify(tampered, pair.public))
        assertFalse(AuthorityKeys.verify(license, pair().public))
        assertNotEquals(license.payload.deviceKeyId, request("device-B").deviceKeyId)
    }

    @Test fun `renewal increments and preserves binding`() {
        val pair = pair(); val first = service.issue(request(), spec(1), pair.private)
        val renewed = service.renew(first, request(), IssueSpec("STANDARD", 2, now.plusSeconds(200000), now.plusSeconds(300000)), pair.private)
        assertEquals(2, renewed.payload.licenseSequence)
        assertEquals(first.payload.restaurantId, renewed.payload.restaurantId)
        assertEquals(first.payload.terminalId, renewed.payload.terminalId)
        assertEquals(first.payload.deviceKeyId, renewed.payload.deviceKeyId)
        assertNotEquals(first.payload.licenseId, renewed.payload.licenseId)
        assertTrue(AuthorityKeys.verify(renewed, pair.public))
        assertThrows(IllegalArgumentException::class.java) { service.renew(first, request(), spec(1), pair.private) }
    }

    @Test fun `request and time validation reject malformed inputs`() {
        assertThrows(IllegalArgumentException::class.java) { ContractValidation.request(request().copy(productCode = "OTHER")) }
        assertThrows(IllegalArgumentException::class.java) { ContractValidation.request(request().copy(schemaVersion = 2)) }
        assertThrows(IllegalArgumentException::class.java) { service.issue(request(), spec(0), pair().private) }
        assertThrows(IllegalArgumentException::class.java) { service.issue(request(), IssueSpec("PILOT", 1, now, now), pair().private) }
    }
}
