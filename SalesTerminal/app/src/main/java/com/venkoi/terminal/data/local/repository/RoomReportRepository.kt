package com.venkoi.terminal.data.local.repository

import com.venkoi.terminal.data.local.database.ReportDao
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.domain.repository.ReportRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.BuildDailyMoneyReport
import com.venkoi.terminal.domain.service.BuildProductReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

class RoomReportRepository @Inject constructor(
    private val reportDao: ReportDao,
    private val terminalConfigurationRepository: TerminalConfigurationRepository,
    private val dailyMoneyReportBuilder: BuildDailyMoneyReport,
    private val productReportBuilder: BuildProductReport
) : ReportRepository {

    override fun observeDailyMoneyReport(businessDate: LocalDate): Flow<DailyMoneyReport> {
        return observeSource(businessDate).map { entities ->
            val domainSales = entities.map { entity ->
                SaleWithLines(
                    sale = entity.sale.toDomain(),
                    lines = entity.lines.map { it.toDomain() }
                )
            }
            dailyMoneyReportBuilder.build(businessDate, domainSales)
        }
    }

    override fun observeProductReport(businessDate: LocalDate): Flow<ProductReport> {
        return observeSource(businessDate).map { entities ->
            val domainSales = entities.map { entity ->
                SaleWithLines(
                    sale = entity.sale.toDomain(),
                    lines = entity.lines.map { it.toDomain() }
                )
            }
            productReportBuilder.build(businessDate, domainSales)
        }
    }

    private fun observeSource(businessDate: LocalDate) =
        terminalConfigurationRepository.observeConfiguration().flatMapLatest { terminal ->
            terminal?.let { reportDao.observeSalesWithLinesForDate(it.terminalId, businessDate) }
                ?: flowOf(emptyList())
        }
}
