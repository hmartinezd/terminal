package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.OpenOrder
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.OrderRepository
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
    private val orderRepository: OrderRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    val openOrders: StateFlow<List<OpenOrder>> = orderRepository.observeOpenOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedOrderId = MutableStateFlow<SaleId?>(null)
    val selectedOrderId: StateFlow<SaleId?> = _selectedOrderId

    val selectedOrder: StateFlow<OpenOrder?> = _selectedOrderId.flatMapLatest { id ->
        if (id == null) flowOf(null) else orderRepository.observeOrder(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentOrderLines: StateFlow<List<OpenOrderLine>> = _selectedOrderId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else orderRepository.observeOrderLines(id)
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

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun selectOrder(saleId: SaleId) {
        _selectedOrderId.value = saleId
    }

    fun createOrder() {
        viewModelScope.launch {
            val id = orderRepository.createOrder()
            _selectedOrderId.value = id
        }
    }

    fun discardOrder(saleId: SaleId) {
        viewModelScope.launch {
            orderRepository.discardOrder(saleId)
            if (_selectedOrderId.value == saleId) {
                _selectedOrderId.value = null
            }
        }
    }

    fun addItemToCurrentOrder(menuItemId: String) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            orderRepository.addItem(orderId, menuItemId)
        }
    }

    fun updateQuantity(lineId: LineId, newQuantity: BigDecimal) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            orderRepository.updateLineQuantity(orderId, lineId, newQuantity)
        }
    }

    fun changePricingMode(lineId: LineId, pricingMode: PricingMode) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            orderRepository.changeLinePricingMode(orderId, lineId, pricingMode)
        }
    }

    fun removeLine(lineId: LineId) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            orderRepository.removeLine(orderId, lineId)
        }
    }

    fun updateTableLabel(label: String) {
        val orderId = _selectedOrderId.value ?: return
        viewModelScope.launch {
            orderRepository.updateOrderLabel(orderId, label)
        }
    }
}
