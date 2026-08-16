package com.venkoi.terminal.licensing

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant

const val LICENSE_PRODUCT_CODE = "SALES_TERMINAL"

@Serializable
data class LicensePayloadV1(
    val schemaVersion: Int = 1,
    val productCode: String,
    val licenseId: String,
    val licenseSequence: Long,
    val restaurantId: String,
    val terminalId: String,
    val deviceKeyId: String,
    val planCode: String,
    val issuedAtUtc: String,
    val expiresAtUtc: String,
    val graceUntilUtc: String
) {
    fun issuedAt(): Instant = Instant.parse(issuedAtUtc)
    fun expiresAt(): Instant = Instant.parse(expiresAtUtc)
    fun graceUntil(): Instant = Instant.parse(graceUntilUtc)
}

@Serializable
data class SignedLicenseV1(
    val payload: LicensePayloadV1,
    val signatureBase64Url: String
)

@Serializable
data class ActivationRequestV1(
    val schemaVersion: Int = 1,
    val productCode: String = LICENSE_PRODUCT_CODE,
    val restaurantId: String,
    val terminalId: String,
    val deviceKeyId: String,
    val generatedAtDeviceUtc: String,
    val requestId: String
)

/**
 * Canonical V1 wire bytes: each field in the order below is encoded as a signed
 * 32-bit big-endian byte length followed by UTF-8 bytes. Integers are rendered
 * as base-10 ASCII. This format, not the JSON envelope, is authority-signed.
 */
object CanonicalLicenseEncoder {
    fun encode(payload: LicensePayloadV1): ByteArray {
        val values = listOf(
            payload.schemaVersion.toString(), payload.productCode, payload.licenseId,
            payload.licenseSequence.toString(), payload.restaurantId, payload.terminalId,
            payload.deviceKeyId, payload.planCode, payload.issuedAtUtc,
            payload.expiresAtUtc, payload.graceUntilUtc
        )
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            values.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }
}

enum class LicenseState {
    NOT_ACTIVATED, VALID, EXPIRING_SOON, GRACE_PERIOD, EXPIRED,
    INVALID_SIGNATURE, WRONG_PRODUCT, RESTAURANT_MISMATCH, TERMINAL_MISMATCH,
    DEVICE_MISMATCH, CLOCK_ROLLBACK_DETECTED, LOCAL_SECURITY_STATE_INVALID,
    APP_INTEGRITY_INVALID
}

data class LicenseSnapshot(
    val state: LicenseState,
    val payload: LicensePayloadV1? = null,
    val detail: String? = null
)

enum class SellingAuthorizationResult {
    AUTHORIZED, AUTHORIZED_GRACE, DENIED_NOT_ACTIVATED, DENIED_EXPIRED,
    DENIED_CLOCK_ROLLBACK, DENIED_INVALID_LICENSE, DENIED_DEVICE_MISMATCH,
    DENIED_APP_INTEGRITY
}

class SellingNotAuthorizedException(val result: SellingAuthorizationResult) :
    IllegalStateException("Selling is not authorized: $result")

object LicensePolicy {
    fun sellingAuthorization(state: LicenseState): SellingAuthorizationResult = when (state) {
        LicenseState.VALID, LicenseState.EXPIRING_SOON -> SellingAuthorizationResult.AUTHORIZED
        LicenseState.GRACE_PERIOD -> SellingAuthorizationResult.AUTHORIZED_GRACE
        LicenseState.NOT_ACTIVATED -> SellingAuthorizationResult.DENIED_NOT_ACTIVATED
        LicenseState.EXPIRED -> SellingAuthorizationResult.DENIED_EXPIRED
        LicenseState.CLOCK_ROLLBACK_DETECTED, LicenseState.LOCAL_SECURITY_STATE_INVALID ->
            SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK
        LicenseState.DEVICE_MISMATCH -> SellingAuthorizationResult.DENIED_DEVICE_MISMATCH
        LicenseState.APP_INTEGRITY_INVALID -> SellingAuthorizationResult.DENIED_APP_INTEGRITY
        else -> SellingAuthorizationResult.DENIED_INVALID_LICENSE
    }
}
