package com.venkoi.terminal.ui

import com.venkoi.terminal.core.BusinessDateResolver
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.MenuCategory
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.PublishedMenu
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.model.TerminalConfiguration
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.ReportRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.ResolveCurrentReportBusinessDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `refresh updates current business date without changing historical selection`() = runTest(dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()
        fixture.viewModel.onDateSelected(LocalDate.parse("2026-08-15"))
        fixture.clock.instant = Instant.parse("2026-08-20T08:05:00Z")

        fixture.viewModel.refreshCurrentBusinessDate()
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-08-20"), fixture.viewModel.currentBusinessDate.value)
        assertEquals(LocalDate.parse("2026-08-15"), fixture.viewModel.selectedDate.value)
    }

    @Test fun `today resolves fresh business date and selects it`() = runTest(dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()
        fixture.clock.instant = Instant.parse("2026-08-20T08:05:00Z")

        fixture.viewModel.onToday()
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-08-20"), fixture.viewModel.currentBusinessDate.value)
        assertEquals(LocalDate.parse("2026-08-20"), fixture.viewModel.selectedDate.value)
    }

    private class Fixture {
        val clock = MutableClock(Instant.parse("2026-08-20T07:55:00Z"))
        private val restaurant = RestaurantConfiguration(
            "restaurant", "Cafe", ZoneId.of("America/New_York"),
            Currency.getInstance("USD"), LocalTime.of(4, 0)
        )
        private val menuRepository = FakeMenuRepository(restaurant)
        val viewModel = ReportsViewModel(
            FakeReportRepository(), menuRepository, FakeTerminalConfigurationRepository(),
            ResolveCurrentReportBusinessDate(clock, BusinessDateResolver()), clock
        )
    }

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class FakeReportRepository : ReportRepository {
        override fun observeDailyMoneyReport(businessDate: LocalDate): Flow<DailyMoneyReport> =
            flowOf(DailyMoneyReport(businessDate, emptyList()))
        override fun observeProductReport(businessDate: LocalDate): Flow<ProductReport> =
            flowOf(ProductReport(businessDate, emptyList()))
    }

    private class FakeMenuRepository(configuration: RestaurantConfiguration) : MenuRepository {
        private val restaurant = MutableStateFlow<RestaurantConfiguration?>(configuration)
        override fun observeRestaurantConfiguration(): Flow<RestaurantConfiguration?> = restaurant
        override suspend fun getRestaurantConfiguration(): RestaurantConfiguration? = restaurant.value
        override fun observePublishedMenu(): Flow<PublishedMenu?> = flowOf(null)
        override fun observeCategories(): Flow<List<MenuCategory>> = flowOf(emptyList())
        override fun observeMenuItems(): Flow<List<MenuItem>> = flowOf(emptyList())
        override fun observeActiveMenuItems(): Flow<List<MenuItem>> = flowOf(emptyList())
        override suspend fun installMenu(restaurant: RestaurantConfiguration, menu: PublishedMenu, categories: List<MenuCategory>, items: List<MenuItem>) = Unit
        override suspend fun getPublishedMenu(): PublishedMenu? = null
        override suspend fun getMenuItem(id: String): MenuItem? = null
    }

    private class FakeTerminalConfigurationRepository : TerminalConfigurationRepository {
        override suspend fun getConfiguration(): TerminalConfiguration? = null
        override fun observeConfiguration(): Flow<TerminalConfiguration?> = flowOf(null)
        override suspend fun saveConfiguration(configuration: TerminalConfiguration) = Unit
        override suspend fun clearConfiguration() = Unit
        override suspend fun provisionTerminal(configuration: TerminalConfiguration, restaurant: RestaurantConfiguration, menu: PublishedMenu, categories: List<MenuCategory>, items: List<MenuItem>) = Unit
    }
}
