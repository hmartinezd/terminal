package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.data.file.DocumentWriteResult
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.PreparedSalesExport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExportSalesCoordinatorTest {
    private val coordinator = ExportSalesCoordinator()
    private val prepared = PreparedSalesExport(
        json = "{\"sales\":[]}", batchId = "batch", exportedAtUtc = Instant.EPOCH,
        revisions = listOf(ExportedSaleRevision(SaleId("sale"), 1)), suggestedFileName = "sales.json"
    )

    @Test fun `cancel does not write or mark`() = runBlocking {
        var writes = 0
        var marks = 0
        val result = coordinator.execute<String>(null, prepared, { _, _ -> writes++; DocumentWriteResult.Success }, { marks++ })
        assertEquals(ExportCoordinationResult.Cancelled, result)
        assertEquals(0, writes)
        assertEquals(0, marks)
    }

    @Test fun `write failure never marks`() = runBlocking {
        var marks = 0
        val result = coordinator.execute("destination", prepared,
            { _, _ -> DocumentWriteResult.Failure("disk full") }, { marks++ })
        assertTrue(result is ExportCoordinationResult.WriteFailed)
        assertEquals(0, marks)
    }

    @Test fun `success marks the exact prepared export`() = runBlocking {
        var marked: PreparedSalesExport? = null
        val result = coordinator.execute("destination", prepared,
            { _, json -> assertEquals(prepared.json, json); DocumentWriteResult.Success }, { marked = it })
        assertEquals(ExportCoordinationResult.Success, result)
        assertEquals(prepared, marked)
    }

    @Test fun `bookkeeping failure is reported after successful write`() = runBlocking {
        var wrote = false
        val result = coordinator.execute("destination", prepared,
            { _, _ -> wrote = true; DocumentWriteResult.Success }, { throw IllegalStateException("room") })
        assertTrue(wrote)
        assertTrue(result is ExportCoordinationResult.BookkeepingFailed)
    }

    @Test fun `share uses exact prepared payload and marks only after handoff`() = runBlocking {
        var handedOff: PreparedSalesExport? = null
        var marked: PreparedSalesExport? = null
        val result = coordinator.executeShare(prepared, { handedOff = it; true }, { marked = it })
        assertEquals(ExportCoordinationResult.Success, result)
        assertEquals(prepared, handedOff)
        assertEquals(prepared, marked)
    }

    @Test fun `failed share handoff never marks revisions`() = runBlocking {
        var marks = 0
        val result = coordinator.executeShare(prepared, { false }, { marks++ })
        assertTrue(result is ExportCoordinationResult.HandoffFailed)
        assertEquals(0, marks)
    }

    @Test fun `share exception never marks revisions`() = runBlocking {
        var marks = 0
        val result = coordinator.executeShare(prepared, { throw IllegalStateException("chooser") }, { marks++ })
        assertTrue(result is ExportCoordinationResult.HandoffFailed)
        assertEquals(0, marks)
    }
}
