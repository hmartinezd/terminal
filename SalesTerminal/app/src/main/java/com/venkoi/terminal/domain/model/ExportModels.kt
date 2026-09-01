package com.venkoi.terminal.domain.model

import com.venkoi.terminal.core.SaleId
import java.time.Instant
import java.time.LocalDate

data class ExportedSaleRevision(val saleId: SaleId, val revision: Int)

/** Internal-only key for the single envelope provenance shared by a V1 document. */
data class SalesExportGroupKey(
    val restaurantId: String,
    val businessDate: LocalDate,
    val menuId: String,
    val publicationRevision: Int,
    val currency: String
)

data class SalesExportGroup(
    val key: SalesExportGroupKey,
    val sales: List<SaleWithLines>
)

data class SalesExportPartition(
    val canonicalGroups: List<SalesExportGroup>,
    val legacyUnsupported: List<SaleWithLines>
)

data class PreparedSalesExport(
    val json: String,
    val exportId: String,
    val exportedAtUtc: Instant,
    val revisions: List<ExportedSaleRevision>,
    val suggestedFileName: String
)

data class SaleExportSummary(
    val pendingCount: Int = 0,
    val legacyUnsupportedCount: Int = 0,
    val lastSuccessfulExportAtUtc: Instant? = null
)
