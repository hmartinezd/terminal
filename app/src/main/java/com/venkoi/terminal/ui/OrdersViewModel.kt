package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.SaleRepository
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import com.venkoi.terminal.domain.service.CalculateOrderTotals
import com.venkoi.terminal.domain.service.OrderTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    val openOrders: StateFlow<List<Sale>> = saleRepository.observeOpenSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedOrderId = MutableStateFlow<SaleId?>(null)
    val selectedOrderId: StateFlow<SaleId?> = _selectedOrderId

    val selectedOrder: StateFlow<Sale?> = _selectedOrderId.flatMapLatest { id ->
        if (id == null) flowOf(null) else saleRepository.observeSale(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentOrderLines: StateFlow<List<SaleLine>> = _selectedOrderId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else saleRepository.observeSaleLines(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentOrderTotals: StateFlow<OrderTotals?> = currentOrderLines.map { lines ->
        if (lines.isEmpty()) null else CalculateOrderTotals.calculate(lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories = menuRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId

    val menuItems = combine(
        menuRepository.observeActiveMenuItems(),
        _selectedCategoryId
    ) { items, selectedId ->
        if (selectedId == null) items else items.filter { it.categoryId == selectedId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCompleting = MutableStateFlow(false)
    val isCompleting: StateFlow<Boolean> = _isCompleting

    private val _completionError = MutableStateFlow<String?>(null)
    val completionError: StateFlow<String?> = _completionError

    private val _completionSuccess = MutableStateFlow(false)
    val completionSuccess: StateFlow<Boolean> = _completionSuccess

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun selectOrder(saleId: SaleId) {
        _selectedOrderId.value = saleId
    }

    fun createOrder() {
        viewModelScope.launch {
            val id = saleRepository.createSale()
            _selectedOrderId.value = id
        }
    }

    fun discardOrder(saleId: SaleId) {
        viewModelScope.launch {
            saleRepository.discardSale(saleId)
            if (_selectedOrderId.value == saleId) {
                _selectedOrderId.value = null
            }
        }
    }

    fun addItemToCurrentOrder(menuItemId: String) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            saleRepository.addItem(orderId, menuItemId)
        }
    }

    fun updateQuantity(lineId: LineId, newQuantity: BigDecimal) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            saleRepository.updateLineQuantity(orderId, lineId, newQuantity)
        }
    }

    fun changePricingMode(lineId: LineId, pricingMode: PricingMode) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            saleRepository.changeLinePricingMode(orderId, lineId, pricingMode)
        }
    }

    fun removeLine(lineId: LineId) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            saleRepository.removeLine(orderId, lineId)
        }
    }

    fun updateTableLabel(label: String) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            saleRepository.updateSaleLabel(orderId, label)
        }
    }

    fun completeSale() {
        val saleId = _selectedOrderId.value ?: return
        if (_isCompleting.value) return

        _isCompleting.value = true
        _completionError.value = null
        _completionSuccess.value = false

        viewModelScope.launch {
            try {
                val result = saleRepository.completeSale(saleId)
                when (result) {
                    SaleCompletionResult.Success -> {
                        _selectedOrderId.value = null
                        _completionSuccess.value = true
                    }
                    is SaleCompletionResult.Failure -> _completionError.value = result.message
                    SaleCompletionResult.EmptySale -> _completionError.value = "Cannot complete an empty sale"
                    SaleCompletionResult.InvalidQuantity -> _completionError.value = "One or more items have invalid quantity"
                    SaleCompletionResult.NotFound -> _completionError.value = "Sale not found"
                    SaleCompletionResult.NotOpen -> _completionError.value = "Sale is not open"
                }
            } finally {
                _isCompleting.value = false
            }
        }
    }

    fun clearCompletionFeedback() {
        _completionError.value = null
        _completionSuccess.value = false
    }
}
