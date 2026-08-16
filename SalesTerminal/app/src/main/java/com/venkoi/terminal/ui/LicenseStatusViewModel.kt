package com.venkoi.terminal.ui

import androidx.lifecycle.ViewModel
import com.venkoi.terminal.licensing.LicenseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LicenseStatusViewModel @Inject constructor(manager: LicenseManager) : ViewModel() {
    val snapshot = manager.snapshot
    val sellingAllowed = manager.sellingAllowed
}
