package com.venkoi.terminal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TerminalColorScheme = lightColorScheme(
    primary = TerminalPrimary,
    onPrimary = TerminalOnPrimary,
    primaryContainer = TerminalPrimaryContainer,
    onPrimaryContainer = TerminalOnPrimaryContainer,
    secondary = TerminalSecondary,
    onSecondary = TerminalOnSecondary,
    background = TerminalBackground,
    surface = TerminalSurface,
    onSurface = TerminalOnSurface,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TerminalOnSurfaceVariant,
    outline = TerminalOutline,
    outlineVariant = TerminalOutlineVariant,
    error = TerminalError,
    onError = TerminalOnError
)

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
