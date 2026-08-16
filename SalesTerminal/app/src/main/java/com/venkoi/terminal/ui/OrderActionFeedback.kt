package com.venkoi.terminal.ui

import com.venkoi.terminal.licensing.SellingAuthorizationResult
import com.venkoi.terminal.licensing.SellingNotAuthorizedException
import kotlinx.coroutines.CancellationException

sealed interface OrderActionFeedback {
    data class SellingNotAuthorized(
        val reason: SellingAuthorizationResult
    ) : OrderActionFeedback

    data object OperationFailed : OrderActionFeedback
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
