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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

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
    fun testGuardedLineOperations() = runBlocking {
        val saleId = SaleId("sale-1")
        val now = Instant.now()
        
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
            businessDate = LocalDate.now(),
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)

        val line = SaleLineEntity(
            lineId = LineId("l1"),
            saleId = saleId,
            menuItemId = "item-1",
            commercialRevision = 1,
            consumptionRevision = 1,
            itemNameSnapshot = "Burger",
            quantity = BigDecimal.ONE,
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

        // Try to add line to completed sale
        saleDao.updateLinesAndSale(saleId, listOf(line), now.plusSeconds(10))
        
        val lines = saleDao.getSaleLinesSync(saleId)
        assertTrue(lines.isEmpty())
        
        // Verify sale updatedAtUtc not changed
        val reRead = saleDao.getSaleSync(saleId)
        assertEquals(now, reRead?.updatedAtUtc)
    }
}
