package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.service.CalculateOrderTotals
import com.venkoi.terminal.domain.service.OrderTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaleWithTotal(
    val sale: Sale,
    val grandTotal: Money
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val saleRepository: SaleRepository
) : ViewModel() {

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

    fun selectSale(saleId: SaleId?) {
        _selectedSaleId.value = saleId
    }

    fun voidSale(saleId: SaleId) {
        viewModelScope.launch {
            saleRepository.voidSale(saleId)
        }
    }
}
