package com.venkoi.terminal.ui.print

data class LaidOutPrintLine(
    val text: String,
    val emphasis: PrintEmphasis,
    val height: Float
)

data class PrintLayoutPage(val lines: List<LaidOutPrintLine>)

data class PrintLayoutPlan(val pages: List<PrintLayoutPage>)

fun interface PrintTextMeasurer {
    fun width(text: String, emphasis: PrintEmphasis): Float
}

fun interface PrintLineHeightProvider {
    fun height(emphasis: PrintEmphasis): Float
}

object PrintLayoutPlanner {
    fun plan(
        document: PrintDocumentModel,
        availableWidth: Float,
        availableHeight: Float,
        measurer: PrintTextMeasurer,
        lineHeights: PrintLineHeightProvider
    ): PrintLayoutPlan {
        require(availableWidth > 0f && availableHeight > 0f)
        val rendered = document.lines.flatMap { line ->
            wrap(line.text, line.emphasis, availableWidth, measurer).map {
                LaidOutPrintLine(it, line.emphasis, lineHeights.height(line.emphasis))
            }
        }
        if (rendered.isEmpty()) return PrintLayoutPlan(listOf(PrintLayoutPage(emptyList())))

        val pages = mutableListOf<PrintLayoutPage>()
        var current = mutableListOf<LaidOutPrintLine>()
        var usedHeight = 0f
        rendered.forEach { line ->
            if (current.isNotEmpty() && usedHeight + line.height > availableHeight) {
                pages += PrintLayoutPage(current)
                current = mutableListOf()
                usedHeight = 0f
            }
            current += line
            usedHeight += line.height
        }
        if (current.isNotEmpty()) pages += PrintLayoutPage(current)
        return PrintLayoutPlan(pages)
    }

    private fun wrap(
        text: String,
        emphasis: PrintEmphasis,
        width: Float,
        measurer: PrintTextMeasurer
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (measurer.width(remaining, emphasis) <= width) {
                result += remaining
                break
            }
            var fit = largestFittingPrefix(remaining, emphasis, width, measurer).coerceAtLeast(1)
            val whitespace = remaining.substring(0, fit).indexOfLast { it.isWhitespace() }
            if (whitespace > 0) fit = whitespace
            result += remaining.substring(0, fit).trimEnd()
            remaining = remaining.substring(fit).trimStart()
        }
        return result
    }

    private fun largestFittingPrefix(
        text: String,
        emphasis: PrintEmphasis,
        width: Float,
        measurer: PrintTextMeasurer
    ): Int {
        var low = 1
        var high = text.length
        var best = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (measurer.width(text.substring(0, middle), emphasis) <= width) {
                best = middle
                low = middle + 1
            } else high = middle - 1
        }
        return best
    }
}
