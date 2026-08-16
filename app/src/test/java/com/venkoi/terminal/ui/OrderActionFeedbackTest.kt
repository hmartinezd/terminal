package com.venkoi.terminal.ui

import com.venkoi.terminal.licensing.SellingAuthorizationResult
import com.venkoi.terminal.licensing.SellingNotAuthorizedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderActionFeedbackTest {
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
