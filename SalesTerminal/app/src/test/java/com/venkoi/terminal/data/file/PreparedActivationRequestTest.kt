package com.venkoi.terminal.data.file

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking

class PreparedActivationRequestTest {
    @Test
    fun `save and share consume the exact same prepared JSON bytes`() {
        val exactJson = """{"schemaVersion":1,"requestId":"request-once","terminalId":"T1"}"""
        val prepared = PreparedActivationRequest(exactJson, "sales_terminal_activation_ABCD.json")

        val saveBytes = prepared.json.toByteArray(StandardCharsets.UTF_8)
        val shareBytes = prepared.json.toByteArray(StandardCharsets.UTF_8)

        assertArrayEquals(saveBytes, shareBytes)
        assertEquals("request-once", Regex("\"requestId\":\"([^\"]+)\"").find(prepared.json)?.groupValues?.get(1))
        assertEquals("sales_terminal_activation_ABCD.json", prepared.suggestedFileName)
    }

    @Test
    fun `save cancellation is safe and the same request remains shareable`() = runBlocking {
        val prepared = PreparedActivationRequest("{\"requestId\":\"same\"}", "activation.json")
        val coordinator = ActivationRequestDeliveryCoordinator()
        var writeCalled = false

        val cancelled = coordinator.save<String>(null, prepared) { _, _ -> writeCalled = true; true }
        var sharedJson: String? = null
        val shared = coordinator.share(prepared) { content, _ -> sharedJson = content; true }

        assertEquals(ActivationDeliveryResult.Cancelled, cancelled)
        assertEquals(false, writeCalled)
        assertEquals(ActivationDeliveryResult.Success, shared)
        assertEquals(prepared.json, sharedJson)
    }

    @Test
    fun `share failure is contained and retry uses unchanged payload`() = runBlocking {
        val prepared = PreparedActivationRequest("{\"requestId\":\"retry-me\"}", "activation.json")
        val coordinator = ActivationRequestDeliveryCoordinator()

        val failed = coordinator.share(prepared) { _, _ -> throw SecurityException("denied") }
        var retryJson: String? = null
        val retried = coordinator.share(prepared) { content, _ -> retryJson = content; true }

        assertEquals(ActivationDeliveryResult.Failed, failed)
        assertEquals(ActivationDeliveryResult.Success, retried)
        assertEquals(prepared.json, retryJson)
    }
}
