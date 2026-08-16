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
import com.venkoi.terminal.ui.util.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.data.file.DocumentWriteResult
import com.venkoi.terminal.data.file.SalesBatchDocumentWriter
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import com.venkoi.terminal.domain.model.PreparedSalesExport
import com.venkoi.terminal.domain.model.SaleExportSummary
import com.venkoi.terminal.domain.repository.SalesExportRepository
import com.venkoi.terminal.domain.service.BuildSalesBatch
import com.venkoi.terminal.domain.service.ResolveCurrentReportBusinessDate
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModel @Inject constructor(
    terminalRepository: TerminalConfigurationRepository,
    menuRepository: MenuRepository,
    private val importService: MenuImportService,
    private val documentReader: DocumentReader,
    private val salesExportRepository: SalesExportRepository,
    private val buildSalesBatch: BuildSalesBatch,
    private val documentWriter: SalesBatchDocumentWriter,
    private val resolveCurrentReportBusinessDate: ResolveCurrentReportBusinessDate,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val json: Json
) : ViewModel() {

    val terminalConfig = terminalRepository.observeConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val restaurantConfig = menuRepository.observeRestaurantConfiguration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val publishedMenu = menuRepository.observePublishedMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val exportSummary = terminalConfig.flatMapLatest { config ->
        if (config == null) flowOf(SaleExportSummary()) else salesExportRepository.observeSummary(config.terminalId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SaleExportSummary())

    var selectedBusinessDate by mutableStateOf<LocalDate?>(null)
        private set

    var pendingDocument by mutableStateOf<PreparedSalesExport?>(null)
        private set

    var exportMessage by mutableStateOf<ExportMessage?>(null)
        private set

    var isExporting by mutableStateOf(false)
        private set

    private val _currentLanguageCode = MutableStateFlow(LocaleManager.getCurrentLanguageCode())
    val currentLanguageCode = _currentLanguageCode.asStateFlow()

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

    fun setLanguage(languageCode: String?) {
        LocaleManager.setLocale(languageCode)
        _currentLanguageCode.value = languageCode
    }

    fun setBusinessDate(date: LocalDate) { selectedBusinessDate = date }

    fun ensureDefaultBusinessDate() {
        if (selectedBusinessDate == null) restaurantConfig.value?.let {
            selectedBusinessDate = resolveCurrentReportBusinessDate.resolve(it)
        }
    }

    fun preparePendingExport() = prepareExport(pending = true)

    fun prepareDayExport() = prepareExport(pending = false)

    private fun prepareExport(pending: Boolean) {
        viewModelScope.launch {
            isExporting = true
            exportMessage = null
            try {
                val terminal = terminalConfig.value ?: error("Terminal is not configured")
                val restaurant = restaurantConfig.value ?: error("Restaurant is not configured")
                val date = selectedBusinessDate ?: resolveCurrentReportBusinessDate.resolve(restaurant).also { selectedBusinessDate = it }
                val sales = if (pending) salesExportRepository.getPendingChanges(terminal.terminalId)
                    else salesExportRepository.getSalesForDay(terminal.terminalId, date)
                if (sales.isEmpty()) {
                    exportMessage = if (pending) ExportMessage.NothingPending else ExportMessage.NoSalesForDay
                    return@launch
                }
                val exportedAt = clock.now()
                val batchId = idGenerator.nextId()
                val batch = buildSalesBatch(
                    terminal.restaurantId.value, terminal.terminalId.value, batchId, exportedAt, sales
                )
                val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC).format(exportedAt)
                pendingDocument = PreparedSalesExport(
                    json = json.encodeToString(batch),
                    batchId = batchId,
                    exportedAtUtc = exportedAt,
                    revisions = batch.sales.map { ExportedSaleRevision(com.venkoi.terminal.core.SaleId(it.saleId), it.revision) },
                    suggestedFileName = if (pending) "sales_pending_$stamp.json" else "sales_${date}_$stamp.json"
                )
            } catch (_: Exception) {
                exportMessage = ExportMessage.Failed
            } finally {
                isExporting = false
            }
        }
    }

    fun onExportDocumentResult(uri: Uri?) {
        val prepared = pendingDocument ?: return
        pendingDocument = null
        if (uri == null) return
        viewModelScope.launch {
            isExporting = true
            when (withContext(Dispatchers.IO) { documentWriter.write(uri, prepared.json) }) {
                DocumentWriteResult.Success -> try {
                    salesExportRepository.markExported(prepared.revisions, prepared.exportedAtUtc, prepared.batchId)
                    exportMessage = ExportMessage.Success(prepared.revisions.size)
                } catch (_: Exception) {
                    exportMessage = ExportMessage.BookkeepingFailed
                }
                is DocumentWriteResult.Failure -> exportMessage = ExportMessage.Failed
            }
            isExporting = false
        }
    }

    fun consumeExportMessage() { exportMessage = null }
}

sealed interface ExportMessage {
    data object NothingPending : ExportMessage
    data object NoSalesForDay : ExportMessage
    data object Failed : ExportMessage
    data object BookkeepingFailed : ExportMessage
    data class Success(val count: Int) : ExportMessage
}
