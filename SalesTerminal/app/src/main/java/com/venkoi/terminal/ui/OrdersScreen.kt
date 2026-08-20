package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.repository.SaleCompletionResult
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.components.MenuProductCard
import com.venkoi.terminal.ui.theme.CategoryPalette
import com.venkoi.terminal.ui.theme.TerminalOrderSummaryContainer
import java.math.BigDecimal
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter
import kotlinx.coroutines.flow.first

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
    val licenseSnapshot by viewModel.licenseSnapshot.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalContext.current.resources
    val categoryStyles = remember(categories) {
        CategoryPalette.resolve(categories.sortedWith(compareBy({ it.displayOrder }, { it.id })).map { it.id })
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val localizedCompletionSuccess = stringResource(R.string.orders_sale_completed)
    val localizedOperationFailed = stringResource(R.string.error_order_action_failed)

    LaunchedEffect(actionFeedback) {
        val feedback = actionFeedback ?: return@LaunchedEffect
        val message = when (feedback) {
            is OrderActionFeedback.SellingNotAuthorized -> {
                resources.getString(sellingDenialMessage(feedback.reason))
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
                    Text(stringResource(R.string.dialog_keep_order))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (!sellingAllowed) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(restrictedBannerMessage(licenseSnapshot.state)),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        OpenOrdersStrip(
            openOrders = openOrders,
            selectedOrderId = selectedOrderId,
            newOrderEnabled = sellingAllowed && !isCreating && !isCompleting,
            onNewOrder = viewModel::createOrder,
            onSelectOrder = viewModel::selectOrder
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

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
                                selectedLabelColor = categoryStyle.selectedContent
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
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tableLabel,
                            onValueChange = {
                                if (!isCompleting && it.length <= OrdersViewModel.MAX_ORDER_LABEL_LENGTH) {
                                        tableLabel = it
                                        viewModel.updateTableLabel(it)
                                }
                            },
                            label = { Text(stringResource(R.string.orders_table_label)) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true,
                            enabled = sellingAllowed && !isCompleting
                        )
                        TextButton(
                            onClick = { showDiscardDialog = true },
                            enabled = !isCompleting,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text(stringResource(R.string.orders_cancel)) }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(currentLines, key = { it.lineId.value }) { line ->
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(TerminalOrderSummaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactModeTotal(
                                    label = stringResource(R.string.pricing_mode_cash),
                                    value = HistoryMoneyFormatter.format(t.cashTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale),
                                    modifier = Modifier.weight(1f)
                                )
                                CompactModeTotal(
                                    label = stringResource(R.string.pricing_mode_transfer),
                                    value = HistoryMoneyFormatter.format(t.transferTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale),
                                    modifier = Modifier.weight(1f)
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.totals_grand_total), style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        HistoryMoneyFormatter.format(t.grandTotal, order.currencyCodeSnapshot, order.currencyScaleSnapshot, locale),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { showCompleteDialog = true },
                                    enabled = sellingAllowed && currentLines.isNotEmpty() && !isCompleting,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) { Text(stringResource(R.string.orders_complete_sale)) }
                            }
                        }
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
private fun OpenOrdersStrip(
    openOrders: List<Sale>,
    selectedOrderId: SaleId?,
    newOrderEnabled: Boolean,
    onNewOrder: () -> Unit,
    onSelectOrder: (SaleId) -> Unit
) {
    val listState = rememberLazyListState()
    val selectedIndex = openOrders.indexOfFirst { it.saleId == selectedOrderId }

    LaunchedEffect(selectedOrderId, openOrders.size) {
        if (selectedIndex >= 0) {
            // New Order occupies index zero, so an order's strip index is offset by one.
            val targetIndex = selectedIndex + 1
            val visibleIndices = snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.map { it.index }
            }.first { it.isNotEmpty() }
            if (targetIndex !in visibleIndices) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "new-order") {
                Button(
                    onClick = onNewOrder,
                    enabled = newOrderEnabled,
                    modifier = Modifier.width(144.dp).fillMaxHeight(),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.orders_new_order),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            items(openOrders, key = { it.saleId.value }) { order ->
                val isSelected = selectedOrderId == order.saleId
                TerminalCard(
                    onClick = { onSelectOrder(order.saleId) },
                    selected = isSelected,
                    modifier = Modifier.width(148.dp).fillMaxHeight()
                ) {
                    val label = order.tableLabel?.ifBlank { null }
                        ?: order.saleId.value.takeLast(6).uppercase()
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
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
                )
                Spacer(Modifier.weight(1f))
                CompactQuantityControl(
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
            .width(96.dp)
    ) {
        val modes = listOf(PricingMode.CASH, PricingMode.TRANSFER)
        modes.forEach { mode ->
            val isSelected = selectedMode == mode
            val description = when (mode) {
                PricingMode.CASH -> stringResource(R.string.cd_pricing_mode_cash)
                PricingMode.TRANSFER -> stringResource(R.string.cd_pricing_mode_transfer)
            }
            val icon = when (mode) {
                PricingMode.CASH -> Icons.Outlined.Payments
                PricingMode.TRANSFER -> Icons.Outlined.AccountBalance
            }
            
            IconButton(
                onClick = { onModeSelected(mode) },
                enabled = enabled,
                modifier = Modifier.size(48.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(34.dp)
                ) { Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = description,
                        modifier = Modifier.size(19.dp)
                    )
                } }
            }
        }
    }
}

@Composable
private fun CompactQuantityControl(
    quantity: BigDecimal,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    enabled: Boolean
) {
    Box(modifier = Modifier.size(width = 116.dp, height = 44.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {}
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease, enabled = enabled && quantity > BigDecimal.ONE, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Remove, stringResource(R.string.cd_decrease), modifier = Modifier.size(17.dp))
            }
            Text(
                quantity.stripTrailingZeros().toPlainString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onIncrease, enabled = enabled, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_increase), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun CompactModeTotal(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
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
