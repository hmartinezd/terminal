package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.RestaurantConfiguration
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.ReportRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.ResolveCurrentReportBusinessDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ReportTab {
    MONEY,
    PRODUCTS
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val menuRepository: MenuRepository,
    terminalConfigurationRepository: TerminalConfigurationRepository,
    private val resolveCurrentReportBusinessDate: ResolveCurrentReportBusinessDate,
    private val clock: Clock
) : ViewModel() {

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _selectedTab = MutableStateFlow(ReportTab.MONEY)
    val selectedTab: StateFlow<ReportTab> = _selectedTab.asStateFlow()

    val restaurantConfiguration: StateFlow<RestaurantConfiguration?> = menuRepository.observeRestaurantConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val terminalConfiguration = terminalConfigurationRepository.observeConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun now() = clock.now()

    init {
        restaurantConfiguration.onEach { config ->
            if (config != null && _selectedDate.value == null) {
                _selectedDate.value = resolveCurrentReportBusinessDate.resolve(config)
            }
        }.launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyMoneyReport: StateFlow<DailyMoneyReport?> = _selectedDate
        .flatMapLatest { date ->
            if (date != null) {
                reportRepository.observeDailyMoneyReport(date)
            } else {
                kotlinx.coroutines.flow.flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val productReport: StateFlow<ProductReport?> = _selectedDate
        .flatMapLatest { date ->
            if (date != null) {
                reportRepository.observeProductReport(date)
            } else {
                kotlinx.coroutines.flow.flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    fun onPreviousDay() {
        _selectedDate.value = _selectedDate.value?.minusDays(1)
    }

    fun onNextDay() {
        _selectedDate.value = _selectedDate.value?.plusDays(1)
    }

    fun onToday() {
        restaurantConfiguration.value?.let { config ->
            _selectedDate.value = resolveCurrentReportBusinessDate.resolve(config)
        }
    }

    fun onTabSelected(tab: ReportTab) {
        _selectedTab.value = tab
    }
}
