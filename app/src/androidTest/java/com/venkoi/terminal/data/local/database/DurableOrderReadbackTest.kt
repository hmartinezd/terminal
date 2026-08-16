package com.venkoi.terminal.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.*
import com.venkoi.terminal.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DurableOrderReadbackTest {

    @Test
    fun testOrderDurability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "order_durable_test.db")
        if (dbFile.exists()) dbFile.delete()

        val saleId = SaleId("sale-123")
        val lineId = LineId("line-456")
        val terminalId = TerminalId("term-1")
        val now = Instant.now()

        // 1. Persist in first instance
        runBlocking {
            val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            
            val order = OpenOrderEntity(
                saleId = saleId,
                terminalId = terminalId,
                openedAtUtc = now,
                updatedAtUtc = now,
                tableLabel = "Mesa 4",
                status = OrderStatus.OPEN
            )
            
            val line = OpenOrderLineEntity(
                lineId = lineId,
                saleId = saleId,
                menuItemId = "item-1",
                commercialRevision = 20,
                consumptionRevision = 5,
                itemNameSnapshot = "Burger",
                quantity = BigDecimal("2"),
                regularUnitPriceSnapshot = Money("1500"),
                cashDiscountModeSnapshot = CashDiscountMode.APPLY_DEFAULT,
                cashDiscountPolicyPercentSnapshot = BigDecimal("10"),
                pricingMode = PricingMode.CASH,
                cashDiscountApplied = true,
                cashDiscountPercent = BigDecimal("10"),
                cashDiscountAmount = Money("300"),
                finalUnitPrice = Money("1350"),
                lineTotal = Money("2700")
            )

            db1.orderDao().insertOrder(order)
            db1.orderDao().insertOrderLines(listOf(line))
            db1.close()
        }

        // 2. Readback in second instance
        runBlocking {
            val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            
            val order = db2.orderDao().observeOrder(saleId).first()
            assertNotNull(order)
            assertEquals("Mesa 4", order?.tableLabel)
            assertEquals(OrderStatus.OPEN, order?.status)
            
            val lines = db2.orderDao().observeOrderLines(saleId).first()
            assertEquals(1, lines.size)
            val line = lines.first()
            assertEquals(lineId, line.lineId)
            assertEquals("item-1", line.menuItemId)
            assertEquals(20, line.commercialRevision)
            assertEquals("Burger", line.itemNameSnapshot)
            assertEquals(0, BigDecimal("2").compareTo(line.quantity))
            assertEquals(0, BigDecimal("1500").compareTo(line.regularUnitPriceSnapshot.amount))
            assertEquals(PricingMode.CASH, line.pricingMode)
            assertEquals(true, line.cashDiscountApplied)
            assertEquals(0, BigDecimal("2700").compareTo(line.lineTotal.amount))

            db2.close()
        }

        dbFile.delete()
    }
}
