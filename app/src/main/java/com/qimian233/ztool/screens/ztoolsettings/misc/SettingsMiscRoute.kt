package com.qimian233.ztool.screens.ztoolsettings.misc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.data.misc.FirmwareResult
import com.qimian233.ztool.data.misc.PcFlashFirmwareRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.MiscSettingsUiState
import com.qimian233.ztool.viewmodel.MiscSettingsViewModel
import com.qimian233.ztool.viewmodel.PcFlashFirmwareUiState

/**
 * Miscellaneous settings: those that live on the Advanced route historically
 * but are neither experimental nor under active exploration.
 */
@Composable
fun SettingsMiscRoute(
    onBack: () -> Unit,
    onOpenEngineeringCodes: () -> Unit = {},
    targetId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as MainActivity
    val viewModel = remember {
        ViewModelProvider(
            activity,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MiscSettingsViewModel(
                    context = context.applicationContext,
                    firmwareRepository = PcFlashFirmwareRepository(context.applicationContext)
                ) as T
                }
            }
        )[MiscSettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val firmwareUiState by viewModel.firmwareUiState.collectAsState()

    val snDefaultHint = stringResource(R.string.system_update_sn_default_hint)

    DisposableEffect(lifecycleOwner) {
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

    val deleteOtaPackageStartingString = stringResource(R.string.page_settings_advanced_delete_ota_package_in_progress)

    if (uiState.showDeleteOtaPackageDialog) {
        DeleteOtaPackageConfirmDialog(
            onConfirm = {
                viewModel.performDeleteOtaPackage()
                Toast.makeText(context, deleteOtaPackageStartingString, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissDeleteOtaPackageDialog
        )
    }

    // Delete system update package: show a result Toast when done (SUCCEEDED / NOT_EXIST / FAILED)
    LaunchedEffect(uiState.deleteOtaPackageStatus) {
        uiState.deleteOtaPackageStatus?.let { status ->
            val message = when (status) {
                "SUCCEEDED" -> context.getString(R.string.page_settings_advanced_delete_ota_package_result_success)
                "NOT_EXIST" -> context.getString(R.string.page_settings_advanced_delete_ota_package_result_not_exist)
                else -> context.getString(
                    R.string.page_settings_advanced_delete_ota_package_result_failed,
                    uiState.deleteOtaPackageMessage ?: ""
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeDeleteOtaPackageResult()
        }
    }

    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        SettingsMiscScreen(
            state = uiState,
            firmwareState = firmwareUiState,
            onBack = onBack,
            onDeleteOtaPackageClick = { viewModel.showDeleteOtaPackageConfirmDialog() },
            onFixNightModeOverride = viewModel::fixNightModeOverride,
            onOpenEngineeringCodes = onOpenEngineeringCodes,
            onFirmwareSnChanged = viewModel::setFirmwareSnInput,
            onFetchFirmware = { viewModel.fetchFirmware(snDefaultHint) },
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    firmwareUiState.errorDialogMessage?.let { message ->
        ZToolDialog(
            onDismissRequest = viewModel::dismissFirmwareErrorDialog,
            title = { Text(stringResource(R.string.common_error_title)) },
            text = { Text(message) },
            confirmButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissFirmwareErrorDialog,
                    text = stringResource(R.string.common_confirm)
                )
            }
        )
    }
}

@Composable
private fun SettingsMiscScreen(
    state: MiscSettingsUiState,
    firmwareState: PcFlashFirmwareUiState,
    onBack: () -> Unit,
    onDeleteOtaPackageClick: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_settings_misc_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    highlightRegistry = highlightRegistry,
                    sections = miscSettingsSections(
                        state = state,
                        firmwareState = firmwareState,
                        onDeleteOtaPackageClick = onDeleteOtaPackageClick,
                        onFixNightModeOverride = onFixNightModeOverride,
                        onOpenEngineeringCodes = onOpenEngineeringCodes,
                        onFirmwareSnChanged = onFirmwareSnChanged,
                        onFetchFirmware = onFetchFirmware
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

@Composable
private fun miscSettingsSections(
    state: MiscSettingsUiState,
    firmwareState: PcFlashFirmwareUiState,
    onDeleteOtaPackageClick: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit
): List<SettingSection> {
    val deleteOtaPackageInProgressString = stringResource(R.string.page_settings_advanced_delete_ota_package_in_progress)
    val deleteOtaPackageDefaultSummary = stringResource(R.string.page_settings_advanced_delete_ota_package_summary)
    return listOf(
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "misc_delete_ota_package",
                    title = stringResource(R.string.page_settings_advanced_delete_ota_package_title),
                    summary = if (state.deleteOtaPackageInProgress) {
                        deleteOtaPackageInProgressString
                    } else {
                        deleteOtaPackageDefaultSummary
                    },
                    onClick = onDeleteOtaPackageClick,
                    enabled = !state.deleteOtaPackageInProgress,
                    icon = if (state.deleteOtaPackageInProgress) null else Icons.Rounded.DeleteForever,
                    trailingContent = if (state.deleteOtaPackageInProgress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(0.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                ),
                SettingItem.Action(
                    key = "misc_fix_night_mode_override",
                    title = stringResource(R.string.system_framework_fix_night_mode_title),
                    summary = stringResource(R.string.system_framework_fix_night_mode_summary),
                    onClick = onFixNightModeOverride,
                    enabled = !state.fixNightModeOverrideInProgress,
                    icon = if (state.fixNightModeOverrideInProgress) null else Icons.Rounded.Brightness4,
                    trailingContent = if (state.fixNightModeOverrideInProgress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(0.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                ),
                SettingItem.Action(
                    key = "misc_engineering_codes",
                    title = stringResource(R.string.engineering_codes_title),
                    summary = stringResource(R.string.engineering_codes_summary),
                    onClick = onOpenEngineeringCodes,
                    icon = Icons.Rounded.BuildCircle
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.system_update_pc_flash_firmware_fetch_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        FirmwareContent(
                            sn = firmwareState.snInput,
                            currentSn = firmwareState.currentSn,
                            isFetching = firmwareState.isFetching,
                            result = firmwareState.firmwareResult,
                            onSnChanged = onFirmwareSnChanged,
                            onFetch = onFetchFirmware
                        )
                    },
                    key = "misc_pc_flash_firmware_fetch"
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.page_settings_advanced_info_title),
            items = listOf(
                SettingItem.Action(
                    key = "misc_api_version",
                    title = stringResource(R.string.page_settings_advanced_api_version),
                    summary = "${state.apiVersion}",
                    onClick = {},
                    enabled = false,
                    icon = Icons.Rounded.Build
                )
            )
        )
    )
}

@Composable
private fun DeleteOtaPackageConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_settings_advanced_delete_ota_package_confirm_title)) },
        text = {
            Text(stringResource(R.string.page_settings_advanced_delete_ota_package_confirm_message))
        },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.common_confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
                isPrimary = false
            )
        }
    )
}

