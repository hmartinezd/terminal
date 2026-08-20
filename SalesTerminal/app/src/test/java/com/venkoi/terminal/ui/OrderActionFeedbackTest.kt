package com.venkoi.terminal.ui

import com.venkoi.terminal.R
import com.venkoi.terminal.licensing.SellingAuthorizationResult
import com.venkoi.terminal.licensing.SellingNotAuthorizedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderActionFeedbackTest {

    @Test fun denialAndBannerMessagesDistinguishClockExpirationAndSecurity() {
        assertEquals(R.string.device_time_correction_required,
            sellingDenialMessage(SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK))
        assertEquals(R.string.selling_disabled,
            sellingDenialMessage(SellingAuthorizationResult.DENIED_EXPIRED))
        listOf(
            SellingAuthorizationResult.DENIED_INVALID_LICENSE,
            SellingAuthorizationResult.DENIED_DEVICE_MISMATCH,
            SellingAuthorizationResult.DENIED_APP_INTEGRITY,
            SellingAuthorizationResult.DENIED_NOT_ACTIVATED
        ).forEach { assertEquals(R.string.license_security_verification_failed, sellingDenialMessage(it)) }
        assertEquals(R.string.device_time_correction_required,
            restrictedBannerMessage(com.venkoi.terminal.licensing.LicenseState.CLOCK_ROLLBACK_DETECTED))
        assertEquals(R.string.selling_disabled,
            restrictedBannerMessage(com.venkoi.terminal.licensing.LicenseState.EXPIRED))
        assertEquals(R.string.license_security_verification_failed,
            restrictedBannerMessage(com.venkoi.terminal.licensing.LicenseState.LOCAL_SECURITY_STATE_INVALID))
    }
    @Test
    fun `authorization denial becomes controlled feedback and stops the action`() = runBlocking {
        var reachedPastDenial = false

        val result = runSellingAction {
            throw SellingNotAuthorizedException(SellingAuthorizationResult.DENIED_EXPIRED)
            @Suppress("UNREACHABLE_CODE")
            reachedPastDenial = true
        }

        assertEquals(
            SellingActionResult.SellingDenied(SellingAuthorizationResult.DENIED_EXPIRED),
            result
        )
        assertTrue(!reachedPastDenial)
    }

    @Test
    fun `successful selling action returns its value`() = runBlocking {
        assertEquals(SellingActionResult.Success("sale-id"), runSellingAction { "sale-id" })
    }

    @Test
    fun `unrelated failure is not mislabeled as license denial`() = runBlocking {
        val failure = IllegalStateException("storage failed")

        val result = runSellingAction { throw failure }

        assertTrue(result is SellingActionResult.Failure)
        assertSame(failure, (result as SellingActionResult.Failure).cause)
    }
}
