package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SalesExportGroupKey
import java.time.LocalDate

/**
 * Transient, non-persisted cursor for repeatable one-group-at-a-time day exports.
 * It deliberately retains keys only; sale snapshots are always loaded afresh.
 */
class DayExportSession(
    val businessDate: LocalDate,
    orderedGroupKeys: List<SalesExportGroupKey>
) {
    var orderedGroupKeys: List<SalesExportGroupKey> = orderedGroupKeys
        private set

    private val handedOffGroupKeys = linkedSetOf<SalesExportGroupKey>()

    var nextGroupIndex: Int = 0
        private set

    val currentGroupKey: SalesExportGroupKey?
        get() = orderedGroupKeys.getOrNull(nextGroupIndex)

    val isComplete: Boolean
        get() = currentGroupKey == null

    /** Reconciles the cursor to the current database-derived key order. */
    fun reconcile(currentGroupKeys: List<SalesExportGroupKey>) {
        orderedGroupKeys = currentGroupKeys.distinct()
        nextGroupIndex = orderedGroupKeys.indexOfFirst { it !in handedOffGroupKeys }
            .let { if (it < 0) orderedGroupKeys.size else it }
    }

    fun markCurrentGroupSuccessfullyHandedOff() {
        val key = currentGroupKey ?: error("Cannot advance a completed day export session")
        handedOffGroupKeys += key
        reconcile(orderedGroupKeys)
    }
}
