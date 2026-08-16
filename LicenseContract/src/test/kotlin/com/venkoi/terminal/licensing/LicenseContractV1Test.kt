package com.venkoi.terminal.licensing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LicenseContractV1Test {
    @Test fun `canonical V1 golden vector is stable`() {
        val payload = LicensePayloadV1(productCode = "SALES_TERMINAL", licenseId = "compat-license-001",
            licenseSequence = 7, restaurantId = "restaurant-fixture", terminalId = "terminal-fixture",
            deviceKeyId = "device-fixture", planCode = "PILOT", issuedAtUtc = "2026-08-01T00:00:00Z",
            expiresAtUtc = "2026-09-01T00:00:00Z", graceUntilUtc = "2026-09-08T00:00:00Z")
        val hash = MessageDigest.getInstance("SHA-256").digest(CanonicalLicenseEncoder.encode(payload))
            .joinToString("") { "%02x".format(it) }
        assertEquals("5df7ad4f74bc09f282f2fc8dd7927a975c65c7abdbf8420047f3080496413e31", hash)
    }

    @Test fun `V1 models serialize and fixtures remain packaged`() {
        val request = ActivationRequestV1(restaurantId = "r", terminalId = "t", deviceKeyId = "d",
            generatedAtDeviceUtc = "2026-08-01T00:00:00Z", requestId = "q")
        val json = Json { encodeDefaults = true }
        assertEquals(request, json.decodeFromString<ActivationRequestV1>(json.encodeToString(request)))
        assertTrue(requireNotNull(javaClass.getResource("/compatibility/development_authority_public.pem"))
            .readText().contains("BEGIN PUBLIC KEY"))
        assertTrue(requireNotNull(javaClass.getResource("/compatibility/license_admin_signed_license_v1.json"))
            .readText().contains("signatureBase64Url"))
    }
}
