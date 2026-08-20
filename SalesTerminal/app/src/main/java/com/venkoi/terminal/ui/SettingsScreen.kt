package com.venkoi.terminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.venkoi.terminal.R
import com.venkoi.terminal.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.venkoi.terminal.ui.util.TerminalDateFormatter
import com.venkoi.terminal.licensing.LicenseImportResult
import com.venkoi.terminal.licensing.LicenseState

private enum class SettingsSectionId {
    LANGUAGE,
    SUBSCRIPTION,
    SALES_EXPORT,
    RESTAURANT,
    TERMINAL,
    MENU,
    APP_INFORMATION
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel<SettingsViewModel>()
) {
    val terminalConfig by viewModel.terminalConfig.collectAsState()
    val restaurantConfig by viewModel.restaurantConfig.collectAsState()
    val publishedMenu by viewModel.publishedMenu.collectAsState()
    val currentLanguageCode by viewModel.currentLanguageCode.collectAsState()
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val exportSummary by viewModel.exportSummary.collectAsState()
    val license by viewModel.licenseSnapshot.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedSectionName by rememberSaveable {
        mutableStateOf(SettingsSectionId.LANGUAGE.name)
    }
    val selectedSection = runCatching { SettingsSectionId.valueOf(selectedSectionName) }
        .getOrDefault(SettingsSectionId.LANGUAGE)
    
    val na = stringResource(R.string.common_not_available)
    val snackbarHostState = remember { SnackbarHostState() }
    val importSuccessMsg = stringResource(R.string.settings_import_success)
    val nothingPendingMsg = stringResource(R.string.settings_export_nothing_pending)
    val noSalesMsg = stringResource(R.string.settings_no_sales_for_date)
    val exportFailedMsg = stringResource(R.string.settings_export_failed)
    val bookkeepingFailedMsg = stringResource(R.string.settings_export_bookkeeping_failed)
    val shareFailedMsg = stringResource(R.string.settings_share_failed)
    val shareReadyMsg = stringResource(R.string.settings_share_ready)
    val activationShareFailedMsg = stringResource(R.string.activation_share_failed)
    val activationShareReadyMsg = stringResource(R.string.activation_share_ready)
    val activationSaveFailedMsg = stringResource(R.string.activation_save_failed)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImportMenu(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> viewModel.onExportDocumentResult(uri) }
    val activationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> viewModel.onActivationDocumentResult(uri) }
    val licensePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::onImportLicense) }

    LaunchedEffect(restaurantConfig) { viewModel.ensureDefaultBusinessDate() }
    val licenseImported = stringResource(R.string.license_imported_successfully)
    val licenseInvalid = stringResource(R.string.unable_to_verify_license)
    val licenseStale = stringResource(R.string.license_older)
    LaunchedEffect(viewModel.licenseImportResult) {
        val message = when (viewModel.licenseImportResult) {
            LicenseImportResult.Accepted, LicenseImportResult.Duplicate -> licenseImported
            LicenseImportResult.Stale -> licenseStale
            null -> null
            else -> licenseInvalid
        }
        if (message != null) { snackbarHostState.showSnackbar(message); viewModel.consumeLicenseImportResult() }
    }
    LaunchedEffect(viewModel.exportMessage) {
        val message = when (val result = viewModel.exportMessage) {
            ExportMessage.NothingPending -> nothingPendingMsg
            ExportMessage.NoSalesForDay -> noSalesMsg
            ExportMessage.Failed -> exportFailedMsg
            ExportMessage.BookkeepingFailed -> bookkeepingFailedMsg
            ExportMessage.ShareFailed -> shareFailedMsg
            ExportMessage.ShareReady -> shareReadyMsg
            is ExportMessage.Success -> resources.getQuantityString(R.plurals.settings_export_success, result.count, result.count)
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeExportMessage()
        }
    }
    LaunchedEffect(viewModel.activationMessage) {
        val message = when (viewModel.activationMessage) {
            ActivationMessage.ShareReady -> activationShareReadyMsg
            ActivationMessage.ShareFailed -> activationShareFailedMsg
            ActivationMessage.SaveFailed -> activationSaveFailedMsg
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeActivationMessage()
        }
    }

    LaunchedEffect(viewModel.showImportSuccess) {
        if (viewModel.showImportSuccess) {
            snackbarHostState.showSnackbar(importSuccessMsg)
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsNavigationPane(
                selectedSection = selectedSection,
                onSectionSelected = {
                    selectedSectionName = it.name
                }
            )
            VerticalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (selectedSection) {
                    SettingsSectionId.LANGUAGE -> DetailSection(stringResource(R.string.settings_section_language)) {
                        LanguageSelector(currentLanguageCode) { viewModel.setLanguage(it) }
                    }
                    SettingsSectionId.SUBSCRIPTION -> DetailSection(stringResource(R.string.settings_section_subscription)) {
                        InfoTable {
                            InfoRow(stringResource(R.string.status), licenseStatusText(license.state))
                            InfoRow(stringResource(R.string.plan), license.payload?.planCode ?: na)
                            InfoRow(stringResource(R.string.device_code), viewModel.deviceCode)
                            license.payload?.let {
                                val zone = restaurantConfig?.timezone ?: ZoneOffset.UTC
                                InfoRow(stringResource(R.string.valid_until), TerminalDateFormatter.formatProtocolDateTime(it.expiresAtUtc, zone, locale))
                                InfoRow(stringResource(R.string.grace_until), TerminalDateFormatter.formatProtocolDateTime(it.graceUntilUtc, zone, locale))
                            }
                        }
                        ActionColumn {
                            Button(onClick = viewModel::prepareActivationRequest, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.generate_activation_request))
                            }
                            OutlinedButton(
                                onClick = { licensePickerLauncher.launch("application/json") },
                                enabled = !viewModel.isImportingLicense,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.import_renew_license)) }
                        }
                    }
                    SettingsSectionId.SALES_EXPORT -> DetailSection(stringResource(R.string.settings_section_sales_export)) {
                        Text(stringResource(R.string.settings_pending_changes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            resources.getQuantityString(R.plurals.settings_unexported_count, exportSummary.pendingCount, exportSummary.pendingCount),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = viewModel::preparePendingExport,
                            enabled = !viewModel.isExporting,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_export_pending)) }
                        InfoTable {
                            InfoRow(
                                stringResource(R.string.settings_last_successful_export),
                                exportSummary.lastSuccessfulExportAtUtc?.let {
                                    val zone = restaurantConfig?.timezone ?: ZoneOffset.UTC
                                    TerminalDateFormatter.formatDateTime(it, zone, locale)
                                } ?: stringResource(R.string.settings_never)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.settings_export_by_date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = viewModel.selectedBusinessDate?.let { selected ->
                                    restaurantConfig?.let { config ->
                                        TerminalDateFormatter.formatDateWithToday(selected, viewModel.resolveCurrentBusinessDate(config), locale, stringResource(R.string.settings_today))
                                    } ?: TerminalDateFormatter.formatDate(selected, locale)
                                } ?: stringResource(R.string.settings_choose_business_date),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.settings_change_date)) }
                        }
                        OutlinedButton(
                            onClick = viewModel::prepareDayExport,
                            enabled = !viewModel.isExporting,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_export_day)) }
                    }
                    SettingsSectionId.RESTAURANT -> DetailSection(stringResource(R.string.settings_section_restaurant)) {
                        InfoTable {
                            InfoRow(stringResource(R.string.settings_restaurant_name), restaurantConfig?.restaurantName ?: na)
                            InfoRow(stringResource(R.string.settings_restaurant_id), restaurantConfig?.restaurantId ?: na)
                            InfoRow(stringResource(R.string.settings_currency), restaurantConfig?.currency?.currencyCode ?: na)
                            InfoRow(stringResource(R.string.settings_timezone), restaurantConfig?.timezone?.id ?: na)
                            InfoRow(stringResource(R.string.settings_business_cutoff), restaurantConfig?.businessDayCutoff?.toString() ?: na)
                        }
                    }
                    SettingsSectionId.TERMINAL -> DetailSection(stringResource(R.string.settings_section_terminal)) {
                        InfoTable {
                            InfoRow(stringResource(R.string.settings_terminal_name), terminalConfig?.terminalName ?: na)
                            InfoRow(stringResource(R.string.settings_terminal_id), terminalConfig?.terminalId?.value ?: na)
                        }
                    }
                    SettingsSectionId.MENU -> DetailSection(stringResource(R.string.settings_section_menu)) {
                        val menuZone = restaurantConfig?.timezone ?: ZoneOffset.UTC
                        InfoTable {
                            InfoRow(stringResource(R.string.settings_menu_id), publishedMenu?.menuId ?: na)
                            InfoRow(stringResource(R.string.settings_publication_revision), publishedMenu?.publicationRevision?.toString() ?: na)
                            InfoRow(stringResource(R.string.settings_published_at), publishedMenu?.publishedAtUtc?.let { TerminalDateFormatter.formatDateTime(it, menuZone, locale) } ?: na)
                            InfoRow(stringResource(R.string.settings_imported_at), publishedMenu?.importTimestamp?.let { TerminalDateFormatter.formatDateTime(it, menuZone, locale) } ?: na)
                        }
                        Button(
                            onClick = { filePickerLauncher.launch("application/json") },
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                            enabled = !viewModel.isImporting
                        ) {
                            if (viewModel.isImporting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Text(stringResource(R.string.settings_import_new_menu))
                        }
                    }
                    SettingsSectionId.APP_INFORMATION -> DetailSection(stringResource(R.string.settings_section_app)) {
                        InfoTable { InfoRow(stringResource(R.string.settings_app_version), BuildConfig.VERSION_NAME) }
                    }
                }
            }
        }
    }

    viewModel.importError?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportError() },
            confirmButton = { TextButton(onClick = { viewModel.clearImportError() }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(stringResource(R.string.settings_import_error_title)) },
            text = { Text(it.ifBlank { stringResource(R.string.settings_import_failed) }) }
        )
    }

    if (showDatePicker) {
        val initialMillis = (viewModel.selectedBusinessDate ?: LocalDate.ofEpochDay(0))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setBusinessDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.dialog_cancel)) } }
        ) { DatePicker(state = pickerState) }
    }

    viewModel.pendingDocument?.let { prepared ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPreparedExport,
            title = { Text(stringResource(R.string.settings_choose_export_method)) },
            text = { Text(stringResource(R.string.settings_choose_export_method_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::sharePreparedExport) {
                    Text(stringResource(R.string.settings_share))
                }
            },
            dismissButton = {
                TextButton(onClick = { exportLauncher.launch(prepared.suggestedFileName) }) {
                    Text(stringResource(R.string.settings_save_file))
                }
            }
        )
    }

    if (viewModel.showActivationRequestMethods) {
        val prepared = viewModel.pendingActivationRequest
        if (prepared != null) AlertDialog(
            onDismissRequest = viewModel::dismissActivationRequestMethods,
            title = { Text(stringResource(R.string.activation_request_created)) },
            text = { Text(stringResource(R.string.activation_choose_method)) },
            confirmButton = {
                TextButton(onClick = viewModel::shareActivationRequest) {
                    Text(stringResource(R.string.settings_share))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.saveActivationRequest()
                    activationLauncher.launch(prepared.suggestedFileName)
                }) { Text(stringResource(R.string.settings_save_file)) }
            }
        )
    }
}

