package com.venkoi.terminal.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.CashDiscountMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class DurableReadbackTest {

    @Test
    fun testDurableReadback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "durable_test.db")
        if (dbFile.exists()) dbFile.delete()

        val terminalId = TerminalId("term-123")
        val restaurantId = RestaurantId("rest-456")
        val now = Instant.now()
        val cutoff = LocalTime.of(4, 0)
        val timezone = ZoneId.of("America/Havana")
        val currency = Currency.getInstance("CUP")

        // 1. Provision in first instance
        runBlocking {
            val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            val terminal = TerminalEntity(
                terminalId = terminalId,
                restaurantId = restaurantId,
                terminalName = "Tablet 1",
                createdAt = now
            )
            val restaurant = RestaurantConfigEntity(
                restaurantId = restaurantId.value,
                restaurantName = "Venkoi Bistro",
                timezone = timezone,
                currency = currency,
                businessDayCutoff = cutoff
            )
            val menu = PublishedMenuEntity(
                menuId = "menu-789",
                publicationRevision = 123,
                publishedAtUtc = now,
                defaultCashDiscountPercent = BigDecimal("10.00"),
                importTimestamp = now
            )
            val categories = listOf(CategoryEntity("cat-1", "Food", 1))
            val items = listOf(
                MenuItemEntity(
                    id = "item-1",
                    categoryId = "cat-1",
                    name = "Burger",
                    active = true,
                    displayOrder = 1,
                    regularPrice = Money("150.00"),
                    cashDiscountMode = CashDiscountMode.APPLY_DEFAULT,
                    commercialRevision = 5,
                    consumptionRevision = 2
                )
            )

            db1.menuDao().provisionTerminal(terminal, restaurant, menu, categories, items)
            db1.close()
        }

        // 2. Reopen and verify in second instance
        runBlocking {
            val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
            
            val terminal = db2.terminalDao().getTerminalConfiguration()
            assertNotNull(terminal)
            assertEquals(terminalId, terminal?.terminalId)
            assertEquals("Tablet 1", terminal?.terminalName)

            val restaurant = db2.menuDao().getRestaurantConfig()
            assertNotNull(restaurant)
            assertEquals("Venkoi Bistro", restaurant?.restaurantName)
            assertEquals(timezone, restaurant?.timezone)
            assertEquals(currency, restaurant?.currency)
            assertEquals(cutoff, restaurant?.businessDayCutoff)

            val menu = db2.menuDao().getPublishedMenu()
            assertNotNull(menu)
            assertEquals("menu-789", menu?.menuId)
            assertEquals(123, menu?.publicationRevision)
            assertEquals(0, BigDecimal("10.00").compareTo(menu?.defaultCashDiscountPercent))

            val items = db2.menuDao().getMenuItem("item-1")
            assertNotNull(items)
            assertEquals("Burger", items?.name)
            assertEquals(0, BigDecimal("150.00").compareTo(items?.regularPrice?.amount))
            assertEquals(CashDiscountMode.APPLY_DEFAULT, items?.cashDiscountMode)
            assertEquals(5, items?.commercialRevision)
            assertEquals(2, items?.consumptionRevision)

            db2.close()
        }

        dbFile.delete()
    }
}
