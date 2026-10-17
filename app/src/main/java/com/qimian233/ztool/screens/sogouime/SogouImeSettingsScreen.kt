package com.qimian233.ztool.screens.sogouime

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
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
import com.qimian233.ztool.data.sogouime.SogouEnterKeyMode
import com.qimian233.ztool.data.sogouime.SogouImeSettingsRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.SogouImeSettingsViewModel

@Composable
fun SogouImeSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SogouImeSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SogouImeSettingsViewModelFactory(
                SogouImeSettingsRepository(context.applicationContext)
            )
        )[SogouImeSettingsViewModel::class.java]
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
        SogouImeSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onHalfWidthPunctChanged = viewModel::setHalfWidthPunct,
            onHalfWidthPunctSignsChanged = viewModel::setHalfWidthPunctSigns,
            onEnterKeyEnabledChanged = viewModel::setEnterKeyEnabled,
            onEnterKeyModeChanged = viewModel::setEnterKeyMode,
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

private class SogouImeSettingsViewModelFactory(
    private val repository: SogouImeSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SogouImeSettingsViewModel::class.java)) {
            return SogouImeSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SogouImeSettingsScreen(
    title: String,
    state: com.qimian233.ztool.viewmodel.SogouImeSettingsUiState,
    onBack: () -> Unit,
    onHalfWidthPunctChanged: (Boolean) -> Unit,
    onHalfWidthPunctSignsChanged: (String) -> Unit,
    onEnterKeyEnabledChanged: (Boolean) -> Unit,
    onEnterKeyModeChanged: (SogouEnterKeyMode) -> Unit,
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
                    sections = sogouImeSettingsSections(
                        state = state,
                        onHalfWidthPunctChanged = onHalfWidthPunctChanged,
                        onHalfWidthPunctSignsChanged = onHalfWidthPunctSignsChanged,
                        onEnterKeyEnabledChanged = onEnterKeyEnabledChanged,
                        onEnterKeyModeChanged = onEnterKeyModeChanged
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun sogouImeSettingsSections(
    state: com.qimian233.ztool.viewmodel.SogouImeSettingsUiState,
    onHalfWidthPunctChanged: (Boolean) -> Unit,
    onHalfWidthPunctSignsChanged: (String) -> Unit,
    onEnterKeyEnabledChanged: (Boolean) -> Unit,
    onEnterKeyModeChanged: (SogouEnterKeyMode) -> Unit
): List<SettingSection> {
    val modeLabels = mapOf(
        SogouEnterKeyMode.EnterSend to stringResource(R.string.sogou_ime_enter_key_mode_send),
        SogouEnterKeyMode.EnterNewline to stringResource(R.string.sogou_ime_enter_key_mode_newline)
    )
    return listOf(
        com.qimian233.ztool.ui.components.SettingSection(
            title = stringResource(R.string.sogou_ime_punct_section_title),
            items = listOf(
                com.qimian233.ztool.ui.components.SettingItem.Custom(
                    content = {
                        HalfWidthPunctSettingsContent(
                            state = state,
                            onHalfWidthPunctChanged = onHalfWidthPunctChanged,
                            onHalfWidthPunctSignsChanged = onHalfWidthPunctSignsChanged
                        )
                    },
                    key = "sogou_ime_half_width_punct"
                )
            )
        ),
        com.qimian233.ztool.ui.components.SettingSection(
            title = stringResource(R.string.sogou_ime_enter_key_section_title),
            items = buildList {
                add(
                    com.qimian233.ztool.ui.components.SettingItem.Switch(
                        title = stringResource(R.string.sogou_ime_enter_key_title),
                        summary = stringResource(R.string.sogou_ime_enter_key_summary),
                        checked = state.enterKeyEnabled,
                        onCheckedChange = onEnterKeyEnabledChanged,
                        key = "sogou_ime_enter_key"
                    )
                )
                if (state.enterKeyEnabled) {
                    add(
                        com.qimian233.ztool.ui.components.SettingItem.Dropdown(
                            label = stringResource(R.string.sogou_ime_enter_key_mode_title),
                            value = modeLabels.getValue(state.enterKeyMode),
                            options = SogouEnterKeyMode.entries,
                            optionLabel = { modeLabels.getValue(it) },
                            onOptionSelected = onEnterKeyModeChanged,
                            summary = stringResource(R.string.sogou_ime_enter_key_mode_summary),
                            key = "sogou_ime_enter_key_mode"
                        )
                    )
                }
            }
        )
    )
}
