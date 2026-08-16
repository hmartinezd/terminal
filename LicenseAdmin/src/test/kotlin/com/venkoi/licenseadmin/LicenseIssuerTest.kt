package com.venkoi.licenseadmin

import com.venkoi.licenseadmin.audit.AuditStore
import com.venkoi.licenseadmin.contract.ContractValidation
import com.venkoi.licenseadmin.crypto.AuthorityKeyPair
import com.venkoi.licenseadmin.crypto.AuthorityKeys
import com.venkoi.licenseadmin.issuer.IssueSpec
import com.venkoi.licenseadmin.issuer.LicenseIssuerService
import com.venkoi.terminal.licensing.ActivationRequestV1
import com.venkoi.terminal.licensing.CanonicalLicenseEncoder
import com.venkoi.terminal.licensing.LicensePayloadV1
import com.venkoi.terminal.licensing.SignedLicenseV1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class LicenseIssuerTest {
    private val now = Instant.parse("2026-08-16T12:00:00Z")
    private val service = LicenseIssuerService(Clock.fixed(now, ZoneOffset.UTC))
    private fun keys() = AuthorityKeys.generate().let { AuthorityKeyPair(it.private, it.public) }
    private fun request(device: String = "device-A") = ActivationRequestV1(restaurantId = "restaurant-1",
        terminalId = "terminal-1", deviceKeyId = device, generatedAtDeviceUtc = now.toString(), requestId = "request-1")
    private fun spec(sequence: Long) = IssueSpec("PILOT", sequence, now.plusSeconds(86400), now.plusSeconds(172800))

    @Test fun `Sales Terminal activation fixture is accepted`() {
        val text = requireNotNull(javaClass.getResource("/compatibility/android_activation_request_v1.json")).readText()
        val parsed = ContractValidation.request(Json.decodeFromString<ActivationRequestV1>(text))
        assertEquals("pilot-restaurant", parsed.restaurantId)
        assertEquals("front-counter-1", parsed.terminalId)
        assertEquals("android-device-fixture-key-id", parsed.deviceKeyId)
    }

    @Test fun `issue validates and self verifies`() {
        val keys = keys()
        val license = service.issue(request(), spec(1), keys)
        assertTrue(AuthorityKeys.verify(license, keys.publicKey))
        assertEquals(request().deviceKeyId, license.payload.deviceKeyId)
        assertFalse(AuthorityKeys.verify(license.copy(payload = license.payload.copy(planCode = "FORGED")), keys.publicKey))
        assertFalse(AuthorityKeys.verify(license, keys().publicKey))
    }

    @Test fun `renewal increments exactly and preserves binding`() {
        val keys = keys()
        val first = service.issue(request(), spec(1), keys)
        val renewed = service.renew(first, request(), IssueSpec("STANDARD", 2,
            now.plusSeconds(200000), now.plusSeconds(300000)), keys)
        assertEquals(2, renewed.payload.licenseSequence)
        assertEquals(first.payload.restaurantId, renewed.payload.restaurantId)
        assertEquals(first.payload.terminalId, renewed.payload.terminalId)
        assertEquals(first.payload.deviceKeyId, renewed.payload.deviceKeyId)
        assertNotEquals(first.payload.licenseId, renewed.payload.licenseId)
        assertThrows(IllegalArgumentException::class.java) { service.renew(first, request(), spec(1), keys) }
        assertThrows(IllegalArgumentException::class.java) { service.renew(first, request("replacement"), spec(2), keys) }
        assertThrows(IllegalArgumentException::class.java) { service.renew(first, request(), spec(2), keys()) }
    }

    @Test fun `request sequence and time validation reject malformed input`() {
        assertThrows(IllegalArgumentException::class.java) { ContractValidation.request(request().copy(productCode = "OTHER")) }
        assertThrows(IllegalArgumentException::class.java) { ContractValidation.request(request().copy(schemaVersion = 2)) }
        assertThrows(RuntimeException::class.java) { ContractValidation.request(request().copy(generatedAtDeviceUtc = "invalid")) }
        assertThrows(IllegalArgumentException::class.java) { service.issue(request(), spec(0), keys()) }
        assertThrows(IllegalArgumentException::class.java) { service.issue(request(), IssueSpec("PILOT", 1, now, now), keys()) }
    }

    @Test fun `PEM keypair challenge rejects mismatch`() {
        val first = AuthorityKeys.generate()
        val second = AuthorityKeys.generate()
        val directory = Files.createTempDirectory("license-admin-key-test")
        val privatePath = directory.resolve("authority-private.pem")
        val publicPath = directory.resolve("authority-public.pem")
        Files.writeString(privatePath, AuthorityKeys.pem("PRIVATE KEY", first.private.encoded))
        Files.writeString(publicPath, AuthorityKeys.pem("PUBLIC KEY", first.public.encoded))
        assertNotNull(AuthorityKeys.loadAndValidate(privatePath, publicPath))
        Files.writeString(publicPath, AuthorityKeys.pem("PUBLIC KEY", second.public.encoded))
        assertThrows(IllegalArgumentException::class.java) { AuthorityKeys.loadAndValidate(privatePath, publicPath) }
    }

    @Test fun `audit and license output contain no private material`() {
        val license = service.issue(request(), spec(1), keys())
        val output = Json.encodeToString(license).toByteArray()
        val audit = Files.createTempFile("license-audit", ".jsonl")
        AuditStore(audit).append(license, request().requestId, output)
        val combined = output.decodeToString() + Files.readString(audit)
        assertFalse(combined.contains("PRIVATE KEY"))
        assertFalse(combined.contains("privateKey"))
        assertEquals(1, AuditStore(audit).records().size)
    }

    @Test fun `canonical V1 golden vector is stable`() {
        val payload = LicensePayloadV1(productCode = "SALES_TERMINAL", licenseId = "compat-license-001",
            licenseSequence = 7, restaurantId = "restaurant-fixture", terminalId = "terminal-fixture",
            deviceKeyId = "device-fixture", planCode = "PILOT", issuedAtUtc = "2026-08-01T00:00:00Z",
            expiresAtUtc = "2026-09-01T00:00:00Z", graceUntilUtc = "2026-09-08T00:00:00Z")
        val hash = MessageDigest.getInstance("SHA-256").digest(CanonicalLicenseEncoder.encode(payload))
            .joinToString("") { "%02x".format(it) }
        assertEquals("5df7ad4f74bc09f282f2fc8dd7927a975c65c7abdbf8420047f3080496413e31", hash)
    }

    @Test fun `fixed development signature fixture verifies`() {
        val licenseText = requireNotNull(javaClass.getResource(
            "/compatibility/license_admin_signed_license_v1.json")).readText()
        val publicPem = requireNotNull(javaClass.getResource(
            "/compatibility/development_authority_public.pem")).readText()
        val keyBytes = Base64.getMimeDecoder().decode(publicPem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), ""))
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        assertTrue(AuthorityKeys.verify(Json.decodeFromString(licenseText), publicKey))
    }

    @Test fun `development issue serialization and renewal are Sales Terminal compatible`() {
        val activationJson = Json.encodeToString(request())
        val parsed = ContractValidation.request(Json.decodeFromString<ActivationRequestV1>(activationJson))
        val authority = keys()
        val wrongAuthority = keys()

        val issued = service.issue(parsed, spec(1), authority)
        val wire = Json.encodeToString(issued)
        val imported = Json.decodeFromString<SignedLicenseV1>(wire)
        assertTrue(terminalCompatibleVerify(imported, authority.publicKey))
        assertEquals(parsed.restaurantId, imported.payload.restaurantId)
        assertEquals(parsed.terminalId, imported.payload.terminalId)
        assertEquals(parsed.deviceKeyId, imported.payload.deviceKeyId)
        assertFalse(terminalCompatibleVerify(imported.copy(payload = imported.payload.copy(
            expiresAtUtc = "2031-01-01T00:00:00Z")), authority.publicKey))
        assertFalse(terminalCompatibleVerify(imported, wrongAuthority.publicKey))

        val renewed = service.renew(imported, parsed, IssueSpec("PILOT", 2,
            now.plusSeconds(200000), now.plusSeconds(300000)), authority)
        assertEquals(2, renewed.payload.licenseSequence)
        assertTrue(terminalCompatibleVerify(Json.decodeFromString(Json.encodeToString(renewed)), authority.publicKey))
    }

    private fun terminalCompatibleVerify(license: SignedLicenseV1, publicKey: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(CanonicalLicenseEncoder.encode(license.payload))
            verify(Base64.getUrlDecoder().decode(license.signatureBase64Url))
        }
    }.getOrDefault(false)
}
