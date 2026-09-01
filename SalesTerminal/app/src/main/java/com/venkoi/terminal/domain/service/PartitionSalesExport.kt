package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.domain.model.SalesExportGroup
import com.venkoi.terminal.domain.model.SalesExportGroupKey
import com.venkoi.terminal.domain.model.SalesExportPartition
import java.time.Instant
import javax.inject.Inject

/** Partitions sales without ever putting two envelope provenance groups in one V1 document. */
class PartitionSalesExport @Inject constructor() {
    operator fun invoke(sales: List<SaleWithLines>): SalesExportPartition {
        val canonical = mutableMapOf<SalesExportGroupKey, MutableList<SaleWithLines>>()
        val legacy = mutableListOf<SaleWithLines>()

        sales.sortedWith(saleOrdering).forEach { saleWithLines ->
            val key = saleWithLines.canonicalExportGroupKey()
            if (key == null) legacy += saleWithLines
            else canonical.getOrPut(key) { mutableListOf() } += saleWithLines
        }

        val groups = canonical.map { (key, members) ->
            SalesExportGroup(key, members.sortedWith(saleOrdering))
        }.sortedWith(groupOrdering)

        return SalesExportPartition(groups, legacy.sortedWith(saleOrdering))
    }

    private val saleOrdering = compareBy<SaleWithLines>(
        { it.sale.businessDate },
        { it.sale.exportClosedAtForOrdering() },
        { it.sale.saleId.value }
    )

    private val groupOrdering = compareBy<SalesExportGroup>(
        { it.sales.first().sale.businessDate },
        { it.sales.first().sale.exportClosedAtForOrdering() },
        { it.key.restaurantId },
        { it.key.menuId },
        { it.key.publicationRevision },
        { it.key.currency }
    )
}

private fun SaleWithLines.canonicalExportGroupKey(): SalesExportGroupKey? {
    val sale = sale
    val restaurantId = sale.restaurantIdSnapshot?.takeIf { it.isNotBlank() } ?: return null
    val menuId = sale.menuIdSnapshot?.takeIf { it.isNotBlank() } ?: return null
    val publicationRevision = sale.publicationRevisionSnapshot?.takeIf { it > 0 } ?: return null
    val businessDate = sale.businessDate ?: return null
    val currency = sale.currencyCodeSnapshot.takeIf { it.isNotBlank() } ?: return null
    return SalesExportGroupKey(restaurantId, businessDate, menuId, publicationRevision, currency)
}

private fun com.venkoi.terminal.domain.model.Sale.exportClosedAtForOrdering(): Instant = when (status) {
    SaleStatus.COMPLETED -> completedAtUtc ?: updatedAtUtc
    SaleStatus.VOIDED -> voidedAtUtc ?: updatedAtUtc
    else -> updatedAtUtc
}
