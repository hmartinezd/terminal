package com.venkoi.terminal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.*
import com.venkoi.terminal.ui.print.*
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter
import com.venkoi.terminal.ui.util.TerminalDateFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentBusinessDate by viewModel.currentBusinessDate.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val moneyReport by viewModel.dailyMoneyReport.collectAsState()
    val productReport by viewModel.productReport.collectAsState()
    val restaurant by viewModel.restaurantConfiguration.collectAsState()
    val terminal by viewModel.terminalConfiguration.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        ReportsHeader(selectedDate, currentBusinessDate, locale, viewModel::onPreviousDay,
            viewModel::onNextDay, viewModel::onToday, {
                val config = restaurant ?: return@ReportsHeader
                val terminalName = terminal?.terminalName?.takeIf(String::isNotBlank)
                    ?: terminal?.terminalId?.value.orEmpty()
                val labels = context.terminalPrintLabels()
                val document = when (selectedTab) {
                    ReportTab.MONEY -> moneyReport?.let { MoneyReportPrintContentBuilder.build(it, config.restaurantName, terminalName, viewModel.now(), config.timezone, locale, labels) }
                    ReportTab.PRODUCTS -> productReport?.let { ProductReportPrintContentBuilder.build(it, config.restaurantName, terminalName, viewModel.now(), config.timezone, locale, labels) }
                }
                document?.let { TerminalPrintManager.print(context, it) }
            }, when (selectedTab) {
                ReportTab.MONEY -> moneyReport?.currencySections?.isNotEmpty() == true
                ReportTab.PRODUCTS -> productReport?.currencySections?.any { it.rows.isNotEmpty() } == true
            })
        Spacer(Modifier.height(10.dp))
        ReportModeSelector(selectedTab, viewModel::onTabSelected)
        Spacer(Modifier.height(10.dp))
        when (selectedTab) {
            ReportTab.MONEY -> MoneyReportContent(moneyReport, locale)
            ReportTab.PRODUCTS -> ProductReportContent(productReport, locale)
        }
    }
}

@Composable
private fun ReportsHeader(selectedDate: LocalDate?, currentBusinessDate: LocalDate?, locale: Locale,
    onPreviousDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit,
    onPrint: () -> Unit, printEnabled: Boolean) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousDay) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.reports_previous_day)) }
            Text(selectedDate?.let { TerminalDateFormatter.formatDateWithToday(it, currentBusinessDate ?: LocalDate.MIN, locale, stringResource(R.string.reports_today)) }.orEmpty(),
                Modifier.width(210.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onNextDay) { Icon(Icons.Default.ChevronRight, stringResource(R.string.reports_next_day)) }
            OutlinedButton(onClick = onToday, enabled = selectedDate != currentBusinessDate) { Text(stringResource(R.string.reports_today)) }
        }
        Button(onClick = onPrint, enabled = printEnabled) {
            Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reports_print_pdf))
        }
    }
}

@Composable
private fun ReportModeSelector(selected: ReportTab, onSelected: (ReportTab) -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.width(300.dp).padding(3.dp)) {
            ReportModeOption(stringResource(R.string.reports_money), selected == ReportTab.MONEY, Modifier.weight(1f)) { onSelected(ReportTab.MONEY) }
            ReportModeOption(stringResource(R.string.reports_products), selected == ReportTab.PRODUCTS, Modifier.weight(1f)) { onSelected(ReportTab.PRODUCTS) }
        }
    }
}

@Composable
private fun ReportModeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (selected) 2.dp else 0.dp) {
        Text(label, Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun MoneyReportContent(report: DailyMoneyReport?, locale: Locale) {
    if (report == null || report.currencySections.isEmpty()) EmptyReportState(stringResource(R.string.reports_no_sales))
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(report.currencySections, key = { it.currencyCode }) { MoneyCurrencySectionView(it, locale) }
    }
}

@Composable
private fun MoneyCurrencySectionView(section: DailyMoneyCurrencySection, locale: Locale) {
    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            if (section.currencyCode.isNotEmpty()) {
                Text(section.currencyCode, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.fillMaxWidth()) {
                PrimaryMetric(stringResource(R.string.reports_net_sales), HistoryMoneyFormatter.format(section.grandTotal, section.currencyCode, section.currencyScale, locale), true, Modifier.weight(1f))
                PrimaryMetric(stringResource(R.string.reports_cash), HistoryMoneyFormatter.format(section.cashTotal, section.currencyCode, section.currencyScale, locale), false, Modifier.weight(1f))
                PrimaryMetric(stringResource(R.string.reports_transfer), HistoryMoneyFormatter.format(section.transferTotal, section.currencyCode, section.currencyScale, locale), false, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth()) {
                SecondaryMetric(stringResource(R.string.reports_cash_discounts), HistoryMoneyFormatter.format(section.cashDiscounts, section.currencyCode, section.currencyScale, locale), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                SecondaryMetric(stringResource(R.string.reports_valid_sales), section.validSaleCount.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                SecondaryMetric(stringResource(R.string.reports_voided_sales), section.voidedSaleCount.toString(), if (section.voidedSaleCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                SecondaryMetric(stringResource(R.string.reports_voided_amount), HistoryMoneyFormatter.format(section.voidedAmount, section.currencyCode, section.currencyScale, locale), if (section.voidedAmount.amount > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PrimaryMetric(label: String, value: String, primary: Boolean, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, style = if (primary) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryMetric(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Spacer(Modifier.height(3.dp)); Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProductReportContent(report: ProductReport?, locale: Locale) {
    val sections = report?.currencySections.orEmpty().filter { it.rows.isNotEmpty() }
    if (sections.isEmpty()) { EmptyReportState(stringResource(R.string.reports_no_products)); return }
    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        LazyColumn {
            sections.forEach { section ->
                item(key = "currency-${section.currencyCode}") { ProductSectionHeader(section) }
                items(section.rows) { row ->
                    ProductRowView(row, section.currencyCode, section.currencyScale, locale)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProductSectionHeader(section: ProductReportCurrencySection) {
    Column {
        if (section.currencyCode.isNotEmpty()) Text(section.currencyCode, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
            TableHeader(stringResource(R.string.reports_product), Modifier.weight(2.8f), TextAlign.Start)
            TableHeader(stringResource(R.string.reports_quantity), Modifier.weight(.9f), TextAlign.End)
            TableHeader(stringResource(R.string.reports_amount), Modifier.weight(1.3f), TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier, alignment: TextAlign) {
    Text(text.uppercase(), modifier, textAlign = alignment, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
}

@Composable
private fun ProductRowView(row: ProductReportRow, currencyCode: String, currencyScale: Int, locale: Locale) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.itemNameSnapshot, Modifier.weight(2.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(row.quantity.toPlainString(), Modifier.weight(.9f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
        Text(HistoryMoneyFormatter.format(row.amount, currencyCode, currencyScale, locale), Modifier.weight(1.3f), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyReportState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
