package com.venkoi.terminal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.domain.service.MenuImportService
import com.venkoi.terminal.domain.service.MenuImportStatus
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProvisioningStep {
    object SelectFile : ProvisioningStep()
    data class Review(val validated: MenuPackageImportResult.Success) : ProvisioningStep()
}

@HiltViewModel
class ProvisioningViewModel @Inject constructor(
    private val importService: MenuImportService
) : ViewModel() {

    var terminalName by mutableStateOf("")
        private set

    var currentStep by mutableStateOf<ProvisioningStep>(ProvisioningStep.SelectFile)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    fun onTerminalNameChange(name: String) {
        terminalName = name
    }

    fun onFileSelected(jsonContent: String) {
        viewModelScope.launch {
            isProcessing = true
            error = null
            val result = importService.parseAndValidate(jsonContent)
            if (result is MenuPackageImportResult.Success) {
                currentStep = ProvisioningStep.Review(result)
            } else if (result is MenuPackageImportResult.Failure) {
                error = with(importService) { result.toErrorMessage() }
            }
            isProcessing = false
        }
    }

    fun onConfirm() {
        val step = currentStep as? ProvisioningStep.Review ?: return
        viewModelScope.launch {
            isProcessing = true
            error = null
            val status = importService.provisionTerminal(terminalName, step.validated)
            if (status is MenuImportStatus.Failure) {
                error = status.message
            }
            isProcessing = false
        }
    }

    fun onCancelReview() {
        currentStep = ProvisioningStep.SelectFile
        error = null
    }
}