@Composable
private fun licenseStatusText(state: LicenseState): String = stringResource(when (state) {
    LicenseState.NOT_ACTIVATED -> R.string.activation_required
    LicenseState.VALID -> R.string.subscription_active
    LicenseState.EXPIRING_SOON -> R.string.expires_soon
    LicenseState.GRACE_PERIOD -> R.string.grace_period
    LicenseState.EXPIRED -> R.string.subscription_expired
    LicenseState.CLOCK_ROLLBACK_DETECTED -> R.string.device_time_changed
    else -> R.string.license_problem
})

@Composable
private fun SettingsNavigationPane(
    selectedSection: SettingsSectionId,
    onSectionSelected: (SettingsSectionId) -> Unit
) {
    val sections = listOf(
        SettingsSectionId.LANGUAGE to stringResource(R.string.settings_section_language),
        SettingsSectionId.SUBSCRIPTION to stringResource(R.string.settings_section_subscription),
        SettingsSectionId.SALES_EXPORT to stringResource(R.string.settings_section_sales_export),
        SettingsSectionId.RESTAURANT to stringResource(R.string.settings_section_restaurant),
        SettingsSectionId.TERMINAL to stringResource(R.string.settings_section_terminal),
        SettingsSectionId.MENU to stringResource(R.string.settings_section_menu),
        SettingsSectionId.APP_INFORMATION to stringResource(R.string.settings_section_app)
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(260.dp).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { (section, label) ->
                val selected = section == selectedSection
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSectionSelected(section) }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.width(4.dp).height(24.dp)
                            ) {}
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun InfoTable(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ActionColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.width(180.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun LanguageSelector(
    currentLanguageCode: String?,
    onLanguageSelected: (String?) -> Unit
) {
    val languages = listOf(
        null to stringResource(R.string.settings_language_default),
        "en" to stringResource(R.string.settings_language_en),
        "es" to stringResource(R.string.settings_language_es)
    )

    Column(modifier = Modifier.selectableGroup().widthIn(max = 520.dp)) {
        languages.forEach { (code, label) ->
            val isSelected = currentLanguageCode == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onLanguageSelected(code) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (code != "es") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
