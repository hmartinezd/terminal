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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SaleDaoLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var saleDao: SaleDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        saleDao = db.saleDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testCompleteSaleGuarded() = runBlocking {
        val saleId = SaleId("sale-1")
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Table 1",
            status = SaleStatus.OPEN,
            revision = null,
            completedAtUtc = null,
            voidedAtUtc = null,
            businessDate = null,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        val affected = saleDao.completeSaleGuarded(saleId, now, businessDate, now)
        assertEquals(1, affected)

        val completed = saleDao.getSaleSync(saleId)
        assertEquals(SaleStatus.COMPLETED, completed?.status)
        assertEquals(1, completed?.revision)
        assertEquals(now, completed?.completedAtUtc)
        assertEquals(businessDate, completed?.businessDate)

        // Try to complete again
        val affectedAgain = saleDao.completeSaleGuarded(saleId, now.plusSeconds(10), businessDate, now.plusSeconds(10))
        assertEquals(0, affectedAgain)
        
        // Verify original values preserved
        val reRead = saleDao.getSaleSync(saleId)
        assertEquals(now, reRead?.completedAtUtc)
    }

    @Test
    fun testVoidSaleGuarded() = runBlocking {
        val saleId = SaleId("sale-1")
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Table 1",
            status = SaleStatus.COMPLETED,
            revision = 1,
            completedAtUtc = now,
            voidedAtUtc = null,
            businessDate = businessDate,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        val voidedAt = now.plusSeconds(10)
        val affected = saleDao.voidSaleGuarded(saleId, voidedAt, voidedAt)
        assertEquals(1, affected)

        val voided = saleDao.getSaleSync(saleId)
        assertEquals(SaleStatus.VOIDED, voided?.status)
        assertEquals(2, voided?.revision)
        assertEquals(voidedAt, voided?.voidedAtUtc)

        // Try to void again
        val affectedAgain = saleDao.voidSaleGuarded(saleId, now.plusSeconds(20), now.plusSeconds(20))
        assertEquals(0, affectedAgain)
        
        // Verify original voidedAtUtc preserved
        val reRead = saleDao.getSaleSync(saleId)
        assertEquals(voidedAt, reRead?.voidedAtUtc)
    }

    @Test
    fun testGuardedMutations() = runBlocking {
        val saleId = SaleId("sale-1")
        val now = Instant.now()
        
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Table 1",
            status = SaleStatus.COMPLETED, // Already completed
            revision = 1,
            completedAtUtc = now,
            voidedAtUtc = null,
            businessDate = LocalDate.now(),
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        // Attempt to update label on completed sale
        val affected = saleDao.updateSaleLabelGuarded(saleId, "New Label", now.plusSeconds(10))
        assertEquals(0, affected)
        
        val reRead = saleDao.getSaleSync(saleId)
        assertEquals("Table 1", reRead?.tableLabel)

        // Attempt to discard completed sale
        val affectedDiscard = saleDao.discardSaleGuarded(saleId, now.plusSeconds(10))
        assertEquals(0, affectedDiscard)
        assertEquals(SaleStatus.COMPLETED, saleDao.getSaleSync(saleId)?.status)
    }

    @Test
    fun testCompleteSaleValidatedSuccess() = runBlocking {
        val saleId = SaleId("sale-1")
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        insertOpenSaleWithLine(saleId, now)

        val resultCode = saleDao.completeSaleValidated(saleId, now, businessDate, now)
        assertEquals(0, resultCode) // Success

        val completed = saleDao.getSaleSync(saleId)
        assertEquals(SaleStatus.COMPLETED, completed?.status)
    }

    @Test
    fun testCompleteSaleValidatedEmptySale() = runBlocking {
        val saleId = SaleId("sale-empty")
        val now = Instant.now()
        
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Empty",
            status = SaleStatus.OPEN,
            revision = null,
            completedAtUtc = null,
            voidedAtUtc = null,
            businessDate = null,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        val resultCode = saleDao.completeSaleValidated(saleId, now, LocalDate.now(), now)
        assertEquals(3, resultCode) // EmptySale

        val reRead = saleDao.getSaleSync(saleId)
        assertEquals(SaleStatus.OPEN, reRead?.status)
    }

    @Test
    fun testCompleteSaleValidatedInvalidQuantity() = runBlocking {
        val saleId = SaleId("sale-invalid-qty")
        val now = Instant.now()
        
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Invalid Qty",
            status = SaleStatus.OPEN,
            revision = null,
            completedAtUtc = null,
            voidedAtUtc = null,
            businessDate = null,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        val line = createLineEntity(saleId, BigDecimal.ZERO)
        saleDao.insertSaleLines(listOf(line))

        val resultCode = saleDao.completeSaleValidated(saleId, now, LocalDate.now(), now)
        assertEquals(4, resultCode) // InvalidQuantity

        val reRead = saleDao.getSaleSync(saleId)
        assertEquals(SaleStatus.OPEN, reRead?.status)
    }

    @Test
    fun testExhaustiveImmutability() = runBlocking {
        val saleId = SaleId("sale-immutability")
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        insertOpenSaleWithLine(saleId, now)
        val lineId = LineId("l-${saleId.value}")
        
        // Complete it
        saleDao.completeSaleValidated(saleId, now, businessDate, now)
        
        val snapshotSale = saleDao.getSaleSync(saleId)!!
        val snapshotLines = saleDao.getSaleLinesSync(saleId)
        
        val later = now.plusSeconds(3600)

        // 1. Attempt Label Update
        saleDao.updateSaleLabelGuarded(saleId, "Hacked", later)
        
        // 2. Attempt Add Line
        val newLine = createLineEntity(saleId, BigDecimal.TEN).copy(lineId = LineId("l-new"))
        saleDao.updateLinesAndSale(saleId, listOf(newLine), later)
        
        // 3. Attempt Remove Line
        saleDao.removeLineAndUpdateSale(saleId, lineId, later)
        
        // 4. Attempt Discard
        saleDao.discardSaleGuarded(saleId, later)
        
        // 5. Attempt Merge
        saleDao.mergeLinesAndSale(saleId, lineId, snapshotLines[0].copy(quantity = BigDecimal("2")), later)

        // Verify everything is IDENTICAL to snapshot
        val finalSale = saleDao.getSaleSync(saleId)!!
        assertEquals(snapshotSale, finalSale)
        
        val finalLines = saleDao.getSaleLinesSync(saleId)
        assertEquals(snapshotLines, finalLines)
        
        // 6. Attempt second completion
        val resultCode = saleDao.completeSaleValidated(saleId, later, businessDate, later)
        assertNotEquals(0, resultCode)
        assertEquals(snapshotSale, saleDao.getSaleSync(saleId))
    }

    @Test
    fun testExhaustiveVoidImmutability() = runBlocking {
        val saleId = SaleId("sale-void-immutability")
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        insertOpenSaleWithLine(saleId, now)
        saleDao.completeSaleValidated(saleId, now, businessDate, now)
        
        val voidedAt = now.plusSeconds(60)
        saleDao.voidSaleGuarded(saleId, voidedAt, voidedAt)
        
        val snapshotSale = saleDao.getSaleSync(saleId)!!
        val snapshotLines = saleDao.getSaleLinesSync(saleId)
        
        val later = voidedAt.plusSeconds(3600)

        // Attempt mutations
        saleDao.updateSaleLabelGuarded(saleId, "Hacked", later)
        saleDao.discardSaleGuarded(saleId, later)
        saleDao.removeLineAndUpdateSale(saleId, LineId("l-${saleId.value}"), later)

        val finalSale = saleDao.getSaleSync(saleId)!!
        assertEquals(snapshotSale, finalSale)
        assertEquals(snapshotLines, saleDao.getSaleLinesSync(saleId))
    }

    @Test
    fun testCompleteOneSaleDoesNotAffectOthers() = runBlocking {
        val id1 = SaleId("sale-1")
        val id2 = SaleId("sale-2")
        val now = Instant.now()
        
        insertOpenSaleWithLine(id1, now)
        insertOpenSaleWithLine(id2, now)
        
        val snapshot2 = saleDao.getSaleSync(id2)!!
        val lines2 = saleDao.getSaleLinesSync(id2)

        // Complete sale 1
        saleDao.completeSaleValidated(id1, now, LocalDate.now(), now)
        
        // Verify sale 2 unchanged
        assertEquals(snapshot2, saleDao.getSaleSync(id2))
        assertEquals(lines2, saleDao.getSaleLinesSync(id2))
    }

    @Test
    fun testHistoryFilterAndOrder() = runBlocking {
        val now = Instant.now()
        val businessDate = LocalDate.now()
        
        // 1. OPEN
        insertOpenSaleWithLine(SaleId("open"), now)
        
        // 2. DISCARDED
        val discId = SaleId("discarded")
        insertOpenSaleWithLine(discId, now)
        saleDao.discardSaleGuarded(discId, now)
        
        // 3. COMPLETED Old
        val oldId = SaleId("completed-old")
        val oldTime = now.minusSeconds(100)
        insertOpenSaleWithLine(oldId, oldTime)
        saleDao.completeSaleValidated(oldId, oldTime, businessDate, oldTime)
        
        // 4. COMPLETED New
        val newId = SaleId("completed-new")
        val newTime = now.plusSeconds(100)
        insertOpenSaleWithLine(newId, newTime)
        saleDao.completeSaleValidated(newId, newTime, businessDate, newTime)
        
        // 5. VOIDED
        val voidId = SaleId("voided")
        insertOpenSaleWithLine(voidId, now)
        saleDao.completeSaleValidated(voidId, now, businessDate, now)
        saleDao.voidSaleGuarded(voidId, now.plusSeconds(10), now.plusSeconds(10))

        val history = saleDao.observeHistorySales().first()
        
        // Should only have 3 sales: old, new, voided
        assertEquals(3, history.size)
        
        // Order should be newest first (by completedAtUtc)
        assertEquals(newId, history[0].saleId)
        assertEquals(voidId, history[1].saleId)
        assertEquals(oldId, history[2].saleId)
        
        assertTrue(history.all { it.status == SaleStatus.COMPLETED || it.status == SaleStatus.VOIDED })
    }

    private suspend fun insertOpenSaleWithLine(saleId: SaleId, now: Instant) {
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Table 1",
            status = SaleStatus.OPEN,
            revision = null,
            completedAtUtc = null,
            voidedAtUtc = null,
            businessDate = null,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)
        saleDao.insertSaleLines(listOf(createLineEntity(saleId, BigDecimal.ONE)))
    }

    private fun createLineEntity(saleId: SaleId, quantity: BigDecimal) = SaleLineEntity(
        lineId = LineId("l-${saleId.value}"),
        saleId = saleId,
        menuItemId = "item-1",
        commercialRevision = 1,
        consumptionRevision = 1,
        itemNameSnapshot = "Burger",
        quantity = quantity,
        regularUnitPriceSnapshot = Money(BigDecimal("100")),
        cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
        cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
        pricingMode = PricingMode.CASH,
        cashDiscountApplied = true,
        cashDiscountPercent = BigDecimal("10"),
        cashDiscountAmount = Money(BigDecimal("10")),
        finalUnitPrice = Money(BigDecimal("90")),
        lineTotal = Money(BigDecimal("90"))
    )
}
