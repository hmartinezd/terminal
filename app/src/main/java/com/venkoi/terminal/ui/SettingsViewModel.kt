package com.venkoi.terminal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.venkoi.terminal.core.DocumentReader
import com.venkoi.terminal.core.ReadResult
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import com.venkoi.terminal.domain.service.MenuImportService
import com.venkoi.terminal.domain.service.MenuImportStatus
import com.venkoi.terminal.integration.menu.MenuPackageImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    terminalRepository: TerminalConfigurationRepository,
    menuRepository: MenuRepository,
    private val importService: MenuImportService,
    private val documentReader: DocumentReader
) : ViewModel() {

    val terminalConfig = terminalRepository.observeConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val restaurantConfig = menuRepository.observeRestaurantConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val publishedMenu = menuRepository.observePublishedMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var importError by mutableStateOf<String?>(null)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var showImportSuccess by mutableStateOf(false)
        private set

    fun onImportMenu(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            importError = null
            showImportSuccess = false
            
            when (val readResult = documentReader.readUri(uri)) {
                is ReadResult.Success -> {
                    val parseResult = importService.parseAndValidate(readResult.content)
                    if (parseResult is MenuPackageImportResult.Success) {
                        val status = importService.importMenu(parseResult)
                        if (status is MenuImportStatus.Success) {
                            showImportSuccess = true
                        } else if (status is MenuImportStatus.Failure) {
                            importError = status.message
                        }
                    } else if (parseResult is MenuPackageImportResult.Failure) {
                        importError = with(importService) { parseResult.toErrorMessage() }
                    }
                }
                is ReadResult.Failure -> {
                    importError = readResult.message
                }
            }
            
            isImporting = false
        }
    }

    fun clearImportError() {
        importError = null
    }

    fun dismissSuccess() {
        showImportSuccess = false
    }
}
