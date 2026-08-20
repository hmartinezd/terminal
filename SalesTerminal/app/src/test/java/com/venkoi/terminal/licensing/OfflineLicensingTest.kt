package com.venkoi.terminal.licensing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.MessageDigest
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

    @Test fun `canonical V1 golden vector matches License Admin`() {
        val vector = LicensePayloadV1(productCode = "SALES_TERMINAL", licenseId = "compat-license-001",
            licenseSequence = 7, restaurantId = "restaurant-fixture", terminalId = "terminal-fixture",
            deviceKeyId = "device-fixture", planCode = "PILOT", issuedAtUtc = "2026-08-01T00:00:00Z",
            expiresAtUtc = "2026-09-01T00:00:00Z", graceUntilUtc = "2026-09-08T00:00:00Z")
        val hash = MessageDigest.getInstance("SHA-256").digest(CanonicalLicenseEncoder.encode(vector))
            .joinToString("") { "%02x".format(it) }
        assertEquals("5df7ad4f74bc09f282f2fc8dd7927a975c65c7abdbf8420047f3080496413e31", hash)
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

    @Test fun `License Admin fixed development fixture verifies in Sales Terminal`() {
        val licenseText = requireNotNull(javaClass.getResource(
            "/compatibility/license_admin_signed_license_v1.json")).readText()
        val publicPem = requireNotNull(javaClass.getResource(
            "/compatibility/development_authority_public.pem")).readText()
        val license = json.decodeFromString<SignedLicenseV1>(licenseText)
        assertTrue(LicenseSignatureVerifier(EncodedLicenseAuthorityPublicKeyProvider(publicPem)).verify(license))
        assertEquals("android-device-fixture-key-id", license.payload.deviceKeyId)
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

    @Test fun `same payload and sequence is duplicate across independent valid ECDSA signatures`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val first = sign(payload(), pair.private)
        var second = sign(payload(), pair.private)
        repeat(5) {
            if (second.signatureBase64Url == first.signatureBase64Url) second = sign(payload(), pair.private)
        }
        val verifier = LicenseSignatureVerifier { pair.public }
        assertTrue(verifier.verify(first)); assertTrue(verifier.verify(second))
        assertEquals(LicenseImportDecision.DUPLICATE, LicenseImportRules.compare(second, first, 5))
    }

    @Test fun `sequence rules use authenticated floor and verified payload`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val current = sign(payload(5), pair.private)
        assertEquals(LicenseImportDecision.ACCEPT, LicenseImportRules.compare(sign(payload(1), pair.private), null, 0))
        assertEquals(LicenseImportDecision.STALE, LicenseImportRules.compare(sign(payload(4), pair.private), current, 5))
        assertEquals(LicenseImportDecision.SEQUENCE_CONFLICT,
            LicenseImportRules.compare(sign(payload(5).copy(planCode = "OTHER"), pair.private), current, 5))
        assertEquals(LicenseImportDecision.ACCEPT, LicenseImportRules.compare(sign(payload(6), pair.private), current, 5))
        assertEquals(LicenseImportDecision.LOCAL_STATE_INVALID, LicenseImportRules.compare(sign(payload(5), pair.private), null, 5))
    }

    @Test fun `forged stored sequence cannot block valid newer recovery`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val verifier = LicenseSignatureVerifier { pair.public }
        val forged = sign(payload(5), pair.private).copy(payload = payload(999))
        assertFalse(verifier.verify(forged))
        val verifiedCurrent = forged.takeIf(verifier::verify)
        assertEquals(LicenseImportDecision.ACCEPT,
            LicenseImportRules.compare(sign(payload(6), pair.private), verifiedCurrent, 5))
        assertEquals(LicenseImportDecision.STALE,
            LicenseImportRules.compare(sign(payload(4), pair.private), verifiedCurrent, 5))
    }

    @Test fun `interrupted recovery continuation is exact clock-only and authenticated-floor gated`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val installed = sign(payload(2), pair.private)
        val samePayload = sign(payload(2), pair.private)

        assertTrue(LicenseImportRules.canResumeInterruptedRecovery(true, samePayload, installed, 1))
        assertFalse(LicenseImportRules.canResumeInterruptedRecovery(false, samePayload, installed, 1))
        assertFalse(LicenseImportRules.canResumeInterruptedRecovery(true, samePayload, installed, 2))
        assertFalse(LicenseImportRules.canResumeInterruptedRecovery(
            true, sign(payload(2).copy(planCode = "OTHER"), pair.private), installed, 1))
        assertFalse(LicenseImportRules.canResumeInterruptedRecovery(
            true, sign(payload(1), pair.private), installed, 0))
    }

    @Test fun `local security failure is denied generically while rollback stays clock specific`() {
        assertEquals(SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK,
            LicensePolicy.sellingAuthorization(LicenseState.CLOCK_ROLLBACK_DETECTED))
        assertEquals(SellingAuthorizationResult.DENIED_INVALID_LICENSE,
            LicensePolicy.sellingAuthorization(LicenseState.LOCAL_SECURITY_STATE_INVALID))
    }

    private fun sign(payload: LicensePayloadV1, key: java.security.PrivateKey): SignedLicenseV1 {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(key); update(CanonicalLicenseEncoder.encode(payload)); sign()
        }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }
}
