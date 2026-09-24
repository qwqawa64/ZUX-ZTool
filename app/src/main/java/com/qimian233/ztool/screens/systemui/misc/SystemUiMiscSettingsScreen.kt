package com.qimian233.ztool.screens.systemui.misc

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.FirmwareResult
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsUiState
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.SystemUiMiscSettingsViewModel

@Composable
fun SystemUiMiscSettingsRoute(
    title: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SystemUiMiscSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SystemUiMiscSettingsViewModelFactory(
                SystemUiMiscSettingsRepository(context.applicationContext)
            )
        )[SystemUiMiscSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    val snDefaultHint = stringResource(R.string.system_update_sn_default_hint)

    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        SystemUiMiscSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onGuestModeChanged = viewModel::setGuestModeController,
            onDisableBiometricErrorVibrationChanged = viewModel::setDisableBiometricErrorVibration,
            onBypassFaceAuthTimeoutChanged = viewModel::setBypassFaceAuthTimeout,
            onAospScrollCaptureChanged = viewModel::setAospScrollCapture,
            onShadeReboundFixChanged = viewModel::setShadeReboundFix,
            onFirmwareSnChanged = viewModel::setFirmwareSnInput,
            onFetchFirmware = {
                viewModel.fetchFirmware(snDefaultHint)
            },
            onDismissErrorDialog = viewModel::dismissErrorDialog,
            onRestartScope = viewModel::showRestartDialog,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    uiState.errorDialogMessage?.let { message ->
        ZToolDialog(
            onDismissRequest = viewModel::dismissErrorDialog,
            title = { Text(stringResource(R.string.common_error_title)) },
            text = { Text(message) },
            confirmButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissErrorDialog,
                    text = stringResource(R.string.common_confirm)
                )
            }
        )
    }

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.common_restart_fail)
        RestartScopeDialog(
            onConfirm = {
                viewModel.forceStopScope { success, error ->
                    if (success) {
                        Toast.makeText(context, R.string.common_restart_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, restartFailString + error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

private class SystemUiMiscSettingsViewModelFactory(
    private val repository: SystemUiMiscSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SystemUiMiscSettingsViewModel::class.java)) {
            return SystemUiMiscSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SystemUiMiscSettingsScreen(
    title: String,
    state: SystemUiMiscSettingsUiState,
    onBack: () -> Unit,
    onGuestModeChanged: (Boolean) -> Unit,
    onDisableBiometricErrorVibrationChanged: (Boolean) -> Unit,
    onBypassFaceAuthTimeoutChanged: (Boolean) -> Unit,
    onAospScrollCaptureChanged: (Boolean) -> Unit,
    onShadeReboundFixChanged: (Boolean) -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit,
    onDismissErrorDialog: () -> Unit,
    onRestartScope: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.common_restart_yes)) }
            )
        }
    ) { innerPadding ->
        Box(
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
                    sections = systemUiMiscSettingsSections(
                        state = state,
                        onGuestModeChanged = onGuestModeChanged,
                        onDisableBiometricErrorVibrationChanged = onDisableBiometricErrorVibrationChanged,
                        onBypassFaceAuthTimeoutChanged = onBypassFaceAuthTimeoutChanged,
                        onAospScrollCaptureChanged = onAospScrollCaptureChanged,
                        onShadeReboundFixChanged = onShadeReboundFixChanged,
                        onFirmwareSnChanged = onFirmwareSnChanged,
                        onFetchFirmware = onFetchFirmware,
                    ),
                    bottomPadding = 96.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun systemUiMiscSettingsSections(
    state: SystemUiMiscSettingsUiState,
    onGuestModeChanged: (Boolean) -> Unit,
    onDisableBiometricErrorVibrationChanged: (Boolean) -> Unit,
    onBypassFaceAuthTimeoutChanged: (Boolean) -> Unit,
    onAospScrollCaptureChanged: (Boolean) -> Unit,
    onShadeReboundFixChanged: (Boolean) -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.system_ui_common_misc),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_ui_misc_disable_guest_user_enable_title),
                        summary = stringResource(R.string.system_ui_misc_disable_guest_user_enable_summary),
                        checked = state.guestModeController,
                        onCheckedChange = onGuestModeChanged,
                        key = "system_ui_misc_disable_guest_user"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_ui_misc_disable_biometric_error_vibration_title),
                        checked = state.disableBiometricErrorVibration,
                        onCheckedChange = onDisableBiometricErrorVibrationChanged,
                        key = "system_ui_misc_disable_biometric_error_vibration"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_ui_misc_bypass_face_auth_timeout_title),
                        summary = stringResource(R.string.system_ui_misc_bypass_face_auth_timeout_summary),
                        checked = state.bypassFaceAuthTimeout,
                        onCheckedChange = onBypassFaceAuthTimeoutChanged,
                        key = "system_ui_misc_bypass_face_auth_timeout"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_ui_misc_aosp_scroll_capture_title),
                        summary = stringResource(R.string.system_ui_misc_aosp_scroll_capture_summary),
                        checked = state.aospScrollCapture,
                        onCheckedChange = onAospScrollCaptureChanged,
                        key = "system_ui_misc_aosp_scroll_capture"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_ui_misc_shade_rebound_fix_title),
                        summary = stringResource(R.string.system_ui_misc_shade_rebound_fix_summary),
                        checked = state.shadeReboundFix,
                        onCheckedChange = onShadeReboundFixChanged,
                        key = "system_ui_misc_shade_rebound_fix"
                    )
                )
            }
        ),
        SettingSection(
            title = stringResource(R.string.system_update_pc_flash_firmware_fetch_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        FirmwareContent(
                            sn = state.firmwareSnInput,
                            currentSn = state.currentSn,
                            isFetching = state.isFetchingFirmware,
                            result = state.firmwareResult,
                            onSnChanged = onFirmwareSnChanged,
                            onFetch = onFetchFirmware
                        )
                    },
                    key = "system_ui_misc_pc_flash_firmware_fetch"
                )
            )
        )
    )
}

@Composable
private fun RestartScopeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.common_restart_xp_message_header) +
                    "com.android.systemui，com.zui.wallpapersetting" +
                    stringResource(R.string.common_restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.common_restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.common_restart_no), isPrimary = false)
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
