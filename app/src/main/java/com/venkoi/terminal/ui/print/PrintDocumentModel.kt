package com.venkoi.terminal.ui.print

data class PrintDocumentModel(
    val jobName: String,
    val lines: List<PrintLine>
)

data class PrintLine(
    val text: String,
    val emphasis: PrintEmphasis = PrintEmphasis.NORMAL
)

enum class PrintEmphasis { NORMAL, HEADING, STRONG, VOIDED }

data class PrintLabels(
    val businessDate: String,
    val generatedAt: String,
    val terminal: String,
    val currency: String,
    val validSales: String,
    val voidedSales: String,
    val cash: String,
    val transfer: String,
    val netSales: String,
    val cashDiscounts: String,
    val voidedAmount: String,
    val product: String,
    val quantity: String,
    val amount: String,
    val status: String,
    val completedAt: String,
    val voidedAt: String,
    val table: String,
    val pricingMode: String,
    val unitPrice: String,
    val lineTotal: String,
    val grandTotal: String,
    val completed: String,
    val voided: String
)
