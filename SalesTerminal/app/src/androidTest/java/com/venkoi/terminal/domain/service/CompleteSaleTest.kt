package com.venkoi.terminal.domain.service

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.*
import com.venkoi.terminal.data.local.database.AppDatabase
import com.venkoi.terminal.data.local.database.SaleDao
import com.venkoi.terminal.data.local.database.SaleEntity
import com.venkoi.terminal.data.local.database.SaleLineEntity
import com.venkoi.terminal.domain.model.*
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

class FakeClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

class FakeMenuRepository(val restaurant: RestaurantConfiguration) : MenuRepository {
    override fun observeRestaurantConfiguration(): Flow<RestaurantConfiguration?> = flowOf(restaurant)
    override fun observePublishedMenu(): Flow<PublishedMenu?> = flowOf(null)
    override fun observeCategories(): Flow<List<MenuCategory>> = flowOf(emptyList())
    override fun observeMenuItems(): Flow<List<MenuItem>> = flowOf(emptyList())
    override fun observeActiveMenuItems(): Flow<List<MenuItem>> = flowOf(emptyList())
    override suspend fun installMenu(restaurant: RestaurantConfiguration, menu: PublishedMenu, categories: List<MenuCategory>, items: List<MenuItem>) {}
    override suspend fun getPublishedMenu(): PublishedMenu? = null
    override suspend fun getRestaurantConfiguration(): RestaurantConfiguration? = restaurant
    override suspend fun getMenuItem(id: String): MenuItem? = null
}

@RunWith(AndroidJUnit4::class)
class CompleteSaleTest {

    private lateinit var db: AppDatabase
    private lateinit var saleDao: SaleDao
    private lateinit var fakeClock: FakeClock
    private val businessDateResolver = BusinessDateResolver()
    private lateinit var completeSale: CompleteSale

    private val zoneIdHavana = ZoneId.of("America/Havana")
    private val cutoffHavana = LocalTime.of(4, 0)
    private val restaurant = RestaurantConfiguration(
        restaurantId = "rest-1",
        restaurantName = "Venkoi",
        timezone = zoneIdHavana,
        currency = Currency.getInstance("CUP"),
        businessDayCutoff = cutoffHavana
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        saleDao = db.saleDao()
        fakeClock = FakeClock(Instant.now())
        val menuRepository = FakeMenuRepository(restaurant)
        completeSale = CompleteSale(saleDao, menuRepository, businessDateResolver, fakeClock)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testCompleteAt0359Havana() = runBlocking {
        // 2026-08-15 03:59:59 local in Havana is 07:59:59 UTC (August is EDT, UTC-4)
        val instant = Instant.parse("2026-08-15T07:59:59Z")
        fakeClock.current = instant
        
        val saleId = SaleId("sale-0359")
        insertOpenSaleWithLine(saleId, instant)

        val result = completeSale.execute(saleId)
        assertEquals(SaleCompletionResult.Success, result)

        val completed = saleDao.getSaleSync(saleId)
        assertEquals(LocalDate.parse("2026-08-14"), completed?.businessDate)
    }

    @Test
    fun testCompleteAt0400Havana() = runBlocking {
        // 2026-08-15 04:00:00 local in Havana is 08:00:00 UTC
        val instant = Instant.parse("2026-08-15T08:00:00Z")
        fakeClock.current = instant
        
        val saleId = SaleId("sale-0400")
        insertOpenSaleWithLine(saleId, instant)

        val result = completeSale.execute(saleId)
        assertEquals(SaleCompletionResult.Success, result)

        val completed = saleDao.getSaleSync(saleId)
        assertEquals(LocalDate.parse("2026-08-15"), completed?.businessDate)
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
        val line = SaleLineEntity(
            lineId = LineId("l-${saleId.value}"),
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
        saleDao.insertSaleLines(listOf(line))
    }
}
