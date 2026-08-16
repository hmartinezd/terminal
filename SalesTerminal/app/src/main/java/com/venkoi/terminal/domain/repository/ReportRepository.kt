package com.venkoi.terminal.domain.repository

import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReportRepository {
    fun observeDailyMoneyReport(businessDate: LocalDate): Flow<DailyMoneyReport>
    fun observeProductReport(businessDate: LocalDate): Flow<ProductReport>
}
