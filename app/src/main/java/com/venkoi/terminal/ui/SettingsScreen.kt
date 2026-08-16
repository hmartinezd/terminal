package com.venkoi.terminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>()
) {
    val terminalConfig by viewModel.terminalConfig.collectAsState()
    val restaurantConfig by viewModel.restaurantConfig.collectAsState()
    val publishedMenu by viewModel.publishedMenu.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImportMenu(it) }
    }

    LaunchedEffect(viewModel.showImportSuccess) {
        if (viewModel.showImportSuccess) {
            snackbarHostState.showSnackbar("Menu imported successfully.")
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            // Terminal Info
            SettingsSection(title = "Terminal Configuration") {
                InfoRow("Terminal Name", terminalConfig?.terminalName ?: "N/A")
                InfoRow("Terminal ID", terminalConfig?.terminalId?.value ?: "N/A")
            }

            // Restaurant Info
            SettingsSection(title = "Restaurant") {
                InfoRow("Name", restaurantConfig?.restaurantName ?: "N/A")
                InfoRow("Restaurant ID", restaurantConfig?.restaurantId ?: "N/A")
                InfoRow("Currency", restaurantConfig?.currency?.currencyCode ?: "N/A")
                InfoRow("Timezone", restaurantConfig?.timezone?.id ?: "N/A")
                InfoRow("Business Cutoff", restaurantConfig?.businessDayCutoff?.toString() ?: "N/A")
            }

            // Menu Info
            SettingsSection(title = "Published Menu") {
                InfoRow("Menu ID", publishedMenu?.menuId ?: "N/A")
                InfoRow("Publication Revision", publishedMenu?.publicationRevision?.toString() ?: "N/A")
                InfoRow("Published At (UTC)", publishedMenu?.publishedAtUtc?.toString() ?: "N/A")
                InfoRow("Imported At", publishedMenu?.importTimestamp?.toString() ?: "N/A")
            }

            Button(
                onClick = { filePickerLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isImporting
            ) {
                if (viewModel.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Import New Menu")
                }
            }

            viewModel.importError?.let {
                AlertDialog(
                    onDismissRequest = { viewModel.clearImportError() },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearImportError() }) {
                            Text("OK")
                        }
                    },
                    title = { Text("Import Error") },
                    text = { Text(it) }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
