package com.venkoi.terminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venkoi.terminal.R
import com.venkoi.terminal.domain.model.DailyMoneyCurrencySection
import com.venkoi.terminal.domain.model.DailyMoneyReport
import com.venkoi.terminal.domain.model.ProductReport
import com.venkoi.terminal.domain.model.ProductReportCurrencySection
import com.venkoi.terminal.domain.model.ProductReportRow
import com.venkoi.terminal.ui.components.TerminalCard
import com.venkoi.terminal.ui.util.HistoryMoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.venkoi.terminal.ui.print.MoneyReportPrintContentBuilder
import com.venkoi.terminal.ui.print.ProductReportPrintContentBuilder
import com.venkoi.terminal.ui.print.TerminalPrintManager
import com.venkoi.terminal.ui.print.terminalPrintLabels

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val dailyMoneyReport by viewModel.dailyMoneyReport.collectAsState()
    val productReport by viewModel.productReport.collectAsState()
    val restaurant by viewModel.restaurantConfiguration.collectAsState()
    val terminal by viewModel.terminalConfiguration.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ReportsHeader(
            selectedDate = selectedDate,
            locale = locale,
            onPreviousDay = viewModel::onPreviousDay,
            onNextDay = viewModel::onNextDay,
            onToday = viewModel::onToday,
            printEnabled = when (selectedTab) {
                ReportTab.MONEY -> dailyMoneyReport?.currencySections?.isNotEmpty() == true
                ReportTab.PRODUCTS -> productReport?.currencySections?.any { it.rows.isNotEmpty() } == true
            },
            onPrint = {
                val restaurantConfig = restaurant ?: return@ReportsHeader
                val terminalName = terminal?.terminalName?.takeIf(String::isNotBlank)
                    ?: terminal?.terminalId?.value.orEmpty()
                val labels = context.terminalPrintLabels()
                val document = when (selectedTab) {
                    ReportTab.MONEY -> dailyMoneyReport?.let {
                        MoneyReportPrintContentBuilder.build(it, restaurantConfig.restaurantName, terminalName, viewModel.now(), restaurantConfig.timezone, locale, labels)
                    }
                    ReportTab.PRODUCTS -> productReport?.let {
                        ProductReportPrintContentBuilder.build(it, restaurantConfig.restaurantName, terminalName, viewModel.now(), restaurantConfig.timezone, locale, labels)
                    }
                }
                document?.let { TerminalPrintManager.print(context, it) }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == ReportTab.MONEY,
                onClick = { viewModel.onTabSelected(ReportTab.MONEY) },
                text = { Text(stringResource(R.string.reports_money)) }
            )
            Tab(
                selected = selectedTab == ReportTab.PRODUCTS,
                onClick = { viewModel.onTabSelected(ReportTab.PRODUCTS) },
                text = { Text(stringResource(R.string.reports_products)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            ReportTab.MONEY -> {
                MoneyReportContent(dailyMoneyReport, locale)
            }
            ReportTab.PRODUCTS -> {
                ProductReportContent(productReport, locale)
            }
        }
    }
}

@Composable
fun ReportsHeader(
    selectedDate: LocalDate?,
    locale: Locale,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onPrint: () -> Unit,
    printEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.reports_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (selectedDate != null) {
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousDay) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.reports_previous_day))
            }
            OutlinedButton(onClick = onToday) {
                Icon(Icons.Default.Today, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.reports_today))
            }
            IconButton(onClick = onNextDay) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.reports_next_day))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onPrint, enabled = printEnabled) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reports_print_pdf))
            }
        }
    }
}

@Composable
fun MoneyReportContent(report: DailyMoneyReport?, locale: Locale) {
    if (report == null || report.currencySections.isEmpty()) {
        EmptyReportState(stringResource(R.string.reports_no_sales))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(report.currencySections) { section ->
                MoneyCurrencySectionView(section, locale)
            }
        }
    }
}

@Composable
fun MoneyCurrencySectionView(section: DailyMoneyCurrencySection, locale: Locale) {
    Column {
        if (section.currencyCode.isNotEmpty()) {
            Text(
                text = section.currencyCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            TerminalCard(onClick = {}, modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_net_sales), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = HistoryMoneyFormatter.format(section.grandTotal, section.currencyCode, section.currencyScale, locale),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = stringResource(R.string.reports_cash),
                value = HistoryMoneyFormatter.format(section.cashTotal, section.currencyCode, section.currencyScale, locale),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.reports_transfer),
                value = HistoryMoneyFormatter.format(section.transferTotal, section.currencyCode, section.currencyScale, locale),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalCard(onClick = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AuditRow(stringResource(R.string.reports_valid_sales), section.validSaleCount.toString())
                AuditRow(stringResource(R.string.reports_voided_sales), section.voidedSaleCount.toString())
                AuditRow(
                    stringResource(R.string.reports_cash_discounts),
                    HistoryMoneyFormatter.format(section.cashDiscounts, section.currencyCode, section.currencyScale, locale)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AuditRow(
                    stringResource(R.string.reports_voided_amount),
                    HistoryMoneyFormatter.format(section.voidedAmount, section.currencyCode, section.currencyScale, locale),
                    isWarning = section.voidedAmount.amount > java.math.BigDecimal.ZERO
                )
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    TerminalCard(onClick = {}, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AuditRow(label: String, value: String, isWarning: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProductReportContent(report: ProductReport?, locale: Locale) {
    if (report == null || report.currencySections.isEmpty()) {
        EmptyReportState(stringResource(R.string.reports_no_products))
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(report.currencySections) { section ->
                ProductCurrencySectionView(section, locale)
            }
        }
    }
}

@Composable
fun ProductCurrencySectionView(section: ProductReportCurrencySection, locale: Locale) {
    Column {
        if (section.currencyCode.isNotEmpty()) {
            Text(
                text = section.currencyCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        TerminalCard(onClick = {}) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.reports_product), modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.reports_quantity), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.reports_amount), modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                section.rows.forEach { row ->
                    ProductRowView(row, section.currencyCode, section.currencyScale, locale)
                }
            }
        }
    }
}

@Composable
fun ProductRowView(row: ProductReportRow, currencyCode: String, currencyScale: Int, locale: Locale) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.itemNameSnapshot, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyLarge)
        Text(
            row.quantity.toPlainString(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            HistoryMoneyFormatter.format(row.amount, currencyCode, currencyScale, locale),
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyReportState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
