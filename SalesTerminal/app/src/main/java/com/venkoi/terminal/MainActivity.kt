package com.venkoi.terminal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.venkoi.terminal.R
import com.venkoi.terminal.ui.AppState
import com.venkoi.terminal.ui.MainScreen
import com.venkoi.terminal.ui.ProvisioningScreen
import com.venkoi.terminal.ui.TerminalViewModel
import com.venkoi.terminal.ui.theme.TerminalTheme

import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.venkoi.terminal.licensing.LicenseManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var licenseManager: LicenseManager

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { licenseManager.refresh() }
    }

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
                    AppState.SetupProblem -> {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stringResource(R.string.provisioning_problem_title), style = MaterialTheme.typography.headlineMedium)
                                Text(text = stringResource(R.string.provisioning_problem_message), modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
