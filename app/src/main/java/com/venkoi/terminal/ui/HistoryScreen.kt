package com.venkoi.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.ui.components.StatusBadge
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.theme.TerminalStatusCompleted
import com.venkoi.terminal.ui.theme.TerminalStatusVoided
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.venkoi.terminal.ui.print.SalePrintContentBuilder
import com.venkoi.terminal.ui.print.TerminalPrintManager
import com.venkoi.terminal.ui.print.terminalPrintLabels

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val sales by viewModel.historySales.collectAsState()
    val selectedSaleId by viewModel.selectedSaleId.collectAsState()
    val selectedSale by viewModel.selectedSale.collectAsState()
    val timezone by viewModel.restaurantTimezone.collectAsState()
    val restaurant by viewModel.restaurantConfiguration.collectAsState()
    val terminal by viewModel.terminalConfiguration.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Panel: Sale List
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_history).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (sales.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.history_empty_state),
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
                    items(sales) { item ->
                        val isSelected = selectedSaleId == item.sale.saleId
                        SaleHistoryItemCard(
                            item = item,
                            isSelected = isSelected,
                            timezone = timezone,
                            locale = locale,
                            onClick = { viewModel.selectSale(item.sale.saleId) }
                        )
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Right Panel: Detail
        Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.background)) {
            selectedSale?.let { sale ->
                if (timezone != null) {
                    HistoryDetailScreen(
                        sale = sale,
                        timezone = timezone!!,
                        locale = locale,
                        viewModel = viewModel,
                        onPrint = { lines, totals ->
                            val restaurantConfig = restaurant ?: return@HistoryDetailScreen
                            val terminalName = terminal?.terminalName?.takeIf(String::isNotBlank)
                                ?: terminal?.terminalId?.value.orEmpty()
                            TerminalPrintManager.print(
                                context,
                                SalePrintContentBuilder.build(
                                    sale, lines, totals, restaurantConfig.restaurantName, terminalName,
                                    restaurantConfig.timezone, locale,
                                    context.terminalPrintLabels()
                                )
                            )
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_select_sale),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SaleHistoryItemCard(item: SaleWithTotal, isSelected: Boolean, timezone: ZoneId?, locale: java.util.Locale, onClick: () -> Unit) {
    val sale = item.sale
    val formatter = remember(timezone) {
        timezone?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(it) }
    }
    
    TerminalCard(
        onClick = onClick,
        selected = isSelected,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val label = sale.tableLabel?.ifBlank { null } ?: sale.saleId.value.takeLast(6).uppercase()
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                sale.completedAtUtc?.let { 
                    Text(
                        text = formatter?.format(it) ?: "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(
                        item.grandTotal,
                        sale.currencyCodeSnapshot,
                        sale.currencyScaleSnapshot,
                        locale
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(4.dp))
                
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
            }
        }
    }
}
