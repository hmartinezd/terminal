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

    @Test fun legacyMissingProvenanceIsSeparateAndNeverBecomesExportable() = runBlocking {
        insert("legacy", SaleStatus.COMPLETED, 1, provenance = false)
        insert("canonical", SaleStatus.COMPLETED, 1)

        assertEquals(listOf("canonical"), database.exportDao().getPendingChanges(terminal).map { it.sale.saleId.value })
        assertEquals(listOf("legacy"), database.exportDao().getLegacyPendingChanges(terminal).map { it.sale.saleId.value })
        assertEquals(1, database.exportDao().observeSummary(terminal).first().pendingCount)
        assertEquals(1, database.exportDao().observeSummary(terminal).first().legacyUnsupportedCount)

        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("canonical"), 1)), firstExport, "canonical-export")
        assertEquals(listOf("legacy"), database.exportDao().getLegacyPendingChanges(terminal).map { it.sale.saleId.value })
        assertEquals(0, database.exportDao().getExportState(SaleId("legacy"))?.lastExportedRevision ?: 0)
    }

    @Test fun dayExportStillReturnsEveryCanonicalPublicationAfterAllWereExported() = runBlocking {
        insert("p1", SaleStatus.COMPLETED, 1, menuId = "menu-1", publicationRevision = 1)
        insert("p2", SaleStatus.COMPLETED, 1, menuId = "menu-2", publicationRevision = 2)
        insert("p3", SaleStatus.COMPLETED, 1, menuId = "menu-3", publicationRevision = 3)
        database.exportDao().markExported(
            listOf(
                ExportedSaleRevision(SaleId("p1"), 1),
                ExportedSaleRevision(SaleId("p2"), 1),
                ExportedSaleRevision(SaleId("p3"), 1)
            ), firstExport, "historical-export"
        )

        assertEquals(emptyList<SaleWithLinesEntity>(), database.exportDao().getPendingChanges(terminal))
        assertEquals(listOf("p1", "p2", "p3"), database.exportDao().getSalesForDay(terminal, day).map { it.sale.saleId.value })
    }

    @Test fun dayExportFiltersTerminalDateAndLifecycleStatusAndIncludesLines() = runBlocking {
        insert("completed-a", SaleStatus.COMPLETED, 1)
        insert("voided-a", SaleStatus.VOIDED, 2)
        insert("open-a", SaleStatus.OPEN, null)
        insert("discarded-a", SaleStatus.DISCARDED, null)
        insert("other-day", SaleStatus.COMPLETED, 1, businessDate = day.plusDays(1))
        insert("other-terminal", SaleStatus.COMPLETED, 1, owner = TerminalId("terminal-b"))

        val result = database.exportDao().getSalesForDay(terminal, day)
        assertEquals(listOf("completed-a", "voided-a"), result.map { it.sale.saleId.value }.sorted())
        assertEquals(listOf("line-completed-a", "line-voided-a"), result.flatMap { it.lines }.map { it.lineId.value }.sorted())
    }

    @Test fun voidBeforeFirstExportIsPendingAtRevisionTwoOnly() = runBlocking {
        insert("sale", SaleStatus.COMPLETED, 1)
        val voidedAt = firstExport.plusSeconds(10)
        database.saleDao().voidSaleGuarded(SaleId("sale"), voidedAt, voidedAt)

        val pending = database.exportDao().getPendingChanges(terminal).single()
        assertEquals(2, pending.sale.revision)
    }

    @Test fun markingPreparedRevisionOneAfterConcurrentVoidLeavesRevisionTwoPending() = runBlocking {
        insert("sale", SaleStatus.COMPLETED, 1)
        val prepared = ExportedSaleRevision(SaleId("sale"), 1)
        val voidedAt = firstExport.plusSeconds(10)
        database.saleDao().voidSaleGuarded(SaleId("sale"), voidedAt, voidedAt)
        database.exportDao().markExported(listOf(prepared), firstExport, "prepared-rev-1")

        assertEquals(2, database.exportDao().getPendingChanges(terminal).single().sale.revision)
        assertEquals(1, database.exportDao().getExportState(SaleId("sale"))?.lastExportedRevision)
    }

    @Test fun exportedCompletedRevisionThenVoidedRevisionKeepsProvenanceAndExactBookkeeping() = runBlocking {
        insert("sale", SaleStatus.COMPLETED, 1)
        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("sale"), 1)), firstExport, "completed")

        database.saleDao().voidSaleGuarded(SaleId("sale"), firstExport.plusSeconds(10), firstExport.plusSeconds(10))
        val pendingVoid = database.exportDao().getPendingChanges(terminal).single()
        assertEquals(2, pendingVoid.sale.revision)
        assertEquals("restaurant-a", pendingVoid.sale.restaurantIdSnapshot)
        assertEquals("menu-a", pendingVoid.sale.menuIdSnapshot)
        assertEquals(1, pendingVoid.sale.publicationRevisionSnapshot)
        assertEquals(listOf("line-sale"), pendingVoid.lines.map { it.lineId.value })

        database.exportDao().markExported(listOf(ExportedSaleRevision(SaleId("sale"), 2)), firstExport.plusSeconds(20), "voided")
        assertEquals(2, database.exportDao().getExportState(SaleId("sale"))?.lastExportedRevision)
        assertEquals(0, database.exportDao().observeSummary(terminal).first().pendingCount)
    }

    private suspend fun insert(
        id: String,
        status: SaleStatus,
        revision: Int?,
        businessDate: LocalDate = day,
        owner: TerminalId = terminal,
        provenance: Boolean = true,
        menuId: String = "menu-a",
        publicationRevision: Int = 1
    ) {
        val completed = Instant.parse("2026-08-10T12:00:00Z")
        database.saleDao().insertSale(SaleEntity(
            SaleId(id), owner, completed.minusSeconds(60), completed, "Mesa 4", status, revision,
            if (revision == null) null else completed,
            if (status == SaleStatus.VOIDED) completed.plusSeconds(10) else null,
            if (revision == null) null else businessDate, "USD", 2,
            if (provenance) "restaurant-a" else null,
            if (provenance) menuId else null,
            if (provenance) publicationRevision else null
        ))
        database.saleDao().insertSaleLines(listOf(SaleLineEntity(
            LineId("line-$id"), SaleId(id), "item", 1, 1, "Historical", BigDecimal.ONE,
            Money("10.00"), CashDiscountMode.NONE, BigDecimal.ZERO, PricingMode.CASH, false,
            BigDecimal.ZERO, Money.ZERO, Money("10.00"), Money("10.00")
        )))
    }
}
