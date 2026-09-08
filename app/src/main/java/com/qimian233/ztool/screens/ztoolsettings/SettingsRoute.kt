package com.qimian233.ztool.screens.ztoolsettings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.SettingsUiState
import com.qimian233.ztool.viewmodel.SettingsViewModel

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsMainRoute(
    onOpenThemeSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = rememberSettingsViewModel(activity)
    val uiState by viewModel.uiState.collectAsState()
    var showRestoreConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteLogsConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val defaultConfigRestoredStr = stringResource(R.string.default_config_restored)
    
    val backupSuccessStr = stringResource(R.string.config_backup_success)
    val restoreSuccessStr = stringResource(R.string.config_restore_success)
    val exportLogsSuccessStr = stringResource(R.string.export_logs_success)
    val exportLogsFailedStr = stringResource(R.string.export_logs_failed)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupConfig(uri) { result ->
                activity.runOnUiThread {
                    if (result) {
                        showSettingsToast(context, backupSuccessStr)
                    }
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreConfig(uri) { result ->
                activity.runOnUiThread {
                    if (result) {
                        showSettingsToast(context, restoreSuccessStr)
                    }
                }
            }
        }
    }
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLogsToUri(uri) { success, error ->
                activity.runOnUiThread {
                    Toast.makeText(
                        context,
                        when {
                            success -> exportLogsSuccessStr
                            error != null -> exportLogsFailedStr + error
                            else -> exportLogsFailedStr
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val deleteLogsSuccessStr = stringResource(R.string.delete_logs_success)
    val deleteLogsFailedStr = stringResource(R.string.delete_logs_failed)

    SettingsRoute(
        state = uiState,
        onBackup = { backupLauncher.launch(viewModel.backupFileName()) },
        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
        onRestoreDefault = { showRestoreConfirmDialog = true },
        onOpenThemeSettings = {
            showRestoreConfirmDialog = false
            onOpenThemeSettings()
        },
        onOpenLanguageSettings = { openAppLanguageSettings(context) },
        onDetailedLoggingChanged = viewModel::setDetailedLoggingEnabled,
        onEntryDisplayChanged = viewModel::setDisplayEntryInSettings,
        onHomepageYiyanChanged = viewModel::setHomepageYiyanEnabled,
        onAbout = {
            showRestoreConfirmDialog = false
            onOpenAbout()
        },
        onExportLogs = { exportLogLauncher.launch(viewModel.exportFileName()) },
        onDeleteAllLogs = { showDeleteLogsConfirmDialog = true },
        onOpenAdvanced = onOpenAdvanced,
        onAutoCheckUpdateChanged = viewModel::setAutoCheckUpdateEnabled
    )

    SettingsDialogs(
        showRestoreConfirmDialog = showRestoreConfirmDialog,
        showDeleteLogsConfirmDialog = showDeleteLogsConfirmDialog,
        onRestoreConfirm = {
            viewModel.restoreDefaultConfig()
            showRestoreConfirmDialog = false
            showSettingsToast(context, defaultConfigRestoredStr)
        },
        onRestoreDismiss = {
            showRestoreConfirmDialog = false
        },
        onDeleteLogsConfirm = {
            showDeleteLogsConfirmDialog = false
            viewModel.deleteAllLogs { success ->
                activity.runOnUiThread {
                    showSettingsToast(
                        context,
                        if (success) deleteLogsSuccessStr else deleteLogsFailedStr
                    )
                }
            }
        },
        onDeleteLogsDismiss = {
            showDeleteLogsConfirmDialog = false
        }
    )
}

@Composable
private fun SettingsDialogs(
    showRestoreConfirmDialog: Boolean,
    showDeleteLogsConfirmDialog: Boolean,
    onRestoreConfirm: () -> Unit,
    onRestoreDismiss: () -> Unit,
    onDeleteLogsConfirm: () -> Unit,
    onDeleteLogsDismiss: () -> Unit
) {
    if (showRestoreConfirmDialog) {
        RestoreDefaultDialog(
            onConfirm = onRestoreConfirm,
            onDismiss = onRestoreDismiss
        )
    }
    if (showDeleteLogsConfirmDialog) {
        DeleteLogsConfirmDialog(
            onConfirm = onDeleteLogsConfirm,
            onDismiss = onDeleteLogsDismiss
        )
    }
}

@Composable
internal fun rememberSettingsViewModel(activity: MainActivity): SettingsViewModel {
    val context = LocalContext.current
    return remember(activity) {
        val repository = SettingsRepository(context.applicationContext)
        ViewModelProvider(
            activity,
            SettingsViewModelFactory(repository)
        )[SettingsViewModel::class.java]
    }
}

private fun openAppLanguageSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        showSettingsToast(context, context.getString(R.string.open_app_language_settings_failed))
    }
}

private fun showSettingsToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SettingsRoute(
    state: SettingsUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreDefault: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onEntryDisplayChanged: (Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onHomepageYiyanChanged: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onExportLogs: () -> Unit,
    onDeleteAllLogs: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onAutoCheckUpdateChanged: (Boolean) -> Unit
) {
    ZToolScaffold (
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.settingsFragment_title),
                addNavIcon = false
            )
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = settingsSections(
                        state = state,
                        onBackup = onBackup,
                        onRestore = onRestore,
                        onRestoreDefault = onRestoreDefault,
                        onOpenThemeSettings = onOpenThemeSettings,
                        onOpenLanguageSettings = onOpenLanguageSettings,
                        onEntryDisplayChanged = onEntryDisplayChanged,
                        onDetailedLoggingChanged = onDetailedLoggingChanged,
                        onHomepageYiyanChanged = onHomepageYiyanChanged,
                        onAbout = onAbout,
                        onExportLogs = onExportLogs,
                        onDeleteAllLogs = onDeleteAllLogs,
                        onOpenAdvanced = onOpenAdvanced,
                        onAutoCheckUpdateChanged = onAutoCheckUpdateChanged,
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

@Composable
private fun settingsSections(
    state: SettingsUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreDefault: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onEntryDisplayChanged: (Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onHomepageYiyanChanged: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onExportLogs: () -> Unit,
    onDeleteAllLogs: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onAutoCheckUpdateChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.backupAndRestore),
            items = listOf(
                SettingItem.Action(
                    key = "backup_config",
                    title = stringResource(R.string.backupConfigToFile),
                    onClick = onBackup,
                    icon = Icons.Rounded.Backup,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                ),
                SettingItem.Action(
                    key = "restore_config",
                    title = stringResource(R.string.restoreConfigFromFile),
                    onClick = onRestore,
                    icon = Icons.Rounded.RestorePage,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                ),
                SettingItem.Action(
                    key = "restore_default",
                    title = stringResource(R.string.restoreDefaultConfig),
                    onClick = onRestoreDefault,
                    icon = Icons.Rounded.SettingsBackupRestore,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.moreSettings),
            items = listOf(
                SettingItem.Switch(
                    key = "display_entry_in_settings",
                    title = stringResource(R.string.display_entry_in_settings),
                    summary = stringResource(R.string.display_entry_in_settings_summary),
                    checked = state.isEntryDisplayedInSettings,
                    onCheckedChange = onEntryDisplayChanged,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew
                ),
                SettingItem.Switch(
                    key = "enable_homepage_yiyan",
                    title = stringResource(R.string.enableHomePageYiyan),
                    summary = stringResource(R.string.enableHomePageYiyanSummary),
                    checked = state.isHomepageYiyanEnabled,
                    onCheckedChange = onHomepageYiyanChanged,
                    icon = Icons.AutoMirrored.Filled.Notes
                ),
                SettingItem.Switch(
                    key = "auto_check_update",
                    title = stringResource(R.string.auto_check_update_title),
                    summary = stringResource(R.string.auto_check_update_summary),
                    checked = state.isAutoCheckUpdateEnabled,
                    onCheckedChange = onAutoCheckUpdateChanged,
                    icon = Icons.Rounded.Update
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.log_settings_title),
            items = listOf(
                SettingItem.Switch(
                    key = "enable_detailed_logging",
                    title = stringResource(R.string.enableDetailedLogging),
                    summary = stringResource(R.string.enableDetailedLoggingDescription),
                    checked = state.isDetailedLoggingEnabled,
                    onCheckedChange = onDetailedLoggingChanged,
                    icon = Icons.AutoMirrored.Rounded.Article
                ),
                SettingItem.Action(
                    key = "export_logs",
                    title = stringResource(R.string.export_logs),
                    summary = stringResource(R.string.export_logs_summary),
                    onClick = onExportLogs,
                    icon = Icons.Rounded.Save,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                ),
                SettingItem.Action(
                    key = "delete_all_logs",
                    title = stringResource(R.string.delete_all_logs),
                    summary = stringResource(R.string.delete_all_logs_summary),
                    onClick = onDeleteAllLogs,
                    icon = Icons.Rounded.DeleteForever,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        ),
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "open_theme_settings",
                    title = stringResource(R.string.app_ui_theme_settings),
                    onClick = onOpenThemeSettings,
                    icon = Icons.Rounded.Palette,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                ),
                SettingItem.Action(
                    key = "open_language_settings",
                    title = stringResource(R.string.app_language_settings),
                    onClick = onOpenLanguageSettings,
                    icon = Icons.Rounded.Language,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                ),
                SettingItem.Action(
                    key = "open_advanced_settings",
                    title = stringResource(R.string.advanced_title),
                    onClick = onOpenAdvanced,
                    icon = Icons.Rounded.Build,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        ),
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "show_about",
                    title = stringResource(R.string.showAboutPage),
                    onClick = onAbout,
                    icon = Icons.Rounded.Info,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        )
    )
}

@Composable
private fun RestoreDefaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.final_confirmation_title)) },
        text = { Text(stringResource(R.string.restore_default_confirmation)) },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.restart_no),
                isPrimary = false
            )
        }
    )
}

@Composable
private fun DeleteLogsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_all_logs)) },
        text = { Text(stringResource(R.string.delete_logs_confirmation)) },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.confirm))
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.restart_no),
                isPrimary = false
            )
        }
    )
}
