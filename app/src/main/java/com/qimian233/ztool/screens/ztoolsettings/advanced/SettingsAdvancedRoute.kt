package com.qimian233.ztool.screens.ztoolsettings.advanced

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.qimian233.ztool.dexindex.base.DexIndexManager
import com.qimian233.ztool.dexindex.base.DexIndexRegistry
import com.qimian233.ztool.ui.components.DexIndexProgressDialog
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalThemeRevealController
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.AdvancedSettingsUiState
import com.qimian233.ztool.viewmodel.AdvancedSettingsViewModel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAdvancedRoute(
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
                    return AdvancedSettingsViewModel() as T
                }
            }
        )[AdvancedSettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val dexIndexState by viewModel.dexIndexState.collectAsState()

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
    val hotReloadStartingString = stringResource(R.string.page_settings_advanced_hot_reload_starting)
    val resetResultSummary = buildResetResultSummary(uiState, context)
    val resetStartingString = stringResource(R.string.page_settings_advanced_reset_starting)
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

    var dexIndexSummary by remember { mutableStateOf(buildDexIndexSummary(context)) }

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

    // DexKit manual refresh: foreground progress Dialog + result Toast when done
    if (dexIndexState.refreshing) {
        DexIndexProgressDialog(progress = dexIndexState.progress)
    }

    LaunchedEffect(dexIndexState.resultRes) {
        dexIndexState.resultRes?.let { res ->
            dexIndexSummary = buildDexIndexSummary(context)
            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
            viewModel.consumeDexIndexResult()
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
        SettingsAdvancedScreen(
            state = uiState,
            onBack = onBack,
            hotReloadResultSummary = hotReloadResultSummary,
            resetResultSummary = resetResultSummary,
            dexIndexInProgress = dexIndexState.refreshing,
            dexIndexSummary = dexIndexSummary,
            onHotReloadClick = { viewModel.showHotReloadConfirmDialog() },
            onResetClick = { viewModel.showResetConfirmDialog() },
            onDeleteOtaPackageClick = { viewModel.showDeleteOtaPackageConfirmDialog() },
            onRefreshDexIndex = { viewModel.refreshDexIndex(context) },
            onOpenFirstrun = { activity.reopenFirstrun() },
            onFixNightModeOverride = { viewModel.fixNightModeOverride(context) },
            onOpenEngineeringCodes = onOpenEngineeringCodes,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }
}
@Composable
private fun SettingsAdvancedScreen(
    state: AdvancedSettingsUiState,
    onBack: () -> Unit,
    hotReloadResultSummary: String?,
    resetResultSummary: String?,
    dexIndexInProgress: Boolean,
    dexIndexSummary: String,
    onHotReloadClick: () -> Unit,
    onResetClick: () -> Unit,
    onDeleteOtaPackageClick: () -> Unit,
    onRefreshDexIndex: () -> Unit,
    onOpenFirstrun: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_settings_advanced_title),
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
                    sections = advancedSettingsSections(
                        state = state,
                        hotReloadResultSummary = hotReloadResultSummary,
                        resetResultSummary = resetResultSummary,
                        onHotReloadClick = onHotReloadClick,
                        onResetClick = onResetClick,
                        onDeleteOtaPackageClick = onDeleteOtaPackageClick,
                        dexIndexInProgress = dexIndexInProgress,
                        dexIndexSummary = dexIndexSummary,
                        onRefreshDexIndex = onRefreshDexIndex,
                        onOpenFirstrun = onOpenFirstrun,
                        onFixNightModeOverride = onFixNightModeOverride,
                        onOpenEngineeringCodes = onOpenEngineeringCodes
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
    onResetClick: () -> Unit,
    onDeleteOtaPackageClick: () -> Unit,
    dexIndexInProgress: Boolean,
    dexIndexSummary: String,
    onRefreshDexIndex: () -> Unit,
    onOpenFirstrun: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit
): List<SettingSection> {
    val hotReloadSupported = state.apiVersion >= 102
    val hasTargets = state.runningTargetCount > 0
    val revealController = LocalThemeRevealController.current
    var firstrunRowAnchor by remember { mutableStateOf(Offset.Zero) }

    return listOf(
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "advanced_refresh_dex_index",
                    title = stringResource(R.string.page_settings_refresh_dex_index),
                    summary = dexIndexSummary,
                    onClick = onRefreshDexIndex,
                    enabled = !dexIndexInProgress,
                    icon = if (dexIndexInProgress) null else Icons.Rounded.Search,
                    trailingContent = if (dexIndexInProgress) {
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
                    key = "advanced_reset_persistent_values",
                    title = stringResource(R.string.page_settings_advanced_reset_title),
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
                    key = "advanced_delete_ota_package",
                    title = stringResource(R.string.page_settings_advanced_delete_ota_package_title),
                    summary = buildDeleteOtaPackageSummary(
                        inProgress = state.deleteOtaPackageInProgress
                    ),
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
                    key = "advanced_fix_night_mode_override",
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
                    key = "advanced_hot_reload",
                    title = stringResource(R.string.page_settings_advanced_hot_reload_title),
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
                ),
                SettingItem.Action(
                    key = "advanced_engineering_codes",
                    title = stringResource(R.string.engineering_codes_title),
                    summary = stringResource(R.string.engineering_codes_summary),
                    onClick = onOpenEngineeringCodes,
                    icon = Icons.Rounded.BuildCircle
                ),
                SettingItem.Action(
                    key = "advanced_open_firstrun",
                    title = stringResource(R.string.page_settings_advanced_open_firstrun_title),
                    summary = stringResource(R.string.page_settings_advanced_open_firstrun_summary),
                    onClick = {
                        revealController.triggerReveal(
                            onAction = onOpenFirstrun,
                            anchor = firstrunRowAnchor
                        )
                    },
                    icon = Icons.Rounded.RocketLaunch,
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(1.dp)
                                .onGloballyPositioned { coordinates ->
                                    firstrunRowAnchor = coordinates.positionInRoot()
                                }
                        )
                    }
                )
            )
        )
    ) + buildResetDetailSection(state) + buildHotReloadDetailSection(state) + listOf(
        SettingSection(
            title = stringResource(R.string.page_settings_advanced_info_title),
            items = listOf(
                SettingItem.Action(
                    key = "api_version",
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
private fun buildResetDetailSection(
    state: AdvancedSettingsUiState
): List<SettingSection> {
    if (state.resetInProgress || state.resetDetails.isEmpty()) return emptyList()
    return listOf(
        SettingSection(
            title = stringResource(R.string.page_settings_advanced_reset_detail_title),
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
    "doze_always_on" -> stringResource(R.string.page_settings_advanced_reset_item_aod)
    "autorun" -> stringResource(R.string.page_settings_advanced_reset_item_autorun)
    "mistouch" -> stringResource(R.string.page_settings_advanced_reset_item_mistouch)
    else -> key
}

@Composable
private fun buildHotReloadDetailSection(
    state: AdvancedSettingsUiState
): List<SettingSection> {
    if (state.hotReloadInProgress || state.hotReloadDetails.isEmpty()) return emptyList()
    return listOf(
        SettingSection(
            title = stringResource(R.string.page_settings_advanced_hot_reload_detail_title),
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
    val hotReloadInProgressString = stringResource(R.string.page_settings_advanced_hot_reload_in_progress)
    val hotReloadNotSupportedString = stringResource(R.string.page_settings_advanced_hot_reload_unsupported)
    val hotReloadNoTargetsString = stringResource(R.string.page_settings_advanced_hot_reload_no_targets)
    val hotReloadSummaryString = stringResource(R.string.page_settings_advanced_hot_reload_summary, targetCount)
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
    context: Context
): String? {
    if (state.hotReloadInProgress) return null
    val total = state.hotReloadResultSucceeded + state.hotReloadResultFailed +
            state.hotReloadResultUnsupported + state.hotReloadResultDied
    if (total == 0) return null
    return context.getString(
        R.string.page_settings_advanced_hot_reload_result,
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
    val resetInProgressString = stringResource(R.string.page_settings_advanced_reset_in_progress)
    val resetDefaultSummary = stringResource(R.string.page_settings_advanced_reset_summary)
    return when {
        inProgress -> resetInProgressString
        resultSummary != null -> resultSummary
        else -> resetDefaultSummary
    }
}

private fun buildResetResultSummary(
    state: AdvancedSettingsUiState,
    context: Context
): String? {
    if (state.resetInProgress) return null
    val total = state.resetResultSucceeded + state.resetResultFailed + state.resetResultUnsupported
    if (total == 0) return null
    return context.getString(
        R.string.page_settings_advanced_reset_result,
        state.resetResultSucceeded,
        state.resetResultFailed,
        state.resetResultUnsupported
    )
}

@Composable
private fun buildDeleteOtaPackageSummary(
    inProgress: Boolean
): String {
    val deleteOtaPackageInProgressString = stringResource(R.string.page_settings_advanced_delete_ota_package_in_progress)
    val deleteOtaPackageDefaultSummary = stringResource(R.string.page_settings_advanced_delete_ota_package_summary)
    return if (inProgress) deleteOtaPackageInProgressString else deleteOtaPackageDefaultSummary
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_settings_advanced_reset_confirm_title)) },
        text = {
            Text(stringResource(R.string.page_settings_advanced_reset_confirm_message))
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
private fun HotReloadConfirmDialog(
    runningTargetCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_settings_advanced_hot_reload_confirm_title)) },
        text = {
            Text(stringResource(R.string.page_settings_advanced_hot_reload_confirm_message, runningTargetCount))
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

/**
 * Summarizes the DexKit index state: takes the most recent successful index time
 * across all scopes and formats it as display text.
 */
private fun buildDexIndexSummary(context: Context): String {
    val latest = DexIndexRegistry.indexers
        .map { DexIndexManager.lastIndexedAt(context, it.scopePackage) }
        .filter { it > 0L }
        .maxOrNull()
    return if (latest != null) {
        context.getString(
            R.string.page_settings_dex_index_last_indexed_at,
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(latest))
        )
    } else {
        context.getString(R.string.page_settings_dex_index_not_generated)
    }
}
