package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.domain.model.MenuItem
import com.venkoi.terminal.domain.model.OpenOrderLine
import com.venkoi.terminal.domain.model.PricingMode
import java.math.BigDecimal

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val openOrders by viewModel.openOrders.collectAsState()
    val selectedOrderId by viewModel.selectedOrderId.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val currentLines by viewModel.currentOrderLines.collectAsState()
    val totals by viewModel.currentOrderTotals.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel: Open Orders
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            Text("OPEN ORDERS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(openOrders) { order ->
                    ListItem(
                        headlineContent = { Text(order.tableLabel?.ifBlank { null } ?: order.saleId.value.takeLast(8)) },
                        supportingContent = { if (!order.tableLabel.isNullOrBlank()) Text(order.saleId.value.takeLast(8)) },
                        modifier = Modifier.clickable { viewModel.selectOrder(order.saleId) },
                        colors = if (selectedOrderId == order.saleId) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors()
                    )
                }
            }
            Button(
                onClick = { viewModel.createOrder() },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Order")
            }
        }

        VerticalDivider()

        // Center Panel: Menu
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Text("MENU", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(menuItems) { item ->
                    Card(
                        onClick = { viewModel.addItemToCurrentOrder(item) },
                        modifier = Modifier.height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.regularPrice}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        VerticalDivider()

        // Right Panel: Current Order
        Column(
            modifier = Modifier
                .width(350.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            selectedOrder?.let { order ->
                var tableLabel by remember(order.saleId) { mutableStateOf(order.tableLabel ?: "") }
                
                TextField(
                    value = tableLabel,
                    onValueChange = { 
                        tableLabel = it
                        viewModel.updateTableLabel(it)
                    },
                    label = { Text("Table/Order Label") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(currentLines) { line ->
                        OrderLineItem(
                            line = line,
                            onUpdateQuantity = { viewModel.updateQuantity(line.lineId, it) },
                            onChangePricingMode = { viewModel.changePricingMode(line.lineId, it) },
                            onRemove = { viewModel.removeLine(line.lineId) }
                        )
                    }
                }

                totals?.let { t ->
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        TotalRow("Regular Subtotal", t.regularSubtotal.toString())
                        if (t.cashDiscounts.amount > BigDecimal.ZERO) {
                            TotalRow("Cash Discounts", "-${t.cashDiscounts}", color = Color.Red)
                        }
                        TotalRow("CASH Total", t.cashTotal.toString())
                        TotalRow("TRANSFER Total", t.transferTotal.toString())
                        Spacer(Modifier.height(8.dp))
                        TotalRow("GRAND TOTAL", t.grandTotal.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
                
                Button(
                    onClick = { viewModel.discardOrder(order.saleId) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard Order")
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select or create an order", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun OrderLineItem(
    line: OpenOrderLine,
    onUpdateQuantity: (BigDecimal) -> Unit,
    onChangePricingMode: (PricingMode) -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.itemNameSnapshot, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onUpdateQuantity(line.quantity.subtract(BigDecimal.ONE)) }) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Text(line.quantity.stripTrailingZeros().toPlainString(), modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onUpdateQuantity(line.quantity.add(BigDecimal.ONE)) }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            Text("${line.lineTotal}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PricingModeToggle(
                selectedMode = line.pricingMode,
                onModeSelected = onChangePricingMode
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun PricingModeToggle(
    selectedMode: PricingMode,
    onModeSelected: (PricingMode) -> Unit
) {
    Row {
        FilterChip(
            selected = selectedMode == PricingMode.TRANSFER,
            onClick = { onModeSelected(PricingMode.TRANSFER) },
            label = { Text("TRANSFER") },
            modifier = Modifier.padding(end = 4.dp)
        )
        FilterChip(
            selected = selectedMode == PricingMode.CASH,
            onClick = { onModeSelected(PricingMode.CASH) },
            label = { Text("CASH") }
        )
    }
}

@Composable
fun TotalRow(label: String, value: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(value, style = style, color = color)
    }
}
