package com.venkoi.licenseadmin.issuer

import com.venkoi.licenseadmin.contract.ContractValidation
import com.venkoi.licenseadmin.crypto.AuthorityKeyPair
import com.venkoi.licenseadmin.crypto.AuthorityKeys
import com.venkoi.terminal.licensing.ActivationRequestV1
import com.venkoi.terminal.licensing.LICENSE_PRODUCT_CODE
import com.venkoi.terminal.licensing.LicensePayloadV1
import com.venkoi.terminal.licensing.SignedLicenseV1
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class IssueSpec(
    val plan: String,
    val sequence: Long,
    val expires: Instant,
    val grace: Instant,
    val allowExpiredForTesting: Boolean = false
)

class LicenseIssuerService(private val clock: Clock = Clock.systemUTC()) {
    fun issue(request: ActivationRequestV1, spec: IssueSpec, keys: AuthorityKeyPair): SignedLicenseV1 {
        ContractValidation.request(request)
        require(spec.plan.isNotBlank()) { "Plan must be nonblank" }
        require(spec.sequence > 0) { "Sequence must be positive" }
        val now = clock.instant()
        require(spec.allowExpiredForTesting || spec.expires > now) { "Expiration is in the past" }
        val payload = LicensePayloadV1(
            productCode = LICENSE_PRODUCT_CODE,
            licenseId = UUID.randomUUID().toString(),
            licenseSequence = spec.sequence,
            restaurantId = request.restaurantId,
            terminalId = request.terminalId,
            deviceKeyId = request.deviceKeyId,
            planCode = spec.plan,
            issuedAtUtc = now.toString(),
            expiresAtUtc = spec.expires.toString(),
            graceUntilUtc = spec.grace.toString()
        )
        ContractValidation.payload(payload)
        return AuthorityKeys.sign(payload, keys.privateKey).also {
            require(AuthorityKeys.verify(it, keys.publicKey)) { "Issued license self-verification failed" }
        }
    }

    fun renew(
        existing: SignedLicenseV1,
        request: ActivationRequestV1?,
        spec: IssueSpec,
        keys: AuthorityKeyPair
    ): SignedLicenseV1 {
        ContractValidation.payload(existing.payload)
        require(AuthorityKeys.verify(existing, keys.publicKey)) {
            "Existing license signature is invalid for this authority"
        }
        request?.let {
            ContractValidation.request(it)
            require(it.restaurantId == existing.payload.restaurantId &&
                it.terminalId == existing.payload.terminalId &&
                it.deviceKeyId == existing.payload.deviceKeyId) {
                "Activation identity does not match existing license"
            }
        }
        require(spec.sequence == existing.payload.licenseSequence + 1) {
            "Renewal sequence must be exactly previous + 1"
        }
        val source = ActivationRequestV1(
            restaurantId = existing.payload.restaurantId,
            terminalId = existing.payload.terminalId,
            deviceKeyId = existing.payload.deviceKeyId,
            generatedAtDeviceUtc = clock.instant().toString(),
            requestId = request?.requestId ?: "renewal:${existing.payload.licenseId}"
        )
        return issue(source, spec, keys)
    }
}
