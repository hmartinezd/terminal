package com.venkoi.terminal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.PricingMode
import com.venkoi.terminal.domain.model.Sale
import com.venkoi.terminal.domain.model.SaleLine
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.domain.repository.VoidResult
import com.venkoi.terminal.domain.service.OrderTotals
import com.venkoi.terminal.ui.components.StatusBadge
import com.venkoi.terminal.ui.theme.TerminalStatusCompleted
import com.venkoi.terminal.ui.theme.TerminalStatusVoided
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter
import com.venkoi.terminal.ui.util.TerminalDateFormatter
import java.math.BigDecimal
import java.time.ZoneId

@Composable
fun HistoryDetailScreen(sale: Sale, timezone: ZoneId, locale: java.util.Locale, viewModel: HistoryViewModel, onPrint: (List<SaleLine>, OrderTotals) -> Unit) {
    val lines by viewModel.selectedSaleLines.collectAsState()
    val totals by viewModel.selectedSaleTotals.collectAsState()
    val isVoiding by viewModel.isVoiding.collectAsState()
    val voidResult by viewModel.voidResult.collectAsState()
    val voidFailed by viewModel.voidFailed.collectAsState()
    var showVoidDialog by remember { mutableStateOf(false) }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text(stringResource(R.string.history_void_sale) + "?") },
            text = { Text(stringResource(R.string.history_void_message)) },
            confirmButton = { TextButton(onClick = { viewModel.voidSale(sale.saleId); showVoidDialog = false }) { Text(stringResource(R.string.history_void_sale), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showVoidDialog = false }) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        HistoryDetailHeader(sale, timezone, locale, isVoiding, totals != null, { totals?.let { onPrint(lines, it) } }, { showVoidDialog = true })
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            InfoLabelValue(stringResource(R.string.history_business_date), sale.businessDate?.let { TerminalDateFormatter.formatDate(it, locale) } ?: stringResource(R.string.common_not_available))
            InfoLabelValue(stringResource(R.string.history_sale_id), sale.saleId.value.takeLast(8).uppercase())
        }
        voidResult?.let { result ->
            if (result !is VoidResult.Success && result !is VoidResult.AlreadyVoided) Text(result.toLocalizedMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (voidFailed) Text(stringResource(R.string.error_void_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        HistoryLineHeader()
        LazyColumn(Modifier.weight(1f)) {
            items(lines, key = { it.lineId.value }) { line -> HistoryLineItemRow(line, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale) }
        }
        totals?.let { HistoryTotals(it, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale) }
    }
}

@Composable
private fun HistoryDetailHeader(sale: Sale, timezone: ZoneId, locale: java.util.Locale, isVoiding: Boolean, canPrint: Boolean, onPrint: () -> Unit, onVoid: () -> Unit) {
    val statusText = if (sale.status == SaleStatus.VOIDED) stringResource(R.string.history_status_voided) else stringResource(R.string.history_status_completed)
    val statusColor = if (sale.status == SaleStatus.VOIDED) TerminalStatusVoided else TerminalStatusCompleted
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(sale.tableLabel?.ifBlank { null } ?: sale.saleId.value.takeLast(8).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                StatusBadge(statusText, statusColor.copy(alpha = 0.1f), statusColor)
            }
            Text(TerminalDateFormatter.formatDateTime(sale.completedAtUtc ?: sale.openedAtUtc, timezone, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrint, enabled = canPrint) {
                Icon(Icons.Default.Print, contentDescription = stringResource(R.string.reports_print_pdf), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.reports_print_pdf))
            }
            if (sale.status == SaleStatus.COMPLETED) {
                OutlinedButton(onClick = onVoid, enabled = !isVoiding, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
                    if (isVoiding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error) else Text(stringResource(R.string.history_void_sale))
                }
            }
        }
    }
}

@Composable
fun InfoLabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryLineHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        HeaderText(R.string.history_column_item, 2.6f)
        HeaderText(R.string.history_column_qty, .55f)
        HeaderText(R.string.history_column_mode, .9f)
        HeaderText(R.string.totals_grand_total, 1f)
    }
    HorizontalDivider()
}

@Composable
private fun RowScope.HeaderText(id: Int, weight: Float) = Text(stringResource(id).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(weight))

@Composable
fun HistoryLineItemRow(line: SaleLine, currencyCode: String, scale: Int, locale: java.util.Locale) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(2.6f).padding(end = 8.dp)) {
            Text(line.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line.cashDiscountApplied) Text(stringResource(R.string.pricing_mode_cash_discount, line.cashDiscountPercent.stripTrailingZeros().toPlainString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        }
        Text(line.quantity.stripTrailingZeros().toPlainString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.55f))
        Text(if (line.pricingMode == PricingMode.CASH) stringResource(R.string.pricing_mode_cash) else stringResource(R.string.pricing_mode_transfer), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(.9f).padding(end = 8.dp))
        Text(HistoryMoneyFormatter.format(line.lineTotal, currencyCode, scale, locale), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HistoryTotals(t: OrderTotals, currencyCode: String, scale: Int, locale: java.util.Locale) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 10.dp)) {
        TotalRow(stringResource(R.string.totals_regular_subtotal), HistoryMoneyFormatter.format(t.regularSubtotal, currencyCode, scale, locale))
        if (t.cashDiscounts.amount > BigDecimal.ZERO) TotalRow(stringResource(R.string.totals_cash_discounts), "-${HistoryMoneyFormatter.format(t.cashDiscounts, currencyCode, scale, locale)}", color = MaterialTheme.colorScheme.secondary)
        TotalRow(stringResource(R.string.totals_cash_total), HistoryMoneyFormatter.format(t.cashTotal, currencyCode, scale, locale))
        TotalRow(stringResource(R.string.totals_transfer_total), HistoryMoneyFormatter.format(t.transferTotal, currencyCode, scale, locale))
        HorizontalDivider(Modifier.padding(vertical = 5.dp))
        TotalRow(stringResource(R.string.totals_grand_total), HistoryMoneyFormatter.format(t.grandTotal, currencyCode, scale, locale), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun VoidResult.toLocalizedMessage(): String = when (this) {
    VoidResult.Success, VoidResult.AlreadyVoided -> stringResource(R.string.history_sale_voided)
    VoidResult.NotCompleted -> stringResource(R.string.error_void_not_completed)
    VoidResult.NotFound -> stringResource(R.string.error_order_not_found)
}
