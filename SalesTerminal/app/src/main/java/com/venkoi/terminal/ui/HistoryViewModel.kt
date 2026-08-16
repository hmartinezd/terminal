package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.domain.repository.VoidResult
import com.venkoi.terminal.domain.service.CalculateOrderTotals
import com.venkoi.terminal.domain.service.OrderTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class SaleWithTotal(
    val sale: Sale,
    val grandTotal: Money
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val menuRepository: MenuRepository,
    terminalConfigurationRepository: TerminalConfigurationRepository,
    private val clock: Clock
) : ViewModel() {

    val restaurantTimezone: StateFlow<ZoneId?> = menuRepository.observeRestaurantConfiguration()
        .map { it?.timezone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val restaurantConfiguration = menuRepository.observeRestaurantConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val terminalConfiguration = terminalConfigurationRepository.observeConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun now() = clock.now()

    val historySales: StateFlow<List<SaleWithTotal>> = saleRepository.observeHistorySales()
        .flatMapLatest { sales ->
            if (sales.isEmpty()) flowOf(emptyList())
            else {
                val flows = sales.map { sale ->
                    saleRepository.observeSaleLines(sale.saleId).map { lines ->
                        val total = CalculateOrderTotals.calculate(lines).grandTotal
                        SaleWithTotal(sale, total)
                    }
                }
                combine(flows) { it.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSaleId = MutableStateFlow<SaleId?>(null)
    val selectedSaleId: StateFlow<SaleId?> = _selectedSaleId

    val selectedSale: StateFlow<Sale?> = _selectedSaleId.flatMapLatest { id ->
        if (id == null) flowOf(null) else saleRepository.observeSale(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSaleLines: StateFlow<List<SaleLine>> = _selectedSaleId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else saleRepository.observeSaleLines(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedSaleTotals: StateFlow<OrderTotals?> = selectedSaleLines.map { lines ->
        if (lines.isEmpty()) null else CalculateOrderTotals.calculate(lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isVoiding = MutableStateFlow(false)
    val isVoiding: StateFlow<Boolean> = _isVoiding

    private val _voidResult = MutableStateFlow<VoidResult?>(null)
    val voidResult: StateFlow<VoidResult?> = _voidResult

    private val _voidSuccess = MutableStateFlow(false)
    val voidSuccess: StateFlow<Boolean> = _voidSuccess

    private val _voidFailed = MutableStateFlow(false)
    val voidFailed: StateFlow<Boolean> = _voidFailed

    fun selectSale(saleId: SaleId?) {
        _selectedSaleId.value = saleId
    }

    fun voidSale(saleId: SaleId) {
        if (_isVoiding.value) return

        _isVoiding.value = true
        _voidResult.value = null
        _voidSuccess.value = false
        _voidFailed.value = false

        viewModelScope.launch {
            try {
                val result = try {
                    saleRepository.voidSale(saleId)
                } catch (_: Exception) {
                    _voidFailed.value = true
                    return@launch
                }
                _voidResult.value = result
                if (result is VoidResult.Success || result is VoidResult.AlreadyVoided) {
                    _voidSuccess.value = true
                }
            } finally {
                _isVoiding.value = false
            }
        }
    }

    fun clearVoidFeedback() {
        _voidResult.value = null
        _voidSuccess.value = false
        _voidFailed.value = false
    }
}
