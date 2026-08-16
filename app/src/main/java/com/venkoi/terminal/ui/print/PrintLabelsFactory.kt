package com.venkoi.terminal.ui.print

import android.content.Context
import com.venkoi.terminal.R

fun Context.terminalPrintLabels() = PrintLabels(
    businessDate = getString(R.string.reports_business_date),
    generatedAt = getString(R.string.reports_generated_at),
    terminal = getString(R.string.reports_terminal),
    currency = getString(R.string.settings_currency),
    validSales = getString(R.string.reports_valid_sales),
    voidedSales = getString(R.string.reports_voided_sales),
    cash = getString(R.string.reports_cash),
    transfer = getString(R.string.reports_transfer),
    netSales = getString(R.string.reports_net_sales),
    cashDiscounts = getString(R.string.reports_cash_discounts),
    voidedAmount = getString(R.string.reports_voided_amount),
    product = getString(R.string.reports_product),
    quantity = getString(R.string.reports_quantity),
    amount = getString(R.string.reports_amount),
    status = getString(R.string.print_status),
    completedAt = getString(R.string.print_completed_at),
    voidedAt = getString(R.string.print_voided_at),
    table = getString(R.string.print_table),
    pricingMode = getString(R.string.print_pricing_mode),
    unitPrice = getString(R.string.print_unit_price),
    lineTotal = getString(R.string.print_line_total),
    grandTotal = getString(R.string.totals_grand_total),
    completed = getString(R.string.history_status_completed),
    voided = getString(R.string.history_status_voided),
    dailySalesReport = getString(R.string.print_job_daily_sales),
    productReport = getString(R.string.print_job_product_report),
    sale = getString(R.string.print_job_sale)
)
