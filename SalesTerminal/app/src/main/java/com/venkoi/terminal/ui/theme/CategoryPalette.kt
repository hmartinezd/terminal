package com.venkoi.terminal.ui.theme

import androidx.compose.ui.graphics.Color

data class CategoryVisualStyle(
    val background: Color,
    val accent: Color,
    val selectedContent: Color = TerminalOnPrimary
)

object CategoryPalette {
    val fallback = CategoryVisualStyle(Color(0xFFF1F3F5), Color(0xFF6B7280))
    private val styles = listOf(
        CategoryVisualStyle(Color(0xFFEAF2FF), Color(0xFF5B8DEF)),
        CategoryVisualStyle(Color(0xFFEAF8F2), Color(0xFF5FAE8B)),
        CategoryVisualStyle(Color(0xFFFFF7DE), Color(0xFFD5A63A)),
        CategoryVisualStyle(Color(0xFFFFF0E7), Color(0xFFD8875F)),
        CategoryVisualStyle(Color(0xFFF1EDFF), Color(0xFF8B75C9)),
        CategoryVisualStyle(Color(0xFFFDECF1), Color(0xFFC97B91)),
        CategoryVisualStyle(Color(0xFFE8F7F7), Color(0xFF5AA6A6)),
        CategoryVisualStyle(Color(0xFFF0F6E8), Color(0xFF7F9E60))
    )

    fun resolve(categoryIdsInDisplayOrder: List<String>): Map<String, CategoryVisualStyle> =
        categoryIdsInDisplayOrder.distinct().mapIndexed { index, id ->
            id to styles[index % styles.size]
        }.toMap()
}
