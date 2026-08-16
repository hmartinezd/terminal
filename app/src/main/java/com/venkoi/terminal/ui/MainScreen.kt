package com.venkoi.terminal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.venkoi.terminal.R

sealed class Screen(@StringRes val titleRes: Int, val icon: ImageVector) {
    object Orders : Screen(R.string.nav_orders, Icons.Default.ListAlt)
    object History : Screen(R.string.nav_history, Icons.Default.History)
    object Reports : Screen(R.string.nav_reports, Icons.Default.BarChart)
    object Settings : Screen(R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Orders) }
    val screens = listOf(Screen.Orders, Screen.History, Screen.Reports, Screen.Settings)

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail for Tablet/Landscape
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            header = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
            },
            modifier = Modifier.fillMaxHeight()
        ) {
            screens.forEach { screen ->
                val title = stringResource(screen.titleRes)
                NavigationRailItem(
                    selected = currentScreen == screen,
                    onClick = { currentScreen = screen },
                    icon = { Icon(screen.icon, contentDescription = title) },
                    label = { 
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium
                        ) 
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopStart
        ) {
            when (currentScreen) {
                Screen.Orders -> OrdersScreen()
                Screen.History -> HistoryScreen()
                Screen.Settings -> SettingsScreen()
                Screen.Reports -> ReportsScreen(viewModel = hiltViewModel())
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(currentScreen.titleRes),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.common_placeholder),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
