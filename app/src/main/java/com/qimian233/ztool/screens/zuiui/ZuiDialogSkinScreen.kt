package com.qimian233.ztool.screens.zuiui

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.zuiui.PendingScopeApp
import com.qimian233.ztool.data.zuiui.ScopeRequestOutcome
import com.qimian233.ztool.data.zuiui.ZuiComponentScanRepository
import com.qimian233.ztool.data.zuiui.ZuiDialogSkinRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.ZuiComponentScanUiState
import com.qimian233.ztool.viewmodel.ZuiDialogSkinUiState
import com.qimian233.ztool.viewmodel.ZuiDialogSkinViewModel

@Composable
fun ZuiDialogSkinRoute(
    title: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("ZuiDialogSkinRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            ZuiDialogSkinViewModelFactory(
                ZuiDialogSkinRepository(context.applicationContext),
                ZuiComponentScanRepository(context.applicationContext)
            )
        )[ZuiDialogSkinViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
        viewModel.onEnterPage()
    }

    val uiState by viewModel.uiState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        ZuiDialogSkinScreen(
            title = title,
            state = uiState,
            scanState = scanState,
            onBack = onBack,
            onDialogSkinChanged = viewModel::setDialogSkinEnabled,
            onHideDividerChanged = viewModel::setHideDividerEnabled,
            onScan = viewModel::startScan,
            onCancelScan = viewModel::cancelScan,
            onOpenRecommendations = viewModel::openRecommendations,
            onApply = {
                viewModel.applyChanges(
                    onUnsupported = {
                        Toast.makeText(
                            context,
                            R.string.ui_components_reload_unsupported_message,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onResult = { outcome ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.ui_components_reload_result_message,
                                outcome.succeeded,
                                outcome.failed
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            },
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )

        if (scanState.showRecommendationDialog) {
            RecommendationDialog(
                pending = scanState.pending,
                onDismiss = viewModel::dismissRecommendations,
                onConfirm = {
                    viewModel.dismissRecommendations()
                    viewModel.applyRecommendedScope { outcome ->
                        val message = when (outcome) {
                            is ScopeRequestOutcome.Approved ->
                                context.getString(R.string.ui_components_recommend_approved)

                            is ScopeRequestOutcome.Failed -> context.getString(
                                R.string.ui_components_recommend_failed,
                                outcome.reason
                            )

                            ScopeRequestOutcome.Unavailable ->
                                context.getString(R.string.ui_components_recommend_unavailable)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

private class ZuiDialogSkinViewModelFactory(
    private val repository: ZuiDialogSkinRepository,
    private val scanRepository: ZuiComponentScanRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZuiDialogSkinViewModel::class.java)) {
            return ZuiDialogSkinViewModel(repository, scanRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun ZuiDialogSkinScreen(
    title: String,
    state: ZuiDialogSkinUiState,
    scanState: ZuiComponentScanUiState,
    onBack: () -> Unit,
    onDialogSkinChanged: (Boolean) -> Unit,
    onHideDividerChanged: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onApply: () -> Unit,
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
                onClick = onApply,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.ui_components_reload_button)) }
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
                    sections = zuiDialogSkinSections(
                        state = state,
                        scanState = scanState,
                        onDialogSkinChanged = onDialogSkinChanged,
                        onHideDividerChanged = onHideDividerChanged,
                        onScan = onScan,
                        onCancelScan = onCancelScan,
                        onOpenRecommendations = onOpenRecommendations
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun zuiDialogSkinSections(
    state: ZuiDialogSkinUiState,
    scanState: ZuiComponentScanUiState,
    onDialogSkinChanged: (Boolean) -> Unit,
    onHideDividerChanged: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenRecommendations: () -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            items = listOf(
                SettingItem.Custom(
                    key = "deco_ui_components_scope_hint",
                    content = { UiComponentsScopeHint() }
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.ui_components_app_name),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.ui_components_dialog_skin_title),
                    summary = stringResource(R.string.ui_components_dialog_skin_summary),
                    checked = state.dialogSkinEnabled,
                    onCheckedChange = onDialogSkinChanged,
                    key = "ui_components_dialog_skin"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.ui_components_dialog_hide_divider_title),
                    summary = stringResource(R.string.ui_components_dialog_hide_divider_summary),
                    checked = state.hideDividerEnabled,
                    onCheckedChange = onHideDividerChanged,
                    key = "ui_components_dialog_hide_divider"
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.ui_components_scope_section_title),
            items = buildList {
                add(
                    SettingItem.Action(
                        key = "ui_components_scan",
                        title = stringResource(R.string.ui_components_scan_title),
                        summary = scanSummary(scanState),
                        onClick = onScan,
                        enabled = !scanState.running,
                        icon = if (scanState.running) null else Icons.Rounded.Search,
                        trailingContent = if (scanState.running) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else null
                    )
                )
                if (scanState.running) {
                    add(
                        SettingItem.Action(
                            key = "ui_components_scan_cancel",
                            title = stringResource(R.string.ui_components_scan_cancel_title),
                            summary = stringResource(R.string.ui_components_scan_cancel_summary),
                            onClick = onCancelScan
                        )
                    )
                } else if (scanState.pending.isNotEmpty()) {
                    add(
                        SettingItem.Action(
                            key = "ui_components_scope_pending",
                            title = stringResource(R.string.ui_components_scope_pending_title),
                            summary = stringResource(
                                R.string.ui_components_scope_pending_summary,
                                scanState.pending.size
                            ),
                            onClick = onOpenRecommendations
                        )
                    )
                }
            }
        )
    )
}

@Composable
private fun scanSummary(scanState: ZuiComponentScanUiState): String = when {
    scanState.running -> stringResource(
        R.string.ui_components_scan_summary_running,
        scanState.scanned,
        scanState.total
    )

    !scanState.hasScannedOnce -> stringResource(R.string.ui_components_scan_summary_idle)

    else -> stringResource(
        R.string.ui_components_scan_summary_done,
        scanState.scannedApps,
        scanState.zuiApps
    )
}

@Composable
private fun RecommendationDialog(
    pending: List<PendingScopeApp>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_components_recommend_title)) },
        text = {
            if (pending.isEmpty()) {
                Text(stringResource(R.string.ui_components_recommend_empty))
            } else {
                Column {
                    Text(
                        stringResource(
                            R.string.ui_components_recommend_message,
                            pending.size
                        )
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        pending.forEach { app ->
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pending.isEmpty()) {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.common_confirm)
                )
            } else {
                ZToolTextButton(
                    onClick = onConfirm,
                    text = stringResource(R.string.common_confirm)
                )
            }
        },
        dismissButton = if (pending.isEmpty()) {
            null
        } else {
            {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.common_cancel),
                    isPrimary = false
                )
            }
        }
    )
}

@Composable
private fun UiComponentsScopeHint() {
    Text(
        text = stringResource(R.string.ui_components_scope_hint),
        style = MaterialTheme.typography.bodySmall,
        color = LocalZToolColorScheme.current.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