@Composable
private fun FirmwareContent(
    sn: String,
    currentSn: String,
    isFetching: Boolean,
    result: FirmwareResult?,
    onSnChanged: (String) -> Unit,
    onFetch: () -> Unit
) {
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.system_update_ota_info_clipboard_label)

    fun copyToClipboard(text: String, toastRes: Int) {
        val clipboard =
            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(clipboardLabel, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.system_update_pc_flash_firmware_fetch_summary),
                style = MaterialTheme.typography.titleMedium,
                color = LocalZToolColorScheme.current.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZToolTextInputRow(
                    value = sn,
                    onValueChange = onSnChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(720.dp),
                    label = if (currentSn.isNotEmpty() && currentSn != stringResource(R.string.system_update_loading_ellipsis)) {
                        stringResource(R.string.system_update_sn_current_machine_hint, currentSn)
                    } else {
                        stringResource(R.string.system_update_sn_default_hint)
                    },
                    singleLine = true,
                    horizontalPadding = 0.dp
                )
                Spacer(modifier = Modifier.width(32.dp))
                ZToolButton(
                    onClick = onFetch,
                    enabled = !isFetching,
                ) {
                    Text(
                        if (isFetching) stringResource(R.string.system_update_fetching_firmware_info)
                        else stringResource(R.string.common_confirm)
                    )
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(start = 24.dp, end = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                ResultText(
                    title = stringResource(R.string.system_update_pc_flash_firmware_fetch_result),
                    body = buildString {
                        append(stringResource(R.string.system_update_firmware_download_link))
                            .append(result.downloadUrl)
                            .append("\n")
                        append(stringResource(R.string.system_update_firmware_extract_password))
                            .append(result.password)
                            .append("\n")
                        append(stringResource(R.string.system_update_firmware_platform_and_method))
                            .append(result.platform)
                            .append(stringResource(R.string.system_update_firmware_platform_suffix))
                            .append(result.method)
                            .append("\n")
                        append(stringResource(R.string.system_update_firmware_first_upload_time))
                            .append(result.firstUploadTime)
                            .append("\n")
                        append(stringResource(R.string.system_update_firmware_last_update_time))
                            .append(result.lastUpdateTime)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive) {
                    Row {
                        Spacer(modifier = Modifier.weight(1f))
                        ZToolTextButton(
                            onClick = {
                                copyToClipboard(result.password, R.string.system_update_password_copied)
                            },
                            text = stringResource(R.string.system_update_copy_password),
                            isPrimary = false,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        ZToolTextButton(
                            onClick = {
                                copyToClipboard(result.downloadUrl, R.string.system_update_download_link_copied)
                            },
                            text = stringResource(R.string.system_update_copy_download_link),
                        )
                    }
                } else {
                    Column {
                        ZToolTextButton(
                            onClick = {
                                copyToClipboard(result.password, R.string.system_update_password_copied)
                            },
                            text = stringResource(R.string.system_update_copy_password),
                            isPrimary = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ZToolTextButton(
                            onClick = {
                                copyToClipboard(result.downloadUrl, R.string.system_update_download_link_copied)
                            },
                            text = stringResource(R.string.system_update_copy_download_link),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultText(
    title: String,
    body: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LocalZToolColorScheme.current.onSurfaceVariant
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
