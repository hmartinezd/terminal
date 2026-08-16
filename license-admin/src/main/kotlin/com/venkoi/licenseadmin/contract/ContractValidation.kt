package com.venkoi.licenseadmin.contract

import com.venkoi.terminal.licensing.ActivationRequestV1
import com.venkoi.terminal.licensing.LICENSE_PRODUCT_CODE
import com.venkoi.terminal.licensing.LicensePayloadV1
import java.time.Instant

object ContractValidation {
    fun request(request: ActivationRequestV1): ActivationRequestV1 {
        require(request.schemaVersion == 1) { "Unsupported activation schemaVersion" }
        require(request.productCode == LICENSE_PRODUCT_CODE) { "Wrong activation productCode" }
        require(request.restaurantId.isNotBlank()) { "restaurantId must be nonblank" }
        require(request.terminalId.isNotBlank()) { "terminalId must be nonblank" }
        require(request.deviceKeyId.isNotBlank()) { "deviceKeyId must be nonblank" }
        require(request.requestId.isNotBlank()) { "requestId must be nonblank" }
        Instant.parse(request.generatedAtDeviceUtc)
        return request
    }

    fun payload(payload: LicensePayloadV1): LicensePayloadV1 {
        require(payload.schemaVersion == 1) { "Unsupported license schemaVersion" }
        require(payload.productCode == LICENSE_PRODUCT_CODE) { "Wrong license productCode" }
        require(payload.licenseId.isNotBlank() && payload.restaurantId.isNotBlank() &&
            payload.terminalId.isNotBlank() && payload.deviceKeyId.isNotBlank() &&
            payload.planCode.isNotBlank()) { "License fields must be nonblank" }
        require(payload.licenseSequence > 0) { "Sequence must be positive" }
        val issued = payload.issuedAt()
        val expires = payload.expiresAt()
        val grace = payload.graceUntil()
        require(issued < expires) { "Expiration must be after issuance" }
        require(grace >= expires) { "Grace must not precede expiration" }
        return payload
    }
}
