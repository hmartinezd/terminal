package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val sales by viewModel.historySales.collectAsState()
    val selectedSaleId by viewModel.selectedSaleId.collectAsState()
    val selectedSale by viewModel.selectedSale.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel: Sale List
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            Text("HISTORY", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sales) { sale ->
                    SaleHistoryItem(
                        item = sale,
                        isSelected = selectedSaleId == sale.sale.saleId,
                        onClick = { viewModel.selectSale(sale.sale.saleId) }
                    )
                }
            }
        }

        VerticalDivider()

        // Right Panel: Detail
        Box(modifier = Modifier.weight(1f)) {
            selectedSale?.let { sale ->
                HistoryDetailScreen(sale = sale, viewModel = viewModel)
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a sale to view details", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun SaleHistoryItem(item: SaleWithTotal, isSelected: Boolean, onClick: () -> Unit) {
    val sale = item.sale
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    
    ListItem(
        headlineContent = { Text(sale.tableLabel?.ifBlank { null } ?: sale.saleId.value.takeLast(8)) },
        supportingContent = {
            Column {
                sale.completedAtUtc?.let { Text(formatter.format(it)) }
                Text(
                    text = if (sale.status == SaleStatus.VOIDED) "VOIDED" else "COMPLETED", 
                    color = if (sale.status == SaleStatus.VOIDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        trailingContent = {
            Text(item.grandTotal.toString(), fontWeight = FontWeight.Bold)
        },
        modifier = Modifier.clickable { onClick() },
        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors()
    )
}
