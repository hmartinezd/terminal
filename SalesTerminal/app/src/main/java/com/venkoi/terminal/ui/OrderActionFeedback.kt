package com.venkoi.terminal.ui

import androidx.annotation.StringRes
import com.venkoi.terminal.R
import com.venkoi.terminal.licensing.LicenseState
import com.venkoi.terminal.licensing.SellingAuthorizationResult
import com.venkoi.terminal.licensing.SellingNotAuthorizedException
import kotlinx.coroutines.CancellationException

sealed interface OrderActionFeedback {
    data class SellingNotAuthorized(
        val reason: SellingAuthorizationResult
    ) : OrderActionFeedback

    data object OperationFailed : OrderActionFeedback
}

@StringRes
internal fun sellingDenialMessage(reason: SellingAuthorizationResult): Int = when (reason) {
    SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK -> R.string.device_time_correction_required
    SellingAuthorizationResult.DENIED_EXPIRED -> R.string.selling_disabled
    else -> R.string.license_security_verification_failed
}

@StringRes
internal fun restrictedBannerMessage(state: LicenseState): Int = when (state) {
    LicenseState.CLOCK_ROLLBACK_DETECTED -> R.string.device_time_correction_required
    LicenseState.EXPIRED -> R.string.selling_disabled
    else -> R.string.license_security_verification_failed
}

internal sealed interface SellingActionResult<out T> {
    data class Success<T>(val value: T) : SellingActionResult<T>
    data class SellingDenied(val reason: SellingAuthorizationResult) : SellingActionResult<Nothing>
    data class Failure(val cause: Exception) : SellingActionResult<Nothing>
}

internal suspend fun <T> runSellingAction(
    action: suspend () -> T
): SellingActionResult<T> = try {
    SellingActionResult.Success(action())
} catch (exception: SellingNotAuthorizedException) {
    SellingActionResult.SellingDenied(exception.result)
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    SellingActionResult.Failure(exception)
}
