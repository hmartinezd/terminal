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
    private var layoutPlan: PrintLayoutPlan? = null
    private val padding = 16f

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
        attributes = newAttributes
        val pdf = PrintedPdfDocument(context, newAttributes)
        val content = pdf.pageContentRect
        layoutPlan = createLayoutPlan(content.width() - padding * 2, content.height() - padding * 2)
        pdf.close()
        val pages = layoutPlan?.pages?.size ?: 1
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
            val plan = layoutPlan ?: createLayoutPlan(
                pdf.pageContentRect.width() - padding * 2,
                pdf.pageContentRect.height() - padding * 2
            )
            val pageCount = plan.pages.size
            for (pageIndex in 0 until pageCount) {
                if (cancellationSignal.isCanceled) return callback.onWriteCancelled()
                if (!pages.containsPage(pageIndex)) continue
                val page = pdf.startPage(pageIndex)
                val canvas = page.canvas
                var y = pdf.pageContentRect.top + padding
                plan.pages[pageIndex].lines.forEach { line ->
                    val paint = paintFor(line.emphasis)
                    y += -paint.fontMetrics.ascent
                    canvas.drawText(line.text, pdf.pageContentRect.left + padding, y, paint)
                    y += line.height + paint.fontMetrics.ascent
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

    private fun createLayoutPlan(width: Float, height: Float) = PrintLayoutPlanner.plan(
        model,
        width,
        height,
        PrintTextMeasurer { text, emphasis -> paintFor(emphasis).measureText(text) },
        PrintLineHeightProvider { emphasis -> paintFor(emphasis).fontSpacing }
    )

    private fun paintFor(emphasis: PrintEmphasis) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (emphasis == PrintEmphasis.VOIDED) Color.rgb(150, 0, 0) else Color.BLACK
        textSize = when (emphasis) {
            PrintEmphasis.HEADING -> 18f
            PrintEmphasis.STRONG, PrintEmphasis.VOIDED -> 14f
            PrintEmphasis.NORMAL -> 11f
        }
        typeface = if (emphasis == PrintEmphasis.NORMAL) Typeface.MONOSPACE
        else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
}

private fun Array<out PageRange>.containsPage(page: Int) = any { page in it.start..it.end }
