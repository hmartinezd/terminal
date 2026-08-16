package com.venkoi.terminal.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.model.SaleWithLines
import com.venkoi.terminal.domain.service.BuildDailyMoneyReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReportDaoTest {
    private lateinit var database: AppDatabase
    private val terminalA = TerminalId("terminal-a")
    private val terminalB = TerminalId("terminal-b")
    private val day = LocalDate.parse("2026-08-10")

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun filtersByTerminalBusinessDateAndLifecycleAndLoadsLines() = runBlocking {
        insert("completed", terminalA, day, SaleStatus.COMPLETED)
        insert("voided", terminalA, day, SaleStatus.VOIDED)
        insert("open", terminalA, day, SaleStatus.OPEN)
        insert("discarded", terminalA, day, SaleStatus.DISCARDED)
        insert("other-day", terminalA, day.plusDays(1), SaleStatus.COMPLETED)
        insert("other-terminal", terminalB, day, SaleStatus.COMPLETED)

        val result = database.reportDao().observeSalesWithLinesForDate(terminalA, day).first()
        assertEquals(setOf("completed", "voided"), result.map { it.sale.saleId.value }.toSet())
        assertEquals(setOf("line-completed", "line-voided"), result.flatMap { it.lines }.map { it.lineId.value }.toSet())
    }

    @Test fun lateVoidRemainsOnPersistedBusinessDateAndReactiveReportChanges() = runBlocking {
        insert("late-void", terminalA, day, SaleStatus.COMPLETED, "20.00")
        val source = database.reportDao().observeSalesWithLinesForDate(terminalA, day)
        val builder = BuildDailyMoneyReport()
        fun report(entities: List<SaleWithLinesEntity>) = builder.build(day, entities.map {
            SaleWithLines(it.sale.toDomain(), it.lines.map(SaleLineEntity::toDomain))
        }).currencySections.single()

        val before = report(source.first())
        assertEquals(1, before.validSaleCount)
        assertEquals("20.00", before.grandTotal.amount.toPlainString())
        assertEquals(0, before.voidedSaleCount)

        val later = Instant.parse("2026-08-12T12:00:00Z")
        assertEquals(1, database.saleDao().voidSaleGuarded(SaleId("late-void"), later, later))
        val after = report(source.first())
        assertEquals(0, after.validSaleCount)
        assertEquals(0, after.grandTotal.amount.compareTo(BigDecimal.ZERO))
        assertEquals(1, after.voidedSaleCount)
        assertEquals("20.00", after.voidedAmount.amount.toPlainString())
        assertEquals(SaleStatus.VOIDED, database.reportDao().observeSalesWithLinesForDate(terminalA, day).first().single().sale.status)
        assertEquals(emptyList<SaleWithLinesEntity>(), database.reportDao().observeSalesWithLinesForDate(terminalA, day.plusDays(2)).first())
    }

    private suspend fun insert(
        id: String,
        terminal: TerminalId,
        businessDate: LocalDate,
        status: SaleStatus,
        total: String = "1.00"
    ) {
        val instant = Instant.parse("2026-08-10T12:00:00Z")
        database.saleDao().insertSale(SaleEntity(
            SaleId(id), terminal, instant, instant, id, status,
            if (status == SaleStatus.OPEN) null else 1,
            if (status == SaleStatus.OPEN) null else instant,
            if (status == SaleStatus.VOIDED) instant.plusSeconds(1) else null,
            businessDate, "USD", 2
        ))
        database.saleDao().insertSaleLines(listOf(SaleLineEntity(
            LineId("line-$id"), SaleId(id), "item-$id", 1, 1, "Item $id", BigDecimal.ONE,
            Money(total), CashDiscountMode.NONE, BigDecimal.ZERO, PricingMode.CASH,
            false, BigDecimal.ZERO, Money.ZERO, Money(total), Money(total)
        )))
    }
}
