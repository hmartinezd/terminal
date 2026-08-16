package com.venkoi.terminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ProvisioningScreen(
    viewModel: ProvisioningViewModel = hiltViewModel<ProvisioningViewModel>()
) {
    // removed context
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Sales Terminal Setup",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            if (viewModel.currentStep is ProvisioningStep.SelectFile) {
                OutlinedTextField(
                    value = viewModel.terminalName,
                    onValueChange = { viewModel.onTerminalNameChange(it) },
                    label = { Text("Terminal Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !viewModel.isProcessing
                )

                Button(
                    onClick = { filePickerLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !viewModel.isProcessing
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Select MenuPackage JSON")
                    }
                }
            } else if (viewModel.currentStep is ProvisioningStep.Review) {
                val validated = (viewModel.currentStep as ProvisioningStep.Review).validated
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Restaurant: ${validated.restaurant.restaurantName}", style = MaterialTheme.typography.titleMedium)
                        Text("Currency: ${validated.restaurant.currency.currencyCode}")
                        Text("Menu Revision: ${validated.menu.publicationRevision}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Categories: ${validated.categories.size}")
                        Text("Menu Items: ${validated.items.size}")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onCancelReview() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = !viewModel.isProcessing
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = { viewModel.onConfirm() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = !viewModel.isProcessing
                    ) {
                        if (viewModel.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Confirm Setup")
                        }
                    }
                }
            }

            viewModel.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
