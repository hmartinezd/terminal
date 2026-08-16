package com.venkoi.terminal.domain.service

import com.venkoi.terminal.data.file.DocumentWriteResult
import com.venkoi.terminal.domain.model.PreparedSalesExport
import javax.inject.Inject

sealed interface ExportCoordinationResult {
    data object Cancelled : ExportCoordinationResult
    data object Success : ExportCoordinationResult
    data class WriteFailed(val failure: DocumentWriteResult.Failure) : ExportCoordinationResult
    data class BookkeepingFailed(val cause: Exception) : ExportCoordinationResult
}

/** Enforces the duplicate-safe write-first boundary independently of Android UI. */
class ExportSalesCoordinator @Inject constructor() {
    suspend fun <Destination> execute(
        destination: Destination?,
        prepared: PreparedSalesExport,
        write: suspend (Destination, String) -> DocumentWriteResult,
        markExactRevisions: suspend (PreparedSalesExport) -> Unit
    ): ExportCoordinationResult {
        destination ?: return ExportCoordinationResult.Cancelled
        return when (val result = write(destination, prepared.json)) {
            is DocumentWriteResult.Failure -> ExportCoordinationResult.WriteFailed(result)
            DocumentWriteResult.Success -> try {
                markExactRevisions(prepared)
                ExportCoordinationResult.Success
            } catch (error: Exception) {
                ExportCoordinationResult.BookkeepingFailed(error)
            }
        }
    }
}
