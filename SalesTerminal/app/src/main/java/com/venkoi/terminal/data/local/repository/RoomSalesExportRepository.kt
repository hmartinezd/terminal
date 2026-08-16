package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.data.local.database.ExportDao
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.SaleExportSummary
import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.domain.repository.SalesExportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class RoomSalesExportRepository @Inject constructor(private val dao: ExportDao) : SalesExportRepository {
    override fun observeSummary(terminalId: TerminalId): Flow<SaleExportSummary> =
        dao.observeSummary(terminalId).map { SaleExportSummary(it.pendingCount, it.lastSuccessfulExportAtUtc) }

    override suspend fun getPendingChanges(terminalId: TerminalId) =
        dao.getPendingChanges(terminalId).map { it.toDomain() }

    override suspend fun getSalesForDay(terminalId: TerminalId, businessDate: LocalDate) =
        dao.getSalesForDay(terminalId, businessDate).map { it.toDomain() }

    override suspend fun markExported(revisions: List<ExportedSaleRevision>, exportedAtUtc: Instant, batchId: String) =
        dao.markExported(revisions, exportedAtUtc, batchId)

    private fun com.venkoi.terminal.data.local.database.SaleWithLinesEntity.toDomain() =
        SaleWithLines(sale.toDomain(), lines.sortedBy { it.lineId.value }.map { it.toDomain() })
}
