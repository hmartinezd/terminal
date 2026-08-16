package com.venkoi.terminal.licensing

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
