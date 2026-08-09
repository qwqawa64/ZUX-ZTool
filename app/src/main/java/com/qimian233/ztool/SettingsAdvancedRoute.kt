package com.qimian233.ztool

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.AdvancedSettingsUiState
import com.qimian233.ztool.viewmodel.AdvancedSettingsViewModel

@Composable
fun SettingsAdvancedRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context as MainActivity
    val viewModel = remember {
        ViewModelProvider(
            activity,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdvancedSettingsViewModel() as T
                }
            }
        )[AdvancedSettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()

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

    val hotReloadResultSummary = buildHotReloadResultSummary(uiState, context)
    val hotReloadStartingString = stringResource(R.string.advanced_hot_reload_starting)
    val resetResultSummary = buildResetResultSummary(uiState, context)
    val resetStartingString = stringResource(R.string.advanced_reset_starting)

    if (uiState.showHotReloadDialog) {
        HotReloadConfirmDialog(
            runningTargetCount = uiState.runningTargetCount,
            onConfirm = {
                viewModel.performHotReload()
                Toast.makeText(context, hotReloadStartingString, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissHotReloadDialog
        )
    }

    if (uiState.showResetDialog) {
        ResetConfirmDialog(
            onConfirm = {
                viewModel.performResetPersistentValues()
                Toast.makeText(context, resetStartingString, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissResetDialog
        )
    }

    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.advanced_title),
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = advancedSettingsSections(
                        state = uiState,
                        hotReloadResultSummary = hotReloadResultSummary,
                        resetResultSummary = resetResultSummary,
                        onHotReloadClick = { viewModel.showHotReloadConfirmDialog() },
                        onResetClick = { viewModel.showResetConfirmDialog() }
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

@Composable
private fun advancedSettingsSections(
    state: AdvancedSettingsUiState,
    hotReloadResultSummary: String?,
    resetResultSummary: String?,
    onHotReloadClick: () -> Unit,
    onResetClick: () -> Unit
): List<SettingSection> {
    val hotReloadSupported = state.apiVersion >= 102
    val hasTargets = state.runningTargetCount > 0

    return listOf(
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "reset_persistent_values",
                    title = stringResource(R.string.advanced_reset_title),
                    summary = buildResetSummary(
                        inProgress = state.resetInProgress,
                        resultSummary = resetResultSummary
                    ),
                    onClick = onResetClick,
                    enabled = !state.resetInProgress,
                    icon = if (state.resetInProgress) null else Icons.Rounded.Restore,
                    trailingContent = if (state.resetInProgress) {
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
                    key = "hot_reload_all",
                    title = stringResource(R.string.advanced_hot_reload_title),
                    summary = buildHotReloadSummary(
                        hotReloadSupported = hotReloadSupported,
                        hasTargets = hasTargets,
                        targetCount = state.runningTargetCount,
                        inProgress = state.hotReloadInProgress,
                        resultSummary = hotReloadResultSummary
                    ),
                    onClick = onHotReloadClick,
                    enabled = hotReloadSupported && hasTargets && !state.hotReloadInProgress,
                    icon = if (state.hotReloadInProgress) null else Icons.Rounded.Refresh,
                    trailingContent = if (state.hotReloadInProgress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(0.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                )
            )
        )
    ) + buildResetDetailSection(state) + buildHotReloadDetailSection(state) + listOf(
        SettingSection(
            title = stringResource(R.string.advanced_info_title),
            items = listOf(
                SettingItem.Action(
                    key = "api_version",
                    title = stringResource(R.string.advanced_api_version),
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
private fun buildResetDetailSection(
    state: AdvancedSettingsUiState
): List<SettingSection> {
    if (state.resetInProgress || state.resetDetails.isEmpty()) return emptyList()
    return listOf(
        SettingSection(
            title = stringResource(R.string.advanced_reset_detail_title),
            items = state.resetDetails.map { detail ->
                val statusColor = when (detail.status) {
                    "SUCCEEDED" -> LocalZToolColorScheme.current.primary
                    "FAILED" -> LocalZToolColorScheme.current.error
                    "UNSUPPORTED" -> LocalZToolColorScheme.current.error
                    else -> LocalZToolColorScheme.current.onSurfaceVariant
                }
                SettingItem.Action(
                    key = "reset_${detail.key}",
                    title = resetItemDisplayName(detail.key),
                    summary = "[${detail.status}] ${detail.message}",
                    onClick = {},
                    enabled = false,
                    icon = null,
                    leadingContent = {
                        Text(
                            text = when (detail.status) {
                                "SUCCEEDED" -> "✓"
                                "FAILED" -> "✗"
                                "UNSUPPORTED" -> "⊘"
                                else -> "?"
                            },
                            color = statusColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            }
        )
    )
}

@Composable
private fun resetItemDisplayName(key: String): String = when (key) {
    "doze_always_on" -> stringResource(R.string.advanced_reset_item_aod)
    "autorun" -> stringResource(R.string.advanced_reset_item_autorun)
    "mistouch" -> stringResource(R.string.advanced_reset_item_mistouch)
    else -> key
}

@Composable
private fun buildHotReloadDetailSection(
    state: AdvancedSettingsUiState
): List<SettingSection> {
    if (state.hotReloadInProgress || state.hotReloadDetails.isEmpty()) return emptyList()
    return listOf(
        SettingSection(
            title = stringResource(R.string.advanced_hot_reload_detail_title),
            items = state.hotReloadDetails.map { detail ->
                val statusColor = when (detail.status) {
                    "SUCCEEDED" -> LocalZToolColorScheme.current.primary
                    "FAILED" -> LocalZToolColorScheme.current.error
                    "UNSUPPORTED" -> LocalZToolColorScheme.current.error
                    "PROCESS_DIED" -> LocalZToolColorScheme.current.error
                    else -> LocalZToolColorScheme.current.onSurfaceVariant
                }
                SettingItem.Action(
                    key = "detail_${detail.processName}",
                    title = detail.processName,
                    summary = "[${detail.status}] ${detail.message}",
                    onClick = {},
                    enabled = false,
                    icon = null,
                    leadingContent = {
                        Text(
                            text = when (detail.status) {
                                "SUCCEEDED" -> "✓"
                                "FAILED" -> "✗"
                                "UNSUPPORTED" -> "⊘"
                                "PROCESS_DIED" -> "☠"
                                else -> "?"
                            },
                            color = statusColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            }
        )
    )
}

@Composable
private fun buildHotReloadSummary(
    hotReloadSupported: Boolean,
    hasTargets: Boolean,
    targetCount: Int,
    inProgress: Boolean,
    resultSummary: String?
): String {
    val hotReloadInProgressString = stringResource(R.string.advanced_hot_reload_in_progress)
    val hotReloadNotSupportedString = stringResource(R.string.advanced_hot_reload_unsupported)
    val hotReloadNoTargetsString = stringResource(R.string.advanced_hot_reload_no_targets)
    val hotReloadSummaryString = stringResource(R.string.advanced_hot_reload_summary, targetCount)
    return when {
        inProgress -> hotReloadInProgressString
        !hotReloadSupported -> hotReloadNotSupportedString
        !hasTargets -> hotReloadNoTargetsString
        resultSummary != null -> resultSummary
        else -> hotReloadSummaryString
    }
}

private fun buildHotReloadResultSummary(
    state: AdvancedSettingsUiState,
    context: android.content.Context
): String? {
    if (state.hotReloadInProgress) return null
    val total = state.hotReloadResultSucceeded + state.hotReloadResultFailed +
            state.hotReloadResultUnsupported + state.hotReloadResultDied
    if (total == 0) return null
    return context.getString(
        R.string.advanced_hot_reload_result,
        state.hotReloadResultSucceeded,
        state.hotReloadResultFailed,
        state.hotReloadResultUnsupported,
        state.hotReloadResultDied
    )
}

@Composable
private fun buildResetSummary(
    inProgress: Boolean,
    resultSummary: String?
): String {
    val resetInProgressString = stringResource(R.string.advanced_reset_in_progress)
    val resetDefaultSummary = stringResource(R.string.advanced_reset_summary)
    return when {
        inProgress -> resetInProgressString
        resultSummary != null -> resultSummary
        else -> resetDefaultSummary
    }
}

private fun buildResetResultSummary(
    state: AdvancedSettingsUiState,
    context: android.content.Context
): String? {
    if (state.resetInProgress) return null
    val total = state.resetResultSucceeded + state.resetResultFailed + state.resetResultUnsupported
    if (total == 0) return null
    return context.getString(
        R.string.advanced_reset_result,
        state.resetResultSucceeded,
        state.resetResultFailed,
        state.resetResultUnsupported
    )
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_reset_confirm_title)) },
        text = {
            Text(stringResource(R.string.advanced_reset_confirm_message))
        },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.cancel),
                isPrimary = false
            )
        }
    )
}

@Composable
private fun HotReloadConfirmDialog(
    runningTargetCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_hot_reload_confirm_title)) },
        text = {
            Text(stringResource(R.string.advanced_hot_reload_confirm_message, runningTargetCount))
        },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.cancel),
                isPrimary = false
            )
        }
    )
}
