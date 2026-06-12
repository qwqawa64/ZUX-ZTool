package com.qimian233.ztool.settingactivity.ota

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.ota.OtaSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.viewmodel.FirmwareResult
import com.qimian233.ztool.viewmodel.OtaInfoResult
import com.qimian233.ztool.viewmodel.OtaSettingsUiState
import com.qimian233.ztool.viewmodel.OtaSettingsViewModel

@Composable
fun OtaSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("OtaSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            OtaSettingsViewModelFactory(
                OtaSettingsRepository(context.applicationContext)
            )
        )[OtaSettingsViewModel::class.java]
    }
    val unknownText = stringResource(R.string.unknown)
    val otaInfoFetchFailed = stringResource(R.string.ota_info_fetch_failed)
    val snDefaultHint = stringResource(R.string.SN_default_hint)
    val clipboardLabel = stringResource(R.string.ota_info_clipboard_label)

    LaunchedEffect(viewModel) {
        viewModel.initialize(unknownText)
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(clipboardLabel, text)
        clipboard.setPrimaryClip(clip)
    }

    val uiState by viewModel.uiState.collectAsState()

    OtaSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onDisableOtaCheckChanged = viewModel::setDisableOtaCheck,
        onFetchOtaInfo = {
            viewModel.fetchOtaInfo(otaInfoFetchFailed)
        },
        onFirmwareSnChanged = viewModel::setFirmwareSnInput,
        onFetchFirmware = {
            viewModel.fetchFirmware(snDefaultHint)
        },
        onCustomVersionChanged = viewModel::setCustomVersion,
        onCustomDeviceIdChanged = viewModel::setCustomDeviceId,
        onCopyDownloadLink = {
            copyToClipboard(it)
            Toast.makeText(context, R.string.download_link_copied, Toast.LENGTH_SHORT).show()
        },
        onCopyChangelog = {
            copyToClipboard(it)
            Toast.makeText(context, R.string.changelog_copied, Toast.LENGTH_SHORT).show()
        },
        onCopyPassword = {
            copyToClipboard(it)
            Toast.makeText(context, R.string.password_copied, Toast.LENGTH_SHORT).show()
        },
        onRestartScope = viewModel::showRestartDialog
    )

    uiState.errorDialogMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = viewModel::dismissErrorDialog
        )
    }

    if (uiState.showRestartDialog) {
        RestartScopeDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartScope(
                    packageName = packageName,
                    onFailure = {
                        Toast.makeText(context, R.string.restart_failed, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

private class OtaSettingsViewModelFactory(
    private val repository: OtaSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OtaSettingsViewModel::class.java)) {
            return OtaSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
@Composable
private fun OtaSettingsScreen(
    title: String,
    state: OtaSettingsUiState,
    onBack: () -> Unit,
    onDisableOtaCheckChanged: (Boolean) -> Unit,
    onFetchOtaInfo: () -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit,
    onCustomVersionChanged: (String) -> Unit,
    onCustomDeviceIdChanged: (String) -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onRestartScope: () -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.restart_yes)) }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = otaSettingsSections(
                        state = state,
                        onDisableOtaCheckChanged = onDisableOtaCheckChanged,
                        onFetchOtaInfo = onFetchOtaInfo,
                        onFirmwareSnChanged = onFirmwareSnChanged,
                        onFetchFirmware = onFetchFirmware,
                        onCustomVersionChanged = onCustomVersionChanged,
                        onCustomDeviceIdChanged = onCustomDeviceIdChanged,
                        onCopyDownloadLink = onCopyDownloadLink,
                        onCopyChangelog = onCopyChangelog,
                        onCopyPassword = onCopyPassword
                    ),
                    bottomPadding = 88.dp
                )
            }
        }
    }
}

@Composable
private fun otaSettingsSections(
    state: OtaSettingsUiState,
    onDisableOtaCheckChanged: (Boolean) -> Unit,
    onFetchOtaInfo: () -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit,
    onCustomVersionChanged: (String) -> Unit,
    onCustomDeviceIdChanged: (String) -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit,
    onCopyPassword: (String) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.Ota_Setting_Title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.OtaDisable_title),
                    summary = stringResource(R.string.OtaDisable_summary),
                    checked = state.disableOtaCheck,
                    onCheckedChange = onDisableOtaCheckChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.OtaInfoFetch_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        OtaInfoContent(
                            isFetching = state.isFetchingOtaInfo,
                            result = state.otaInfoResult,
                            onFetch = onFetchOtaInfo,
                            onCopyDownloadLink = onCopyDownloadLink,
                            onCopyChangelog = onCopyChangelog
                        )
                    }
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.PCFlashFirmwareFetch_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        FirmwareContent(
                            sn = state.firmwareSnInput,
                            currentSn = state.currentSn,
                            isFetching = state.isFetchingFirmware,
                            result = state.firmwareResult,
                            onSnChanged = onFirmwareSnChanged,
                            onFetch = onFetchFirmware,
                            onCopyDownloadLink = onCopyDownloadLink,
                            onCopyPassword = onCopyPassword
                        )
                    }
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.Ota_Custom_Params_Title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        OtaCustomParamsContent(
                            state = state,
                            onCustomVersionChanged = onCustomVersionChanged,
                            onCustomDeviceIdChanged = onCustomDeviceIdChanged
                        )
                    }
                )
            )
        )
    )
}

