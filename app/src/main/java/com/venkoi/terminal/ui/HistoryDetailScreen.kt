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
fun HistoryDetailScreen(sale: Sale, timezone: ZoneId, viewModel: HistoryViewModel) {
    val lines by viewModel.selectedSaleLines.collectAsState()
    val totals by viewModel.selectedSaleTotals.collectAsState()
    val isVoiding by viewModel.isVoiding.collectAsState()
    val voidError by viewModel.voidError.collectAsState()
    val voidSuccess by viewModel.voidSuccess.collectAsState()
    
    var showVoidDialog by remember { mutableStateOf(false) }

    LaunchedEffect(voidError) {
        voidError?.let {
            // In a real app we might show a snackbar
        }
    }

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
                    enabled = !isVoiding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isVoiding) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Void Sale")
                    }
                }
            } else if (sale.status == SaleStatus.VOIDED) {
                Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text("VOIDED", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(timezone)
        
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
        
        voidError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (voidSuccess) {
            Text("Sale voided successfully", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lines) { line ->
                HistoryLineItem(line, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot)
            }
        }
        
        totals?.let { t ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TotalRow(
                    "Regular Subtotal", 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.regularSubtotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot)
                )
                if (t.cashDiscounts.amount > java.math.BigDecimal.ZERO) {
                    TotalRow(
                        "Cash Discounts", 
                        "-${com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.cashDiscounts, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot)}", 
                        color = Color.Red
                    )
                }
                TotalRow(
                    "CASH Total", 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.cashTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot)
                )
                TotalRow(
                    "TRANSFER Total", 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.transferTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot)
                )
                Spacer(Modifier.height(8.dp))
                TotalRow(
                    "GRAND TOTAL", 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.grandTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot), 
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
fun HistoryLineItem(line: SaleLine, currencyCode: String, scale: Int) {
    Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.itemNameSnapshot, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${line.quantity.stripTrailingZeros().toPlainString()} x ${com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(line.regularUnitPriceSnapshot, currencyCode, scale)} (${line.pricingMode})", 
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(line.lineTotal, currencyCode, scale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (line.cashDiscountApplied) {
            Text(
                "Discount: ${line.cashDiscountPercent.stripTrailingZeros().toPlainString()}% (-${com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(line.cashDiscountAmount, currencyCode, scale)})",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red
            )
        }
    }
}
