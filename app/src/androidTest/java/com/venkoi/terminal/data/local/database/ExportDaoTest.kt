package com.venkoi.terminal.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.SaleStatus
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
class ExportDaoTest {
    private lateinit var database: AppDatabase
    private val terminal = TerminalId("terminal-a")
    private val day = LocalDate.parse("2026-08-10")
    private val firstExport = Instant.parse("2026-08-15T12:00:00Z")

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun closeDatabase() = database.close()

    @Test fun pendingIsLifecycleBasedAndDayExportIsAlwaysReExportable() = runBlocking {
        insert("completed", SaleStatus.COMPLETED, 1)
        insert("open", SaleStatus.OPEN, null)
        insert("discarded", SaleStatus.DISCARDED, null)
        assertEquals(listOf("completed"), database.exportDao().getPendingChanges(terminal).map { it.sale.saleId.value })

        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("completed"), 1)), firstExport, "batch-1")
        assertEquals(0, database.exportDao().observeSummary(terminal).first().pendingCount)
        assertEquals(listOf("completed"), database.exportDao().getSalesForDay(terminal, day).map { it.sale.saleId.value })

        val voidedAt = firstExport.plusSeconds(10)
        database.saleDao().voidSaleGuarded(SaleId("completed"), voidedAt, voidedAt)
        val pending = database.exportDao().getPendingChanges(terminal).single()
        assertEquals(2, pending.sale.revision)
        assertEquals(day, pending.sale.businessDate)
        assertEquals("line-completed", pending.lines.single().lineId.value)
    }

    @Test fun staleMarkNeverRegressesExportRevision() = runBlocking {
        insert("sale", SaleStatus.VOIDED, 2)
        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("sale"), 2)), firstExport, "new")
        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("sale"), 1)), firstExport.plusSeconds(5), "stale")
        assertEquals(0, database.exportDao().observeSummary(terminal).first().pendingCount)
        assertEquals(emptyList<SaleWithLinesEntity>(), database.exportDao().getPendingChanges(terminal))
    }

    private suspend fun insert(id: String, status: SaleStatus, revision: Int?) {
        val completed = Instant.parse("2026-08-10T12:00:00Z")
        database.saleDao().insertSale(SaleEntity(
            SaleId(id), terminal, completed.minusSeconds(60), completed, "Mesa 4", status, revision,
            if (revision == null) null else completed,
            if (status == SaleStatus.VOIDED) completed.plusSeconds(10) else null,
            if (revision == null) null else day, "USD", 2
        ))
        database.saleDao().insertSaleLines(listOf(SaleLineEntity(
            LineId("line-$id"), SaleId(id), "item", 1, 1, "Historical", BigDecimal.ONE,
            Money("10.00"), CashDiscountMode.NONE, BigDecimal.ZERO, PricingMode.CASH, false,
            BigDecimal.ZERO, Money.ZERO, Money("10.00"), Money("10.00")
        )))
    }
}
