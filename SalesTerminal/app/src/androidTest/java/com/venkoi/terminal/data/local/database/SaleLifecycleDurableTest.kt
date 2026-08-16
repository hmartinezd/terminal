package com.venkoi.terminal.data.local.database

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SaleLifecycleDurableTest {

    @Test
    fun testDurableSaleLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "lifecycle_durable_test.db")
        if (dbFile.exists()) dbFile.delete()

        val saleId = SaleId("sale-durable-1")
        val now = Instant.now()
        val businessDate = LocalDate.now()

        // 1. Create, Add Line, Complete and Void in first instance
        runBlocking {
            val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            val saleDao = db1.saleDao()

            val sale = SaleEntity(
                saleId = saleId,
                terminalId = TerminalId("t1"),
                openedAtUtc = now,
                updatedAtUtc = now,
                tableLabel = "Table 5",
                status = SaleStatus.OPEN,
                revision = null,
                completedAtUtc = null,
                voidedAtUtc = null,
                businessDate = null,
                currencyCodeSnapshot = "USD",
                currencyScaleSnapshot = 2
            )
            saleDao.insertSale(sale)

            val line = SaleLineEntity(
                lineId = LineId("l1"),
                saleId = saleId,
                menuItemId = "item-1",
                commercialRevision = 1,
                consumptionRevision = 1,
                itemNameSnapshot = "Coffee",
                quantity = BigDecimal.ONE,
                regularUnitPriceSnapshot = Money("5.00"),
                cashDiscountModeSnapshot = CashDiscountMode.NONE,
                cashDiscountPolicyPercentSnapshot = BigDecimal.ZERO,
                pricingMode = PricingMode.TRANSFER,
                cashDiscountApplied = false,
                cashDiscountPercent = BigDecimal.ZERO,
                cashDiscountAmount = Money.ZERO,
                finalUnitPrice = Money("5.00"),
                lineTotal = Money("5.00")
            )
            saleDao.insertSaleLines(listOf(line))

            // Complete
            saleDao.completeSaleGuarded(saleId, now, businessDate, now)
            
            // Void
            val voidedAt = now.plusSeconds(5)
            saleDao.voidSaleGuarded(saleId, voidedAt, voidedAt)

            db1.close()
        }

        // 2. Reopen and verify
        runBlocking {
            val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            val saleDao = db2.saleDao()

            val sale = saleDao.getSaleSync(saleId)
            assertNotNull(sale)
            assertEquals(SaleStatus.VOIDED, sale?.status)
            assertEquals(2, sale?.revision)
            assertEquals("Table 5", sale?.tableLabel)
            assertEquals(businessDate, sale?.businessDate)
            assertEquals("USD", sale?.currencyCodeSnapshot)
            assertEquals(2, sale?.currencyScaleSnapshot)

            val lines = saleDao.getSaleLinesSync(saleId)
            assertEquals(1, lines.size)
            assertEquals("Coffee", lines[0].itemNameSnapshot)
            assertEquals(0, BigDecimal("5.00").compareTo(lines[0].lineTotal.amount))

            db2.close()
        }

        dbFile.delete()
    }
}
