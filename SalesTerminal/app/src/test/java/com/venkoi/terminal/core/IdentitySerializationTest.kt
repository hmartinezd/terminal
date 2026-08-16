package com.venkoi.terminal.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentitySerializationTest {
    @Test
    fun `identity round-trip serialization`() {
        val id = TerminalId("test-uuid")
        val json = Json.encodeToString(id)
        assertEquals("\"test-uuid\"", json)
        
        val decoded = Json.decodeFromString<TerminalId>(json)
        assertEquals(id, decoded)
    }
}
