package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.repository.VoidResult
import com.venkoi.terminal.domain.service.OrderTotals
import com.venkoi.terminal.ui.components.StatusBadge
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.theme.TerminalStatusCompleted
import com.venkoi.terminal.ui.theme.TerminalStatusVoided
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryDetailScreen(
    sale: Sale,
    timezone: ZoneId,
    locale: java.util.Locale,
    viewModel: HistoryViewModel,
    onPrint: (List<SaleLine>, OrderTotals) -> Unit
) {
    val lines by viewModel.selectedSaleLines.collectAsState()
    val totals by viewModel.selectedSaleTotals.collectAsState()
    val isVoiding by viewModel.isVoiding.collectAsState()
    val voidResult by viewModel.voidResult.collectAsState()
    
    var showVoidDialog by remember { mutableStateOf(false) }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text(stringResource(R.string.history_void_sale) + "?") },
            text = { Text(stringResource(R.string.history_void_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.voidSale(sale.saleId)
                    showVoidDialog = false
                }) {
                    Text(stringResource(R.string.history_void_sale), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sale.tableLabel?.ifBlank { null } ?: sale.saleId.value.takeLast(8).uppercase(),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val statusText = if (sale.status == SaleStatus.VOIDED) {
                    stringResource(R.string.history_status_voided)
                } else {
                    stringResource(R.string.history_status_completed)
                }
                
                val statusColor = if (sale.status == SaleStatus.VOIDED) {
                    TerminalStatusVoided
                } else {
                    TerminalStatusCompleted
                }
                
                StatusBadge(
                    text = statusText,
                    containerColor = statusColor.copy(alpha = 0.1f),
                    contentColor = statusColor
                )
                
                if (sale.status == SaleStatus.COMPLETED) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showVoidDialog = true },
                        enabled = !isVoiding,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        if (isVoiding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.history_void_sale))
                        }
                    }
                }
                totals?.let { currentTotals ->
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onPrint(lines, currentTotals) }) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reports_print_pdf))
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(timezone)
        
        TerminalCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoLabelValue(stringResource(R.string.history_business_date), sale.businessDate.toString())
                    InfoLabelValue(stringResource(R.string.history_timestamp), dateTimeFormatter.format(sale.completedAtUtc ?: sale.openedAtUtc))
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        voidResult?.let { result ->
            if (result !is VoidResult.Success && result !is VoidResult.AlreadyVoided) {
                Text(
                    text = result.toLocalizedMessage(), 
                    color = MaterialTheme.colorScheme.error, 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                HistoryLineItemCard(line, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale)
            }
        }
        
        totals?.let { t ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(16.dp)
            ) {
                TotalRow(
                    stringResource(R.string.totals_regular_subtotal), 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.regularSubtotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale)
                )
                if (t.cashDiscounts.amount > java.math.BigDecimal.ZERO) {
                    TotalRow(
                        stringResource(R.string.totals_cash_discounts), 
                        "-${com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.cashDiscounts, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale)}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TotalRow(
                    stringResource(R.string.totals_cash_total), 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.cashTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale)
                )
                TotalRow(
                    stringResource(R.string.totals_transfer_total), 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.transferTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TotalRow(
                    stringResource(R.string.totals_grand_total), 
                    com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(t.grandTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun InfoLabelValue(label: String, value: String) {
    Row {
        Text(text = "$label: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryLineItemCard(line: SaleLine, currencyCode: String, scale: Int, locale: java.util.Locale) {
    TerminalCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(line.itemNameSnapshot, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${line.quantity.stripTrailingZeros().toPlainString()} x ${com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(line.regularUnitPriceSnapshot, currencyCode, scale, locale)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(line.lineTotal, currencyCode, scale, locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (line.cashDiscountApplied) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pricing_mode_cash_discount, line.cashDiscountPercent.stripTrailingZeros().toPlainString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VoidResult.toLocalizedMessage(): String {
    return when (this) {
        VoidResult.Success -> stringResource(R.string.history_sale_voided)
        VoidResult.AlreadyVoided -> stringResource(R.string.history_sale_voided)
        VoidResult.NotCompleted -> stringResource(R.string.error_void_not_completed)
        VoidResult.NotFound -> stringResource(R.string.error_order_not_found)
    }
}
