package com.venkoi.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.ui.AppState
import com.venkoi.terminal.ui.MainScreen
import com.venkoi.terminal.ui.ProvisioningScreen
import com.venkoi.terminal.ui.TerminalViewModel
import com.venkoi.terminal.ui.theme.TerminalTheme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerminalTheme {
                val viewModel = hiltViewModel<TerminalViewModel>()
                val appState by viewModel.appState.collectAsState()

                when (appState) {
                    AppState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    AppState.NeedsProvisioning -> {
                        ProvisioningScreen()
                    }
                    AppState.Ready -> {
                        MainScreen()
                    }
                    is AppState.SetupProblem -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Configuration Problem: ${(appState as AppState.SetupProblem).message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
