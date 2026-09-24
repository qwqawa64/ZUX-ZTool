package com.qimian233.ztool.screens.ota

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
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
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
import com.qimian233.ztool.data.ota.OtaSettingsRepository
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
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.OtaInfoResult
import com.qimian233.ztool.viewmodel.OtaSettingsUiState
import com.qimian233.ztool.viewmodel.OtaSettingsViewModel

@Composable
fun OtaSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
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
    val unknownText = stringResource(R.string.common_unknown)
    val otaInfoFetchFailed = stringResource(R.string.system_update_ota_info_fetch_failed)
    val clipboardLabel = stringResource(R.string.system_update_ota_info_clipboard_label)

    LaunchedEffect(viewModel) {
        viewModel.initialize(unknownText)
    }

    fun copyToClipboard(text: String) {
        val clipboard =
            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(clipboardLabel, text)
        clipboard.setPrimaryClip(clip)
    }

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        OtaSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onDisableOtaCheckChanged = viewModel::setDisableOtaCheck,
            onHideOtaUpdateHintChanged = viewModel::setHideOtaUpdate,
            onDisableOtaAutoInstallChanged = viewModel::setDisableAutoOtaInstall,
            onBlockOtaInstallDialogChanged = viewModel::setBlockOtaInstallDialog,
            onDisableOtaNotificationAndRedDot = viewModel::setDisableOtaNotificationAndRedDot,
            onFetchOtaInfo = {
                viewModel.fetchOtaInfo(otaInfoFetchFailed)
            },
            onCopyDownloadLink = {
                copyToClipboard(it)
                Toast.makeText(context, R.string.system_update_download_link_copied, Toast.LENGTH_SHORT).show()
            },
            onCopyChangelog = {
                copyToClipboard(it)
                Toast.makeText(context, R.string.system_update_changelog_copied, Toast.LENGTH_SHORT).show()
            },
            onRestartScope = viewModel::showRestartDialog,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

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
                    onFailure = {
                        Toast.makeText(context, R.string.system_update_restart_failed, Toast.LENGTH_SHORT).show()
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
    onHideOtaUpdateHintChanged: (Boolean) -> Unit,
    onDisableOtaAutoInstallChanged: (Boolean) -> Unit,
    onBlockOtaInstallDialogChanged: (Boolean) -> Unit,
    onDisableOtaNotificationAndRedDot: (Boolean) -> Unit,
    onFetchOtaInfo: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit,
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
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
                    sections = otaSettingsSections(
                        state = state,
                        onDisableOtaCheckChanged = onDisableOtaCheckChanged,
                        onHideOtaUpdateHintChanged = onHideOtaUpdateHintChanged,
                        onFetchOtaInfo = onFetchOtaInfo,
                        onCopyDownloadLink = onCopyDownloadLink,
                        onCopyChangelog = onCopyChangelog,
                        onDisableOtaAutoInstallChanged = onDisableOtaAutoInstallChanged,
                        onBlockOtaInstallDialogChanged = onBlockOtaInstallDialogChanged,
                        onDisableOtaNotificationAndRedDot = onDisableOtaNotificationAndRedDot
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun otaSettingsSections(
    state: OtaSettingsUiState,
    onDisableOtaCheckChanged: (Boolean) -> Unit,
    onHideOtaUpdateHintChanged: (Boolean) -> Unit,
    onDisableOtaAutoInstallChanged: (Boolean) -> Unit,
    onBlockOtaInstallDialogChanged: (Boolean) -> Unit,
    onDisableOtaNotificationAndRedDot: (Boolean) -> Unit,
    onFetchOtaInfo: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.system_update_ota_setting_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.system_update_ota_disable_title),
                    summary = stringResource(R.string.system_update_ota_disable_summary),
                    checked = state.disableOtaCheck,
                    onCheckedChange = onDisableOtaCheckChanged,
                    key = "ota_disable_update"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_update_disable_ota_auto_install_title),
                    checked = state.noAutoOtaInstall,
                    onCheckedChange = onDisableOtaAutoInstallChanged,
                    key = "ota_disable_auto_install"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_update_block_ota_install_dialog_title),
                    checked = state.blockOtaInstallDialog,
                    onCheckedChange = onBlockOtaInstallDialogChanged,
                    key = "ota_block_install_dialog"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_update_hide_ota_update_hint),
                    checked = state.hideOtaUpdateHint,
                    onCheckedChange = onHideOtaUpdateHintChanged,
                    key = "ota_hide_update_hint"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_update_disable_ota_notification_and_red_dot_title),
                    summary = stringResource(R.string.system_update_disable_ota_notification_and_red_dot_summary),
                    checked = state.disableOtaNotificationAndRedDot,
                    onCheckedChange = onDisableOtaNotificationAndRedDot,
                    key = "ota_disable_notification_and_red_dot"
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.system_update_ota_info_fetch_title),
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
                    },
                    key = "ota_info_fetch"
                )
            )
        ),
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
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.system_update_ota_info_fetch_summary),
                style = MaterialTheme.typography.titleMedium,
                color = LocalZToolColorScheme.current.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 720.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ZToolButton(
                onClick = onFetch,
                enabled = !isFetching
            ) {
                Text(
                    if (isFetching) {
                        stringResource(R.string.system_update_loading_ellipsis)
                    } else {
                        stringResource(R.string.system_update_ota_info_fetch_title)
                    }
                )
            }
        }
        if (result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(start = 24.dp, end = 24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            ResultText(
                title = stringResource(R.string.system_update_update_info_title),
                body = stringResource(
                    R.string.system_update_version_info_format,
                    result.fromVersion,
                    result.toVersion
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.system_update_update_log),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalZToolColorScheme.current.onSurfaceVariant
            )
            Text(
                text = result.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (result.isNewVersionAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                ResultText(
                    title = stringResource(R.string.system_update_copy_download_link),
                    body =
                        stringResource(
                            R.string.system_update_download_info_format,
                            result.downloadUrl,
                            result.formattedSize,
                            result.md5
                        )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive) {
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    ZToolTextButton(
                        onClick = { onCopyChangelog(result.changelogCopyText) },
                        text = stringResource(R.string.system_update_copy_changelog),
                        isPrimary = false,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    ZToolTextButton(
                        onClick = { onCopyDownloadLink(result.downloadUrl) },
                        enabled = result.isNewVersionAvailable,
                        text = stringResource(R.string.system_update_copy_download_link),
                        isPrimary = true,
                    )
                }
            } else {
                Column {
                    ZToolTextButton(
                        onClick = { onCopyChangelog(result.changelogCopyText) },
                        text = stringResource(R.string.system_update_copy_changelog),
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ZToolTextButton(
                        onClick = { onCopyDownloadLink(result.downloadUrl) },
                        enabled = result.isNewVersionAvailable,
                        text = stringResource(R.string.system_update_copy_download_link),
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_error_title)) },
        text = { Text(message) },
        confirmButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.common_confirm))
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
        title = { Text(stringResource(R.string.common_restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.common_restart_xp_message_header) +
                        packageName +
                        ", com.lenovo.tbengine" +
                        ", com.android.settings " +
                        stringResource(R.string.common_restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.common_restart_yes))
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_restart_no),
                isPrimary = false
            )
        }
    )
}
