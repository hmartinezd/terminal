package com.venkoi.terminal.licensing

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant

const val LICENSE_PRODUCT_CODE = "SALES_TERMINAL"

@Serializable
data class ActivationRequestV1(val schemaVersion: Int = 1, val productCode: String = LICENSE_PRODUCT_CODE,
    val restaurantId: String, val terminalId: String, val deviceKeyId: String,
    val generatedAtDeviceUtc: String, val requestId: String)

@Serializable
data class LicensePayloadV1(val schemaVersion: Int = 1, val productCode: String,
    val licenseId: String, val licenseSequence: Long, val restaurantId: String,
    val terminalId: String, val deviceKeyId: String, val planCode: String,
    val issuedAtUtc: String, val expiresAtUtc: String, val graceUntilUtc: String) {
    fun issuedAt(): Instant = Instant.parse(issuedAtUtc)
    fun expiresAt(): Instant = Instant.parse(expiresAtUtc)
    fun graceUntil(): Instant = Instant.parse(graceUntilUtc)
}

@Serializable
data class SignedLicenseV1(val payload: LicensePayloadV1, val signatureBase64Url: String)

object CanonicalLicenseEncoder {
    fun encode(payload: LicensePayloadV1): ByteArray {
        val values = listOf(payload.schemaVersion.toString(), payload.productCode, payload.licenseId,
            payload.licenseSequence.toString(), payload.restaurantId, payload.terminalId,
            payload.deviceKeyId, payload.planCode, payload.issuedAtUtc, payload.expiresAtUtc,
            payload.graceUntilUtc)
        return ByteArrayOutputStream().also { output -> DataOutputStream(output).use { data ->
            values.forEach { value -> value.toByteArray(Charsets.UTF_8).also { data.writeInt(it.size); data.write(it) } }
        } }.toByteArray()
    }
}
