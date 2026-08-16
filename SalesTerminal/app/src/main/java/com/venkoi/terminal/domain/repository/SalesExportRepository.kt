package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.SaleExportSummary
import com.venkoi.terminal.domain.model.SaleWithLines
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface SalesExportRepository {
    fun observeSummary(terminalId: TerminalId): Flow<SaleExportSummary>
    suspend fun getPendingChanges(terminalId: TerminalId): List<SaleWithLines>
    suspend fun getSalesForDay(terminalId: TerminalId, businessDate: LocalDate): List<SaleWithLines>
    suspend fun markExported(revisions: List<ExportedSaleRevision>, exportedAtUtc: Instant, batchId: String)
}
