package com.venkoi.terminal.domain.service

import com.venkoi.terminal.domain.model.SalesExportGroup
import java.time.LocalDate

/** Transient, non-persisted cursor for repeatable one-group-at-a-time day exports. */
class DayExportSession(
    val businessDate: LocalDate,
    val groups: List<SalesExportGroup>
) {
    var nextGroupIndex: Int = 0
        private set

    val currentGroup: SalesExportGroup?
        get() = groups.getOrNull(nextGroupIndex)

    val isComplete: Boolean
        get() = currentGroup == null

    fun advanceAfterSuccessfulHandoff() {
        check(!isComplete) { "Cannot advance a completed day export session" }
        nextGroupIndex++
    }
}
