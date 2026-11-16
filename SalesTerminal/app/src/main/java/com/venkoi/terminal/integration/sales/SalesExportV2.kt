package com.venkoi.terminal.integration.sales

import kotlinx.serialization.Serializable

const val SALES_EXPORT_V2_FORMAT_VERSION = 2

@Serializable
data class SalesExportV2(
    val format: String,
    val formatVersion: Int,
    val exportId: String,
    val terminalId: String,
    val restaurantId: String,
    val generatedAt: String,
    val businessDate: String,
    val menuId: String,
    val publicationRevision: Int,
    val currency: String,
    val transactions: List<SalesExportTransactionV2>
) {
    init {
        require(format == SALES_EXPORT_FORMAT) { "Unsupported sales export format: $format" }
        require(formatVersion == SALES_EXPORT_V2_FORMAT_VERSION) {
            "Unsupported SalesExport version: $formatVersion"
        }
    }
}

@Serializable
data class SalesExportTransactionV2(
    val transactionId: String,
    val openedAt: String,
    val closedAt: String,
    val status: SalesExportStatus,
    val lines: List<SalesExportLineV2>
)

@Serializable
data class SalesExportLineV2(
    val saleLineId: String,
    val sellableItemId: String,
    val displayNameSnapshot: String,
    val quantity: String,
    val unitPrice: String,
    val gross: String,
    val discount: String,
    val net: String,
    val commercialRevision: Int,
    val consumptionRevision: Int,
    /** Stable source pricing classification, not proof of a settled payment. */
    val pricingMode: String
) {
    init {
        require(pricingMode.matches(Regex("[A-Z][A-Z0-9_]{0,63}"))) {
            "Invalid pricing mode code"
        }
    }
}
