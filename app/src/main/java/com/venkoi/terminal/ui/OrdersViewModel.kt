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
import com.venkoi.terminal.licensing.LicenseManager

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val menuRepository: MenuRepository,
    licenseManager: LicenseManager
) : ViewModel() {

    val sellingAllowed = licenseManager.sellingAllowed

    companion object {
        const val MAX_ORDER_LABEL_LENGTH = 100
    }

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

    private val _completionResult = MutableStateFlow<SaleCompletionResult?>(null)
    val completionResult: StateFlow<SaleCompletionResult?> = _completionResult

    private val _completionSuccess = MutableStateFlow(false)
    val completionSuccess: StateFlow<Boolean> = _completionSuccess

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating

    private val _actionFeedback = MutableStateFlow<OrderActionFeedback?>(null)
    val actionFeedback: StateFlow<OrderActionFeedback?> = _actionFeedback

    private val _discardingOrderIds = MutableStateFlow<Set<SaleId>>(emptySet())
    val discardingOrderIds: StateFlow<Set<SaleId>> = _discardingOrderIds

    init {
        viewModelScope.launch {
            openOrders.collect { orders ->
                val selected = _selectedOrderId.value
                if (selected == null || orders.none { it.saleId == selected }) {
                    _selectedOrderId.value = orders.firstOrNull()?.saleId
                }
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun selectOrder(saleId: SaleId) {
        _selectedOrderId.value = saleId
    }

    fun createOrder() {
        if (_isCreating.value) return
        _isCreating.value = true
        viewModelScope.launch {
            try {
                when (val result = runSellingAction { saleRepository.createSale() }) {
                    is SellingActionResult.Success -> _selectedOrderId.value = result.value
                    is SellingActionResult.SellingDenied -> showSellingDenied(result)
                    is SellingActionResult.Failure -> showOperationFailed()
                }
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun discardOrder(saleId: SaleId) {
        if (saleId in _discardingOrderIds.value) return
        _discardingOrderIds.value = _discardingOrderIds.value + saleId
        viewModelScope.launch {
            try {
                saleRepository.discardSale(saleId)
                if (_selectedOrderId.value == saleId) {
                    _selectedOrderId.value = null
                }
            } finally {
                _discardingOrderIds.value = _discardingOrderIds.value - saleId
            }
        }
    }

    fun addItemToCurrentOrder(menuItemId: String) {
        val orderId = _selectedOrderId.value ?: return
        launchSellingAction { saleRepository.addItem(orderId, menuItemId) }
    }

    fun updateQuantity(lineId: LineId, newQuantity: BigDecimal) {
        val orderId = _selectedOrderId.value ?: return
        launchSellingAction { saleRepository.updateLineQuantity(orderId, lineId, newQuantity) }
    }

    fun changePricingMode(lineId: LineId, pricingMode: PricingMode) {
        val orderId = _selectedOrderId.value ?: return
        launchSellingAction { saleRepository.changeLinePricingMode(orderId, lineId, pricingMode) }
    }

    fun removeLine(lineId: LineId) {
        val orderId = _selectedOrderId.value ?: return
        launchSellingAction { saleRepository.removeLine(orderId, lineId) }
    }

    fun updateTableLabel(label: String) {
        val orderId = _selectedOrderId.value ?: return
        if (label.length > MAX_ORDER_LABEL_LENGTH) return
        launchSellingAction { saleRepository.updateSaleLabel(orderId, label.ifBlank { null }) }
    }

    fun completeSale() {
        val saleId = _selectedOrderId.value ?: return
        if (_isCompleting.value) return

        _isCompleting.value = true
        _completionResult.value = null
        _completionSuccess.value = false

        viewModelScope.launch {
            try {
                when (val action = runSellingAction { saleRepository.completeSale(saleId) }) {
                    is SellingActionResult.Success -> {
                        val result = action.value
                        _completionResult.value = result
                        if (result is SaleCompletionResult.Success) {
                            _selectedOrderId.value = null
                            _completionSuccess.value = true
                        }
                    }
                    is SellingActionResult.SellingDenied -> showSellingDenied(action)
                    is SellingActionResult.Failure -> {
                        _completionResult.value = SaleCompletionResult.Failure("Unable to complete sale")
                    }
                }
            } finally {
                _isCompleting.value = false
            }
        }
    }

    fun clearCompletionFeedback() {
        _completionResult.value = null
        _completionSuccess.value = false
    }

    fun clearActionFeedback() {
        _actionFeedback.value = null
    }

    private fun launchSellingAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            when (val result = runSellingAction(action)) {
                is SellingActionResult.Success -> Unit
                is SellingActionResult.SellingDenied -> showSellingDenied(result)
                is SellingActionResult.Failure -> showOperationFailed()
            }
        }
    }

    private fun showSellingDenied(result: SellingActionResult.SellingDenied) {
        _actionFeedback.value = OrderActionFeedback.SellingNotAuthorized(result.reason)
    }

    private fun showOperationFailed() {
        _actionFeedback.value = OrderActionFeedback.OperationFailed
    }
}
