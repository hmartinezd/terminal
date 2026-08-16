package com.venkoi.terminal.ui.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream

object TerminalPrintManager {
    fun print(context: Context, document: PrintDocumentModel) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(document.jobName, TerminalPrintAdapter(context, document), null)
    }
}

class TerminalPrintAdapter(
    private val context: Context,
    private val model: PrintDocumentModel
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null
    private val linesPerPage = 42
    private val printableLines by lazy {
        model.lines.flatMap { line ->
            if (line.text.isEmpty()) listOf(line)
            else line.text.chunked(100).map { line.copy(text = it) }
        }
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
        attributes = newAttributes
        val pages = maxOf(1, (printableLines.size + linesPerPage - 1) / linesPerPage)
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(model.jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),
            oldAttributes != newAttributes
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal, callback: WriteResultCallback
    ) {
        val attrs = attributes ?: return callback.onWriteFailed("Missing print attributes")
        val pdf = PrintedPdfDocument(context, attrs)
        try {
            val pageCount = maxOf(1, (printableLines.size + linesPerPage - 1) / linesPerPage)
            for (pageIndex in 0 until pageCount) {
                if (cancellationSignal.isCanceled) return callback.onWriteCancelled()
                if (!pages.containsPage(pageIndex)) continue
                val page = pdf.startPage(pageIndex)
                val canvas = page.canvas
                var y = 48f
                printableLines.drop(pageIndex * linesPerPage).take(linesPerPage).forEach { line ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (line.emphasis == PrintEmphasis.VOIDED) Color.rgb(150, 0, 0) else Color.BLACK
                        textSize = when (line.emphasis) { PrintEmphasis.HEADING -> 18f; PrintEmphasis.STRONG, PrintEmphasis.VOIDED -> 14f; else -> 11f }
                        typeface = if (line.emphasis == PrintEmphasis.NORMAL) Typeface.MONOSPACE else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    canvas.drawText(line.text, 42f, y, paint)
                    y += 18f
                }
                pdf.finishPage(page)
            }
            pdf.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(pages)
        } catch (error: Exception) {
            callback.onWriteFailed(error.message)
        } finally {
            pdf.close()
        }
    }
}

private fun Array<out PageRange>.containsPage(page: Int) = any { page in it.start..it.end }
