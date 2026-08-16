package com.venkoi.terminal.ui.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintLayoutPlannerTest {
    private val measurer = PrintTextMeasurer { text, _ -> text.length.toFloat() }
    private val heights = PrintLineHeightProvider { 1f }

    @Test fun `long product name wraps completely and preserves following row`() {
        val name = "Extraordinary family-size sandwich with avocado tomatoes onions and averylongunbrokentoken"
        val document = PrintDocumentModel("Products", listOf(PrintLine(name), PrintLine("Following product | 1 | 4.00 USD")))
        val plan = PrintLayoutPlanner.plan(document, 24f, 20f, measurer, heights)
        val rendered = plan.pages.flatMap { it.lines }.map { it.text }
        assertTrue(rendered.size > 2)
        val expected = document.lines.joinToString("") { it.text }.filterNot(Char::isWhitespace)
        assertEquals(expected, rendered.joinToString("").filterNot(Char::isWhitespace))
        assertTrue(rendered.joinToString(" ").contains("Following product"))
    }

    @Test fun `multi-page product layout retains first middle final rows in order`() {
        val rows = (1..75).map { PrintLine("Product $it | 1 | 1.00 USD") }
        val plan = PrintLayoutPlanner.plan(PrintDocumentModel("Products", rows), 80f, 10f, measurer, heights)
        val rendered = plan.pages.flatMap { it.lines }.map { it.text }
        assertTrue(plan.pages.size > 1)
        assertEquals(rows.map { it.text }, rendered)
        assertTrue(rendered.contains("Product 1 | 1 | 1.00 USD"))
        assertTrue(rendered.contains("Product 38 | 1 | 1.00 USD"))
        assertTrue(rendered.contains("Product 75 | 1 | 1.00 USD"))
    }
}
