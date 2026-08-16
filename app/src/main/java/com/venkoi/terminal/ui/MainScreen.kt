package com.venkoi.terminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.ui.OrdersScreen
import com.venkoi.terminal.ui.SettingsScreen

sealed class Screen(val title: String, val icon: ImageVector) {
    object Orders : Screen("Orders", Icons.Default.ListAlt)
    object History : Screen("History", Icons.Default.History)
    object Reports : Screen("Reports", Icons.Default.BarChart)
    object Settings : Screen("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Orders) }
    val screens = listOf(Screen.Orders, Screen.History, Screen.Reports, Screen.Settings)

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail for Tablet/Landscape
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(Modifier.weight(1f))
            screens.forEach { screen ->
                NavigationRailItem(
                    selected = currentScreen == screen,
                    onClick = { currentScreen = screen },
                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                    label = { Text(screen.title) }
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            when (currentScreen) {
                Screen.Orders -> OrdersScreen()
                Screen.History -> HistoryScreen()
                Screen.Settings -> SettingsScreen()
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentScreen.title,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Placeholder for ${currentScreen.title} functionality",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
