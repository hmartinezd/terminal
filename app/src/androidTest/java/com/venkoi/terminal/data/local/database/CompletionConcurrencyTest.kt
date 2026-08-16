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
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CompletionConcurrencyTest {

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
    fun testCompletionVsLineRemovalConcurrency() = runBlocking {
        val saleId = SaleId("sale-race")
        val lineId = LineId("line-race")
        val now = Instant.now()
        
        // Setup: OPEN sale with ONE line
        val sale = SaleEntity(
            saleId = saleId,
            terminalId = TerminalId("t1"),
            openedAtUtc = now,
            updatedAtUtc = now,
            tableLabel = "Race",
            status = SaleStatus.OPEN,
            revision = null,
            completedAtUtc = null,
            voidedAtUtc = null,
            businessDate = null,
            currencyCodeSnapshot = "CUP",
            currencyScaleSnapshot = 2
        )
        saleDao.insertSale(sale)
        
        val line = SaleLineEntity(
            lineId = lineId,
            saleId = saleId,
            menuItemId = "item-1",
            commercialRevision = 1,
            consumptionRevision = 1,
            itemNameSnapshot = "Burger",
            quantity = BigDecimal.ONE,
            regularUnitPriceSnapshot = Money("100"),
            cashDiscountModeSnapshot = CashDiscountMode.NONE,
            cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO,
            pricingMode = PricingMode.TRANSFER,
            cashDiscountApplied = false,
            cashDiscountPercent = BigDecimal.ZERO,
            cashDiscountAmount = Money.ZERO,
            finalUnitPrice = Money("100"),
            lineTotal = Money("100")
        )
        saleDao.insertSaleLines(listOf(line))

        // We want to simulate a race where completion and removal happen nearly simultaneously.
        // Since we are using a real (in-memory) database, the Room transaction will serialise them.
        
        val completionJob = async(Dispatchers.IO) {
            saleDao.completeSaleValidated(saleId, now, LocalDate.now(), now)
        }
        
        val removalJob = async(Dispatchers.IO) {
            saleDao.removeLineAndUpdateSale(saleId, lineId, now)
        }

        val completionResult = completionJob.await()
        removalJob.await()

        val finalSale = saleDao.getSaleSync(saleId)
        val finalLines = saleDao.getSaleLinesSync(saleId)

        if (finalSale?.status == SaleStatus.COMPLETED) {
            // If completion won, there MUST be at least one line (the one that was there)
            // Wait, if completion won, it means it saw the line.
            // If removal also "succeeded" in its own transaction, it might have removed the line AFTER completion.
            // But removeLineAndUpdateSale is also guarded by status = OPEN!
            
            // So if completion won, removal should have FAILED (affected 0).
            assertFalse("COMPLETED sale must have lines", finalLines.isEmpty())
            assertEquals(SaleStatus.COMPLETED, finalSale.status)
        } else {
            // If removal won, sale remains OPEN but line is gone.
            // Completion should have returned 3 (EmptySale)
            assertEquals(SaleStatus.OPEN, finalSale?.status)
            assertTrue(finalLines.isEmpty())
            assertEquals(3, completionResult)
        }
    }
}