@Composable
private fun OtaInfoContent(
    isFetching: Boolean,
    result: OtaInfoResult?,
    onFetch: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.OtaInfoFetch_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        ZToolButton(
            onClick = onFetch,
            enabled = !isFetching
        ) {
            Text(
                if (isFetching) {
                    stringResource(R.string.loading_ellipsis)
                } else {
                    stringResource(R.string.OtaInfoFetch_title)
                }
            )
        }

        if (result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ResultText(
                title = stringResource(R.string.ota_update_info_title),
                body = stringResource(
                    R.string.version_info_format,
                    result.fromVersion,
                    result.toVersion
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.updateLog),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = result.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            ResultText(
                title = stringResource(R.string.copy_download_link),
                body = stringResource(
                    R.string.download_info_format,
                    result.downloadUrl,
                    result.formattedSize,
                    result.md5
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(onClick = { onCopyDownloadLink(result.downloadUrl) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_download_link))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onCopyChangelog(result.changelogCopyText) }) {
                    Text(stringResource(R.string.copy_changelog))
                }
            }
        }
    }
}

@Composable
private fun FirmwareContent(
    sn: String,
    currentSn: String,
    isFetching: Boolean,
    result: FirmwareResult?,
    onSnChanged: (String) -> Unit,
    onFetch: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyPassword: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.PCFlashFirmwareFetch_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = sn,
            onValueChange = onSnChanged,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    if (currentSn.isNotEmpty() && currentSn != stringResource(R.string.loading_ellipsis)) {
                        stringResource(R.string.SN_current_machine_hint, currentSn)
                    } else {
                        stringResource(R.string.SN_default_hint)
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        Spacer(modifier = Modifier.height(12.dp))
        ZToolButton(
            onClick = onFetch,
            enabled = !isFetching
        ) {
            Text(
                if (isFetching) {
                    stringResource(R.string.fetching_firmware_info)
                } else {
                    stringResource(R.string.confirm)
                }
            )
        }

        if (result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ResultText(
                title = stringResource(R.string.PCFlashFirmwareFetch_result),
                body = buildString {
                    append(stringResource(R.string.firmware_download_link)).append(result.downloadUrl).append("\n")
                    append(stringResource(R.string.firmware_extract_password)).append(result.password).append("\n")
                    append(stringResource(R.string.firmware_platform_and_method))
                        .append(result.platform)
                        .append(stringResource(R.string.firmware_platform_suffix))
                        .append(result.method)
                        .append("\n")
                    append(stringResource(R.string.firmware_first_upload_time)).append(result.firstUploadTime).append("\n")
                    append(stringResource(R.string.firmware_last_update_time)).append(result.lastUpdateTime)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(onClick = { onCopyDownloadLink(result.downloadUrl) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_download_link))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onCopyPassword(result.password) }) {
                    Text(stringResource(R.string.copy_password))
                }
            }
        }
    }
}

@Composable
private fun OtaCustomParamsContent(
    state: OtaSettingsUiState,
    onCustomVersionChanged: (String) -> Unit,
    onCustomDeviceIdChanged: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.Ota_Custom_Params_Desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.current_version_fmt, state.currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
            value = state.customVersion,
            onValueChange = onCustomVersionChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            label = { Text(stringResource(R.string.Ota_Custom_Version_Hint)) },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.current_sn_fmt, state.currentSn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
            value = state.customDeviceId,
            onValueChange = onCustomDeviceIdChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            label = { Text(stringResource(R.string.Ota_Custom_DeviceID_Hint)) },
            singleLine = true
        )
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
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(message) },
        confirmButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.confirm))
        }
    )
}

@Composable
private fun RestartScopeDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    packageName +
                    ",com.lenovo.tbengine" +
                    stringResource(R.string.restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}
