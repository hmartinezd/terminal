package com.venkoi.terminal.ui

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
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryDetailScreen(sale: Sale, viewModel: HistoryViewModel) {
    val lines by viewModel.selectedSaleLines.collectAsState()
    val totals by viewModel.selectedSaleTotals.collectAsState()
    
    var showVoidDialog by remember { mutableStateOf(false) }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text("Void Sale?") },
            text = { Text("The original sale will remain in History as VOIDED. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.voidSale(sale.saleId)
                    showVoidDialog = false
                }) {
                    Text("Void Sale", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = sale.tableLabel?.ifBlank { "No Label" } ?: "No Label",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "ID: ${sale.saleId.value}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (sale.status == SaleStatus.COMPLETED) {
                Button(
                    onClick = { showVoidDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Void Sale")
                }
            } else if (sale.status == SaleStatus.VOIDED) {
                Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text("VOIDED", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Opened: ${dateTimeFormatter.format(sale.openedAtUtc)}", style = MaterialTheme.typography.bodySmall)
                sale.completedAtUtc?.let { Text("Completed: ${dateTimeFormatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
                sale.voidedAtUtc?.let { Text("Voided: ${dateTimeFormatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
            }
            Text("Date: ${sale.businessDate}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lines) { line ->
                HistoryLineItem(line)
            }
        }
        
        totals?.let { t ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TotalRow("Regular Subtotal", t.regularSubtotal.toString())
                if (t.cashDiscounts.amount > java.math.BigDecimal.ZERO) {
                    TotalRow("Cash Discounts", "-${t.cashDiscounts}", color = Color.Red)
                }
                TotalRow("CASH Total", t.cashTotal.toString())
                TotalRow("TRANSFER Total", t.transferTotal.toString())
                Spacer(Modifier.height(8.dp))
                TotalRow("GRAND TOTAL", t.grandTotal.toString(), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun HistoryLineItem(line: SaleLine) {
    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.itemNameSnapshot, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${line.quantity.stripTrailingZeros().toPlainString()} x ${line.regularUnitPriceSnapshot} (${line.pricingMode})", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${line.lineTotal}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (line.cashDiscountApplied) {
            Text(
                "Discount: ${line.cashDiscountPercent.stripTrailingZeros().toPlainString()}% (-${line.cashDiscountAmount})",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red
            )
        }
    }
}
