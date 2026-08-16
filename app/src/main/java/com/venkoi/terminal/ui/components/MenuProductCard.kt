package com.venkoi.terminal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class CategoryVisualStyle(val background: Color, val accent: Color)

object CategoryPalette {
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
        categoryIdsInDisplayOrder.distinct().mapIndexed { index, id -> id to styles[index % styles.size] }.toMap()
}

@Composable
fun MenuProductCard(
    name: String,
    price: String,
    categoryStyle: CategoryVisualStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = categoryStyle.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = categoryStyle.background.copy(alpha = 0.45f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, categoryStyle.accent.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
