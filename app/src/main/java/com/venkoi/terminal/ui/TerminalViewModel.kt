package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class AppState {
    object Loading : AppState()
    object NeedsProvisioning : AppState()
    object Ready : AppState()
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    terminalRepository: TerminalConfigurationRepository,
    menuRepository: MenuRepository
) : ViewModel() {

    val appState: StateFlow<AppState> = combine(
        terminalRepository.observeConfiguration(),
        menuRepository.observePublishedMenu()
    ) { config, menu ->
        if (config == null || menu == null) {
            AppState.NeedsProvisioning
        } else {
            AppState.Ready
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppState.Loading
    )
}
