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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.SaleStatus
import com.venkoi.terminal.ui.components.StatusBadge
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.theme.TerminalStatusCompleted
import com.venkoi.terminal.ui.theme.TerminalStatusVoided
import java.time.ZoneId
import java.time.LocalDate
import com.venkoi.terminal.ui.util.TerminalDateFormatter
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
                .width(400.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_history).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
                HistoryColumnHeader()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(historyPresentationEntries(sales), key = { it.key }) { entry ->
                        when (entry) {
                            is HistoryPresentationEntry.BusinessDateHeader -> BusinessDateHeader(
                                businessDate = entry.businessDate,
                                locale = locale
                            )
                            is HistoryPresentationEntry.SaleRow -> {
                                val item = entry.item
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

internal sealed interface HistoryPresentationEntry {
    val key: String

    data class BusinessDateHeader(
        val businessDate: LocalDate?,
        private val sourceIndex: Int
    ) : HistoryPresentationEntry {
        override val key: String = "business-date-$sourceIndex"
    }

    data class SaleRow(val item: SaleWithTotal) : HistoryPresentationEntry {
        override val key: String = "sale-${item.sale.saleId.value}"
    }
}

internal fun historyPresentationEntries(sales: List<SaleWithTotal>): List<HistoryPresentationEntry> = buildList {
    var previousBusinessDate: LocalDate? = null
    var hasPreviousSale = false
    sales.forEachIndexed { index, item ->
        val businessDate = item.sale.businessDate
        if (!hasPreviousSale || businessDate != previousBusinessDate) {
            add(HistoryPresentationEntry.BusinessDateHeader(businessDate, index))
        }
        add(HistoryPresentationEntry.SaleRow(item))
        previousBusinessDate = businessDate
        hasPreviousSale = true
    }
}

@Composable
private fun BusinessDateHeader(businessDate: LocalDate?, locale: java.util.Locale) {
    val formattedDate = businessDate?.let { TerminalDateFormatter.formatDate(it, locale) }
        ?: stringResource(R.string.common_not_available)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.history_business_date_header, formattedDate),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SaleHistoryItemCard(item: SaleWithTotal, isSelected: Boolean, timezone: ZoneId?, locale: java.util.Locale, onClick: () -> Unit) {
    val sale = item.sale
    val statusText = if (sale.status == SaleStatus.VOIDED) {
        stringResource(R.string.history_status_voided)
    } else {
        stringResource(R.string.history_status_completed)
    }
    val statusColor = if (sale.status == SaleStatus.VOIDED) TerminalStatusVoided else TerminalStatusCompleted
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { selected = isSelected }
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            )
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Column(modifier = Modifier.weight(1.3f)) {
                val label = sale.tableLabel?.ifBlank { null } ?: sale.saleId.value.takeLast(6).uppercase()
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = sale.completedAtUtc?.let {
                    timezone?.let { zone -> TerminalDateFormatter.formatTime(it, zone, locale) }
                } ?: "…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(0.85f)
            )
            Text(
                text = com.venkoi.terminal.ui.util.HistoryMoneyFormatter.format(
                    item.grandTotal, sale.currencyCodeSnapshot, sale.currencyScaleSnapshot, locale
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(0.95f)
            )
            StatusBadge(
                text = statusText,
                containerColor = statusColor.copy(alpha = 0.1f),
                contentColor = statusColor,
                modifier = Modifier.weight(1.25f).padding(start = 6.dp)
            )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HistoryColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.history_column_order), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.3f))
        Text(stringResource(R.string.history_column_closed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.85f))
        Text(stringResource(R.string.history_column_amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
        Text(stringResource(R.string.history_column_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1.25f).padding(start = 6.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
