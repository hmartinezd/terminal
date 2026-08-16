package com.venkoi.terminal.licensing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

fun interface LicenseAuthorityPublicKeyProvider { fun publicKey(): PublicKey? }

class EncodedLicenseAuthorityPublicKeyProvider(private val encoded: String) : LicenseAuthorityPublicKeyProvider {
    override fun publicKey(): PublicKey? = runCatching {
        val normalized = encoded.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), "")
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(normalized)))
    }.getOrNull()
}

class LicenseSignatureVerifier(private val provider: LicenseAuthorityPublicKeyProvider) {
    fun verify(license: SignedLicenseV1): Boolean = runCatching {
        val key = provider.publicKey() ?: return false
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(key)
        signature.update(CanonicalLicenseEncoder.encode(license.payload))
        signature.verify(Base64.getUrlDecoder().decode(license.signatureBase64Url))
    }.getOrDefault(false)
}

object EvaluateLicense {
    private val warning = java.time.Duration.ofDays(7)

    fun evaluate(
        license: SignedLicenseV1?, signatureValid: Boolean,
        restaurantId: String, terminalId: String, deviceKeyId: String,
        trustedNow: java.time.Instant, securityState: LicenseState? = null,
        appIntegrityValid: Boolean = true
    ): LicenseSnapshot {
        if (securityState != null) return LicenseSnapshot(securityState, license?.payload)
        if (!appIntegrityValid) return LicenseSnapshot(LicenseState.APP_INTEGRITY_INVALID, license?.payload)
        if (license == null) return LicenseSnapshot(LicenseState.NOT_ACTIVATED)
        val p = license.payload
        if (!signatureValid) return LicenseSnapshot(LicenseState.INVALID_SIGNATURE, p)
        if (p.schemaVersion != 1 || p.productCode != LICENSE_PRODUCT_CODE) return LicenseSnapshot(LicenseState.WRONG_PRODUCT, p)
        if (p.restaurantId != restaurantId) return LicenseSnapshot(LicenseState.RESTAURANT_MISMATCH, p)
        if (p.terminalId != terminalId) return LicenseSnapshot(LicenseState.TERMINAL_MISMATCH, p)
        if (p.deviceKeyId != deviceKeyId) return LicenseSnapshot(LicenseState.DEVICE_MISMATCH, p)
        val issued = runCatching { p.issuedAt() }.getOrNull() ?: return LicenseSnapshot(LicenseState.INVALID_SIGNATURE, p)
        val expires = runCatching { p.expiresAt() }.getOrNull() ?: return LicenseSnapshot(LicenseState.INVALID_SIGNATURE, p)
        val grace = runCatching { p.graceUntil() }.getOrNull() ?: return LicenseSnapshot(LicenseState.INVALID_SIGNATURE, p)
        if (!issued.isBefore(expires) || grace.isBefore(expires) || p.licenseSequence < 1) return LicenseSnapshot(LicenseState.INVALID_SIGNATURE, p)
        return when {
            !trustedNow.isBefore(grace) -> LicenseSnapshot(LicenseState.EXPIRED, p)
            !trustedNow.isBefore(expires) -> LicenseSnapshot(LicenseState.GRACE_PERIOD, p)
            !trustedNow.isBefore(expires.minus(warning)) -> LicenseSnapshot(LicenseState.EXPIRING_SOON, p)
            else -> LicenseSnapshot(LicenseState.VALID, p)
        }
    }
}
