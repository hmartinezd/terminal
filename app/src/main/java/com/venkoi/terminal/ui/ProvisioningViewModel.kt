package com.venkoi.terminal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.venkoi.terminal.core.DocumentReader
import com.venkoi.terminal.core.ReadResult
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
    private val importService: MenuImportService,
    private val documentReader: DocumentReader
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
        terminalName = name.take(MAX_TERMINAL_NAME_LENGTH)
    }

    fun onFileSelected(uri: Uri) {
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch {
            error = null
            try {
                when (val readResult = documentReader.readUri(uri)) {
                is ReadResult.Success -> {
                    val result = importService.parseAndValidate(readResult.content)
                    if (result is MenuPackageImportResult.Success) {
                        currentStep = ProvisioningStep.Review(result)
                    } else if (result is MenuPackageImportResult.Failure) {
                        error = with(importService) { result.toErrorMessage() }
                    }
                }
                is ReadResult.Failure -> {
                    error = readResult.message
                }
                }
            } catch (_: Exception) {
                error = ""
            } finally {
                isProcessing = false
            }
        }
    }

    fun onConfirm() {
        val step = currentStep as? ProvisioningStep.Review ?: return
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch {
            error = null
            try {
                val status = importService.provisionTerminal(terminalName, step.validated)
                if (status is MenuImportStatus.Failure) error = status.message
            } catch (_: Exception) {
                error = ""
            } finally {
                isProcessing = false
            }
        }
    }

    fun onCancelReview() {
        currentStep = ProvisioningStep.SelectFile
        error = null
    }

    private companion object { const val MAX_TERMINAL_NAME_LENGTH = 100 }
}
