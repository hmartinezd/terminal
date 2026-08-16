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
    data class SetupProblem(val message: String) : AppState()
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    terminalRepository: TerminalConfigurationRepository,
    menuRepository: MenuRepository
) : ViewModel() {

    val appState: StateFlow<AppState> = combine(
        terminalRepository.observeConfiguration(),
        menuRepository.observePublishedMenu(),
        menuRepository.observeRestaurantConfiguration()
    ) { config, menu, restaurant ->
        when {
            config == null || menu == null || restaurant == null -> {
                AppState.NeedsProvisioning
            }
            config.restaurantId.value != restaurant.restaurantId -> {
                AppState.SetupProblem("Terminal restaurant ID mismatch. Expected ${config.restaurantId.value}, found ${restaurant.restaurantId}.")
            }
            else -> {
                AppState.Ready
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppState.Loading
    )
}
