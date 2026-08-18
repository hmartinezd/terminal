package com.venkoi.terminal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.licensing.LicenseState
import com.venkoi.terminal.ui.theme.TerminalNavigation
import com.venkoi.terminal.ui.theme.TerminalOnNavigation
import kotlinx.coroutines.launch

sealed class Screen(@StringRes val titleRes: Int, val icon: ImageVector) {
    object Orders : Screen(R.string.nav_orders, Icons.Default.ListAlt)
    object History : Screen(R.string.nav_history, Icons.Default.History)
    object Reports : Screen(R.string.nav_reports, Icons.Default.BarChart)
    object Settings : Screen(R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    val licenseViewModel: LicenseStatusViewModel = hiltViewModel()
    val license by licenseViewModel.snapshot.collectAsState()
    val sellingAllowed by licenseViewModel.sellingAllowed.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Orders) }
    val screens = listOf(Screen.Orders, Screen.History, Screen.Reports, Screen.Settings)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = TerminalNavigation,
                drawerContentColor = TerminalOnNavigation,
                modifier = Modifier.width(280.dp)
            ) {
                Text(
                    text = "VENKOI",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                )
                screens.forEach { screen ->
                    val title = stringResource(screen.titleRes)
                    NavigationDrawerItem(
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(screen.icon, contentDescription = title) },
                        label = { Text(title) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedIconColor = TerminalOnNavigation,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedTextColor = TerminalOnNavigation,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            unselectedContainerColor = TerminalNavigation
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val banner = when (license.state) {
                LicenseState.EXPIRING_SOON -> stringResource(R.string.expires_soon)
                LicenseState.GRACE_PERIOD -> stringResource(R.string.subscription_renewal_required)
                LicenseState.CLOCK_ROLLBACK_DETECTED -> stringResource(R.string.device_time_changed)
                else -> if (!sellingAllowed) stringResource(R.string.selling_disabled) else null
            }
            banner?.let {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_open_navigation))
                    }
                    Text(stringResource(currentScreen.titleRes), style = MaterialTheme.typography.titleMedium)
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background),
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
}
