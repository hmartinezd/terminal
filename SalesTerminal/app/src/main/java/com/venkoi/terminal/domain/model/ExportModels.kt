package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.SaleId
import java.time.Instant

data class ExportedSaleRevision(val saleId: SaleId, val revision: Int)

data class PreparedSalesExport(
    val json: String,
    val batchId: String,
    val exportedAtUtc: Instant,
    val revisions: List<ExportedSaleRevision>,
    val suggestedFileName: String
)

data class SaleExportSummary(
    val pendingCount: Int = 0,
    val lastSuccessfulExportAtUtc: Instant? = null
)
