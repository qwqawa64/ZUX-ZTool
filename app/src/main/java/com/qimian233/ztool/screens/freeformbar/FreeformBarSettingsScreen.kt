package com.qimian233.ztool.screens.freeformbar

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
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
import com.qimian233.ztool.data.freeformbar.FreeformBarSettingsRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.FreeformBarSettingsUiState
import com.qimian233.ztool.viewmodel.FreeformBarSettingsViewModel

@Composable
fun FreeformBarSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("FreeformBarSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            FreeformBarSettingsViewModelFactory(
                FreeformBarSettingsRepository(context.applicationContext)
            )
        )[FreeformBarSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
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
        FreeformBarSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onEnhanceChanged = viewModel::setEnhance,
            onDrawerSlotsChanged = viewModel::setDrawerSlots,
            onRecentSlotsChanged = viewModel::setRecentSlots,
            onRestartScope = viewModel::showRestartDialog,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    if (uiState.showRestartDialog) {
        ZToolDialog(
            onDismissRequest = viewModel::dismissRestartDialog,
            title = { Text(stringResource(R.string.common_restart_xp_title)) },
            text = {
                Text(
                    stringResource(R.string.common_restart_xp_message_header) +
                            packageName + " " +
                            stringResource(R.string.common_restart_xp_message)
                )
            },
            confirmButton = {
                ZToolTextButton(
                    onClick = {
                        viewModel.restartScope(
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    R.string.system_update_restart_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    },
                    text = stringResource(R.string.common_restart_yes)
                )
            },
            dismissButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissRestartDialog,
                    text = stringResource(R.string.common_restart_no),
                    isPrimary = false
                )
            }
        )
    }
}

private class FreeformBarSettingsViewModelFactory(
    private val repository: FreeformBarSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FreeformBarSettingsViewModel::class.java)) {
            return FreeformBarSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun FreeformBarSettingsScreen(
    title: String,
    state: FreeformBarSettingsUiState,
    onBack: () -> Unit,
    onEnhanceChanged: (Boolean) -> Unit,
    onDrawerSlotsChanged: (Int) -> Unit,
    onRecentSlotsChanged: (Int) -> Unit,
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
                    sections = freeformBarSettingsSections(
                        state = state,
                        onEnhanceChanged = onEnhanceChanged,
                        onDrawerSlotsChanged = onDrawerSlotsChanged,
                        onRecentSlotsChanged = onRecentSlotsChanged
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun freeformBarSettingsSections(
    state: FreeformBarSettingsUiState,
    onEnhanceChanged: (Boolean) -> Unit,
    onDrawerSlotsChanged: (Int) -> Unit,
    onRecentSlotsChanged: (Int) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.freeform_sidebar_drawer_section_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.freeform_sidebar_drawer_enhance_title),
                        summary = stringResource(R.string.freeform_sidebar_drawer_enhance_summary),
                        checked = state.enhanceEnabled,
                        onCheckedChange = onEnhanceChanged,
                        key = "sidebar_drawer_enhance"
                    )
                )
                if (state.enhanceEnabled) {
                    add(
                        SettingItem.Slider(
                            title = stringResource(R.string.freeform_sidebar_drawer_slots_title),
                            summary = stringResource(R.string.freeform_sidebar_drawer_slots_summary),
                            value = state.drawerSlots.toFloat(),
                            valueText = state.drawerSlots.toString(),
                            valueRange = FreeformBarSettingsRepository.SLOTS_MIN.toFloat()..
                                FreeformBarSettingsRepository.SLOTS_MAX.toFloat(),
                            steps = FreeformBarSettingsRepository.SLOTS_MAX -
                                FreeformBarSettingsRepository.SLOTS_MIN - 1,
                            onValueChange = { onDrawerSlotsChanged(it.toInt()) },
                            key = "sidebar_drawer_slots"
                        )
                    )
                    add(
                        SettingItem.Slider(
                            title = stringResource(R.string.freeform_sidebar_drawer_recent_slots_title),
                            summary = stringResource(R.string.freeform_sidebar_drawer_recent_slots_summary),
                            value = state.recentSlots.toFloat(),
                            valueText = state.recentSlots.toString(),
                            valueRange = FreeformBarSettingsRepository.RECENT_SLOTS_MIN.toFloat()..
                                FreeformBarSettingsRepository.RECENT_SLOTS_MAX.toFloat(),
                            steps = FreeformBarSettingsRepository.RECENT_SLOTS_MAX -
                                FreeformBarSettingsRepository.RECENT_SLOTS_MIN - 1,
                            onValueChange = { onRecentSlotsChanged(it.toInt()) },
                            key = "sidebar_drawer_recent_slots"
                        )
                    )
                }
                add(
                    SettingItem.Custom(
                        key = "deco_freeform_sidebar_drawer_usage",
                        content = { FreeformBarUsageNote() }
                    )
                )
            }
        )
    )
}

@Composable
private fun FreeformBarUsageNote() {
    Text(
        text = stringResource(R.string.freeform_sidebar_drawer_usage_summary),
        style = MaterialTheme.typography.bodySmall,
        color = LocalZToolColorScheme.current.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
