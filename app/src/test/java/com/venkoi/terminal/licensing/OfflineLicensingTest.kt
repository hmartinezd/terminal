package com.venkoi.terminal.licensing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64

class OfflineLicensingTest {
    private val json = Json { encodeDefaults = true }
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    private fun payload(sequence: Long = 5) = LicensePayloadV1(
        productCode = LICENSE_PRODUCT_CODE, licenseId = "L1", licenseSequence = sequence,
        restaurantId = "R1", terminalId = "T1", deviceKeyId = "DEVICE_A", planCode = "PRO",
        issuedAtUtc = "2026-08-01T00:00:00Z", expiresAtUtc = "2026-09-01T00:00:00Z",
        graceUntilUtc = "2026-09-10T00:00:00Z"
    )

    @Test fun `canonical bytes are deterministic and every mutation changes them`() {
        val original = CanonicalLicenseEncoder.encode(payload())
        assertArrayEquals(original, CanonicalLicenseEncoder.encode(payload()))
        assertFalse(original.contentEquals(CanonicalLicenseEncoder.encode(payload().copy(planCode = "STANDARD"))))
        assertFalse(original.contentEquals(CanonicalLicenseEncoder.encode(payload().copy(deviceKeyId = "DEVICE_B"))))
        assertFalse(original.contentEquals(CanonicalLicenseEncoder.encode(payload().copy(expiresAtUtc = "2026-10-01T00:00:00Z"))))
    }

    @Test fun `ECDSA verifies canonical payload and rejects tampering and wrong authority`() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        val authority = generator.generateKeyPair()
        val other = generator.generateKeyPair()
        val signed = sign(payload(), authority.private)
        val verifier = LicenseSignatureVerifier { authority.public }
        assertTrue(verifier.verify(signed))
        assertFalse(verifier.verify(signed.copy(payload = signed.payload.copy(expiresAtUtc = "2027-01-01T00:00:00Z"))))
        assertFalse(LicenseSignatureVerifier { other.public }.verify(signed))
    }

    @Test fun `JSON whitespace and property formatting do not affect verification`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val signed = sign(payload(), pair.private)
        val compact = json.encodeToString(signed)
        val reordered = """{
          "signatureBase64Url": ${json.encodeToString(signed.signatureBase64Url)},
          "payload": ${json.encodeToString(signed.payload)}
        }"""
        val verifier = LicenseSignatureVerifier { pair.public }
        assertTrue(verifier.verify(json.decodeFromString(compact)))
        assertTrue(verifier.verify(json.decodeFromString(reordered)))
    }

    @Test fun `activation and license contracts round trip language-neutrally`() {
        val request = ActivationRequestV1(restaurantId = "R1", terminalId = "T1", deviceKeyId = "D1",
            generatedAtDeviceUtc = now.toString(), requestId = "Q1")
        assertEquals(request, json.decodeFromString<ActivationRequestV1>(json.encodeToString(request)))
        val license = SignedLicenseV1(payload(), "signature")
        assertEquals(license, json.decodeFromString<SignedLicenseV1>(json.encodeToString(license)))
        assertEquals("SALES_TERMINAL", request.productCode)
    }

    @Test fun `bindings product and exact validity boundaries are enforced`() {
        fun state(at: String, p: LicensePayloadV1 = payload(), restaurant: String = "R1", terminal: String = "T1", device: String = "DEVICE_A") =
            EvaluateLicense.evaluate(SignedLicenseV1(p, "x"), true, restaurant, terminal, device, Instant.parse(at)).state
        assertEquals(LicenseState.EXPIRING_SOON, state("2026-08-31T23:59:59Z"))
        assertEquals(LicenseState.GRACE_PERIOD, state("2026-09-01T00:00:00Z"))
        assertEquals(LicenseState.EXPIRED, state("2026-09-10T00:00:00Z"))
        assertEquals(LicenseState.RESTAURANT_MISMATCH, state(now.toString(), restaurant = "R2"))
        assertEquals(LicenseState.TERMINAL_MISMATCH, state(now.toString(), terminal = "T2"))
        assertEquals(LicenseState.DEVICE_MISMATCH, state(now.toString(), device = "DEVICE_B"))
        assertEquals(LicenseState.WRONG_PRODUCT, state(now.toString(), p = payload().copy(productCode = "OTHER")))
        assertEquals(SellingAuthorizationResult.DENIED_DEVICE_MISMATCH, LicensePolicy.sellingAuthorization(LicenseState.DEVICE_MISMATCH))
        assertEquals(SellingAuthorizationResult.AUTHORIZED_GRACE, LicensePolicy.sellingAuthorization(LicenseState.GRACE_PERIOD))
    }

    private fun sign(payload: LicensePayloadV1, key: java.security.PrivateKey): SignedLicenseV1 {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(key); update(CanonicalLicenseEncoder.encode(payload)); sign()
        }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }
}
