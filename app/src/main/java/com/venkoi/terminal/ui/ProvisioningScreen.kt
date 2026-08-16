package com.venkoi.terminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.ui.components.TerminalCard

@Composable
fun ProvisioningScreen(
    viewModel: ProvisioningViewModel = hiltViewModel<ProvisioningViewModel>()
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = stringResource(R.string.provisioning_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            if (viewModel.currentStep is ProvisioningStep.SelectFile) {
                TerminalCard(onClick = {}) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = viewModel.terminalName,
                            onValueChange = { viewModel.onTerminalNameChange(it) },
                            label = { Text(stringResource(R.string.provisioning_terminal_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true,
                            enabled = !viewModel.isProcessing
                        )

                        Button(
                            onClick = { filePickerLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !viewModel.isProcessing,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (viewModel.isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(stringResource(R.string.provisioning_select_file))
                            }
                        }
                    }
                }
            } else if (viewModel.currentStep is ProvisioningStep.Review) {
                val validated = (viewModel.currentStep as ProvisioningStep.Review).validated
                
                TerminalCard(onClick = {}) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(stringResource(R.string.provisioning_restaurant), validated.restaurant.restaurantName)
                        InfoRow(stringResource(R.string.provisioning_currency), validated.restaurant.currency.currencyCode)
                        InfoRow(stringResource(R.string.provisioning_menu_revision), validated.menu.publicationRevision.toString())
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        InfoRow(stringResource(R.string.provisioning_categories), validated.categories.size.toString())
                        InfoRow(stringResource(R.string.provisioning_menu_items), validated.items.size.toString())
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onCancelReview() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = !viewModel.isProcessing,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.provisioning_back))
                    }
                    Button(
                        onClick = { viewModel.onConfirm() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = !viewModel.isProcessing,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (viewModel.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.provisioning_confirm))
                        }
                    }
                }
            }

            viewModel.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
