package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import com.venkoi.terminal.licensing.SellingAuthorizationResult
import com.venkoi.terminal.ui.components.QuantityControl
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.components.CategoryPalette
import com.venkoi.terminal.ui.components.MenuProductCard
import java.math.BigDecimal
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val openOrders by viewModel.openOrders.collectAsState()
    val selectedOrderId by viewModel.selectedOrderId.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val currentLines by viewModel.currentOrderLines.collectAsState()
    val totals by viewModel.currentOrderTotals.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val completionResult by viewModel.completionResult.collectAsState()
    val isCompleting by viewModel.isCompleting.collectAsState()
    val completionSuccess by viewModel.completionSuccess.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val discardingOrderIds by viewModel.discardingOrderIds.collectAsState()
    val sellingAllowed by viewModel.sellingAllowed.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val categoryStyles = remember(categories) {
        CategoryPalette.resolve(categories.sortedWith(compareBy({ it.displayOrder }, { it.id })).map { it.id })
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val localizedCompletionSuccess = stringResource(R.string.orders_sale_completed)
    val localizedSellingDisabled = stringResource(R.string.selling_disabled)
    val localizedClockCorrection = stringResource(R.string.device_time_correction_required)
    val localizedOperationFailed = stringResource(R.string.error_order_action_failed)

    LaunchedEffect(actionFeedback) {
        val feedback = actionFeedback ?: return@LaunchedEffect
        val message = when (feedback) {
            is OrderActionFeedback.SellingNotAuthorized -> {
                if (feedback.reason == SellingAuthorizationResult.DENIED_CLOCK_ROLLBACK) {
                    localizedClockCorrection
                } else {
                    localizedSellingDisabled
                }
            }
            OrderActionFeedback.OperationFailed -> localizedOperationFailed
        }
        snackbarHostState.showSnackbar(message)
        viewModel.clearActionFeedback()
    }

    LaunchedEffect(completionSuccess) {
        if (completionSuccess) {
            snackbarHostState.showSnackbar(localizedCompletionSuccess)
            viewModel.clearCompletionFeedback()
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_title)) },
            text = { Text(stringResource(R.string.dialog_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedOrderId?.let { viewModel.discardOrder(it) }
                    showDiscardDialog = false
                }, enabled = selectedOrderId !in discardingOrderIds) {
                    Text(stringResource(R.string.dialog_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCompleting) showCompleteDialog = false },
            title = { Text(stringResource(R.string.dialog_complete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_complete_message))
                    Spacer(Modifier.height(16.dp))
                    totals?.let { t ->
                        selectedOrder?.let { sale ->
                            TotalRow(stringResource(R.string.totals_cash_total), HistoryMoneyFormatter.format(t.cashTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale))
                            TotalRow(stringResource(R.string.totals_transfer_total), HistoryMoneyFormatter.format(t.transferTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        TotalRow(
                            stringResource(R.string.totals_grand_total), 
                            selectedOrder?.let { HistoryMoneyFormatter.format(t.grandTotal, it.currencyCodeSnapshot, it.currencyScaleSnapshot, locale) } ?: t.grandTotal.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isCompleting) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.completeSale() },
                    enabled = sellingAllowed && !isCompleting
                ) {
                    Text(stringResource(R.string.dialog_complete_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompleteDialog = false },
                    enabled = !isCompleting
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    LaunchedEffect(completionSuccess) {
        if (completionSuccess) {
            showCompleteDialog = false
        }
    }

    completionResult?.let { result ->
        if (result !is SaleCompletionResult.Success) {
            AlertDialog(
                onDismissRequest = { viewModel.clearCompletionFeedback() },
                title = { Text(stringResource(R.string.dialog_error_title)) },
                text = { Text(result.toLocalizedMessage()) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearCompletionFeedback() }) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (!sellingAllowed) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.selling_disabled), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left Panel: Open Orders
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.orders_open_orders),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (openOrders.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.orders_select_order),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(openOrders) { order ->
                            val isSelected = selectedOrderId == order.saleId
                            TerminalCard(
                                onClick = { viewModel.selectOrder(order.saleId) },
                                selected = isSelected,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val label = order.tableLabel?.ifBlank { null } ?: order.saleId.value.takeLast(6).uppercase()
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!order.tableLabel.isNullOrBlank()) {
                                    Text(
                                        text = "#${order.saleId.value.takeLast(6).uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.createOrder() },
                    enabled = sellingAllowed && !isCreating && !isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.orders_new_order))
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Center Panel: Menu
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.orders_menu),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text(stringResource(R.string.orders_category_all)) },
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                    items(categories) { category ->
                        val categoryStyle = categoryStyles[category.id] ?: CategoryPalette.fallback
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = categoryStyle.background,
                                selectedContainerColor = categoryStyle.accent,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategoryId == category.id,
                                borderColor = categoryStyle.accent.copy(alpha = 0.65f),
                                selectedBorderColor = categoryStyle.accent
                            )
                        )
                    }
                }

                if (menuItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.orders_empty_menu),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(menuItems) { item ->
                            MenuProductCard(
                                name = item.name,
                                price = selectedOrder?.let { HistoryMoneyFormatter.format(item.regularPrice, it.currencyCodeSnapshot, it.currencyScaleSnapshot, locale) } ?: item.regularPrice.toString(),
                                categoryStyle = categoryStyles[item.categoryId] ?: CategoryPalette.fallback,
                                enabled = sellingAllowed && !isCompleting,
                                onClick = { 
                                    viewModel.addItemToCurrentOrder(item.id)
                                },
                                modifier = Modifier.height(136.dp)
                            )
                        }
                    }
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Right Panel: Current Order
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                selectedOrder?.let { order ->
                    var tableLabel by remember(order.saleId) { mutableStateOf(order.tableLabel ?: "") }

                    LaunchedEffect(order.saleId, order.tableLabel, actionFeedback) {
                        if (actionFeedback is OrderActionFeedback.SellingNotAuthorized) {
                            tableLabel = order.tableLabel ?: ""
                        }
                    }
                    
                    OutlinedTextField(
                        value = tableLabel,
                        onValueChange = { 
                            if (!isCompleting) {
                                if (it.length <= OrdersViewModel.MAX_ORDER_LABEL_LENGTH) {
                                    tableLabel = it
                                    viewModel.updateTableLabel(it)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.orders_table_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        enabled = sellingAllowed && !isCompleting
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(currentLines) { line ->
                            OrderLineItemCard(
                                line = line,
                                currencyCode = order.currencyCodeSnapshot,
                                currencyScale = order.currencyScaleSnapshot,
                                locale = locale,
                                onUpdateQuantity = { viewModel.updateQuantity(line.lineId, it) },
                                onChangePricingMode = { viewModel.changePricingMode(line.lineId, it) },
                                onRemove = { viewModel.removeLine(line.lineId) },
                                enabled = sellingAllowed && !isCompleting
                            )
                        }
                    }

                    totals?.let { t ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${stringResource(R.string.pricing_mode_cash)} ${HistoryMoneyFormatter.format(t.cashTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${stringResource(R.string.pricing_mode_transfer)} ${HistoryMoneyFormatter.format(t.transferTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (t.cashDiscounts.amount > BigDecimal.ZERO) {
                                TotalRow(
                                    stringResource(R.string.totals_cash_discounts), 
                                    "-${HistoryMoneyFormatter.format(t.cashDiscounts, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    verticalPadding = 2.dp
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            TotalRow(
                                stringResource(R.string.totals_grand_total), 
                                HistoryMoneyFormatter.format(t.grandTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                verticalPadding = 2.dp
                            )
                        }
                    }
                    
                    Button(
                        onClick = { showCompleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sellingAllowed && currentLines.isNotEmpty() && !isCompleting,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text(
                            text = stringResource(R.string.orders_complete_sale),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    TextButton(
                        onClick = { showDiscardDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCompleting,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.orders_discard_order))
                    }
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.orders_select_order),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }
    }
}

@Composable
fun OrderLineItemCard(
    line: SaleLine,
    currencyCode: String,
    currencyScale: Int,
    locale: java.util.Locale,
    onUpdateQuantity: (BigDecimal) -> Unit,
    onChangePricingMode: (PricingMode) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = line.itemNameSnapshot,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Text(
                    text = HistoryMoneyFormatter.format(line.lineTotal, currencyCode, currencyScale, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PricingModeSelector(
                    selectedMode = line.pricingMode,
                    onModeSelected = onChangePricingMode,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                QuantityControl(
                    quantity = line.quantity,
                    onIncrease = { onUpdateQuantity(line.quantity.add(BigDecimal.ONE)) },
                    onDecrease = { onUpdateQuantity(line.quantity.subtract(BigDecimal.ONE)) },
                    enabled = enabled
                )
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_remove),
                        modifier = Modifier.size(20.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (line.pricingMode == PricingMode.CASH && line.cashDiscountApplied) {
                Text(
                    text = stringResource(
                        R.string.pricing_mode_cash_discount,
                        line.cashDiscountPercent.stripTrailingZeros().toPlainString()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PricingModeSelector(
    selectedMode: PricingMode,
    onModeSelected: (PricingMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        val modes = listOf(PricingMode.CASH, PricingMode.TRANSFER)
        modes.forEach { mode ->
            val isSelected = selectedMode == mode
            val label = when (mode) {
                PricingMode.CASH -> stringResource(R.string.pricing_mode_cash)
                PricingMode.TRANSFER -> stringResource(R.string.pricing_mode_transfer)
            }
            
            Surface(
                onClick = { if (enabled) onModeSelected(mode) },
                selected = isSelected,
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(36.dp).weight(1f)
            ) {
                Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TotalRow(
    label: String, 
    value: String, 
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, 
    color: Color = Color.Unspecified,
    verticalPadding: androidx.compose.ui.unit.Dp = 4.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = style)
        Text(value, style = style, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SaleCompletionResult.toLocalizedMessage(): String {
    return when (this) {
        is SaleCompletionResult.Success -> stringResource(R.string.orders_sale_completed)
        is SaleCompletionResult.Failure -> stringResource(R.string.error_complete_failed)
        SaleCompletionResult.EmptySale -> stringResource(R.string.error_empty_sale)
        SaleCompletionResult.InvalidQuantity -> stringResource(R.string.error_invalid_quantity)
        SaleCompletionResult.NotFound -> stringResource(R.string.error_order_not_found)
        SaleCompletionResult.NotOpen -> stringResource(R.string.error_order_not_open)
    }
}
