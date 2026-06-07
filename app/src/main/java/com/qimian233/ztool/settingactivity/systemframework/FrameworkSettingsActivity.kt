package com.qimian233.ztool.settingactivity.systemframework

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.FrameworkSettingsUiState
import com.qimian233.ztool.viewmodel.FrameworkSettingsViewModel
import kotlinx.coroutines.delay

class FrameworkSettingsActivity : ComponentActivity() {

    private lateinit var viewModel: FrameworkSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        val repository = FrameworkSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            FrameworkSettingsViewModelFactory(repository)
        )[FrameworkSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                FrameworkSettingsScreen(
                    title = appName,
                    state = uiState,
                    onBack = ::finish,
                    onRestart = viewModel::showRestartConfirmDialog,
                    onKeepRotationChanged = viewModel::setKeepRotation,
                    onAllowGetPackagesChanged = viewModel::setAllowGetPackages,
                    onDisableFlagSecureChanged = viewModel::setDisableFlagSecure,
                    onAiInputExpandChanged = viewModel::setAiInputExpand,
                    onAiInputSignsChanged = viewModel::setAiInputSigns,
                    onShowAiInputInfo = viewModel::showAiInputInfoDialog
                )

                if (uiState.showAiInputInfoDialog) {
                    AiInputInfoDialog(
                        onDismiss = viewModel::dismissAiInputInfoDialog
                    )
                }

                if (uiState.showRestartConfirmDialog) {
                    RestartSystemDialog(
                        onConfirm = {
                            viewModel.restartSystem(::showRestartFailure)
                        },
                        onDismiss = viewModel::dismissRestartConfirmDialog
                    )
                }
            }
        }
    }

    private fun showRestartFailure(error: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                getString(R.string.restart_fail_prefix) + error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private class FrameworkSettingsViewModelFactory(
    private val repository: FrameworkSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FrameworkSettingsViewModel::class.java)) {
            return FrameworkSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun FrameworkSettingsScreen(
    title: String,
    state: FrameworkSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onKeepRotationChanged: (Boolean) -> Unit,
    onAllowGetPackagesChanged: (Boolean) -> Unit,
    onDisableFlagSecureChanged: (Boolean) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
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
            FloatingActionButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null
                )
            }
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
                    sections = frameworkSettingsSections(
                        state = state,
                        onKeepRotationChanged = onKeepRotationChanged,
                        onAllowGetPackagesChanged = onAllowGetPackagesChanged,
                        onDisableFlagSecureChanged = onDisableFlagSecureChanged,
                        onAiInputExpandChanged = onAiInputExpandChanged,
                        onAiInputSignsChanged = onAiInputSignsChanged,
                        onShowAiInputInfo = onShowAiInputInfo
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun frameworkSettingsSections(
    state: FrameworkSettingsUiState,
    onKeepRotationChanged: (Boolean) -> Unit,
    onAllowGetPackagesChanged: (Boolean) -> Unit,
    onDisableFlagSecureChanged: (Boolean) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.keep_rotation_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.keep_rotation_enable_title),
                    summary = stringResource(R.string.keep_rotation_enable_summary),
                    checked = state.keepRotation,
                    onCheckedChange = onKeepRotationChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.disable_zui_applist_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.disable_zui_applist_enable_title),
                    summary = stringResource(R.string.disable_zui_applist_enable_summary),
                    checked = state.allowGetPackages,
                    onCheckedChange = onAllowGetPackagesChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.disable_flag_secure_title),
                    summary = stringResource(R.string.disable_flag_secure_summary),
                    checked = state.disableFlagSecure,
                    onCheckedChange = onDisableFlagSecureChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.ai_input_Title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        AiInputSettingsContent(
                            state = state,
                            onAiInputExpandChanged = onAiInputExpandChanged,
                            onAiInputSignsChanged = onAiInputSignsChanged,
                            onShowAiInputInfo = onShowAiInputInfo
                        )
                    }
                )
            )
        )
    )
}

@Composable
private fun AiInputSettingsContent(
    state: FrameworkSettingsUiState,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
) {
    ZToolSwitchRow(
        title = stringResource(R.string.ai_input_expand_Title),
        summary = stringResource(R.string.ai_input_expand_summary),
        checked = state.aiInputExpand,
        onCheckedChange = onAiInputExpandChanged
    )
    if (state.aiInputExpand) {
        IconButton(
            onClick = onShowAiInputInfo,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = state.aiInputSigns,
            onValueChange = onAiInputSignsChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            label = { Text(stringResource(R.string.custom_detector_hint)) },
            isError = state.aiInputSignsError != null,
            supportingText = {
                if (state.aiInputSignsError != null) {
                    Text(state.aiInputSignsError)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            minLines = 1,
            maxLines = 3
        )
    }
}

@Composable
private fun AiInputInfoDialog(
    onDismiss: () -> Unit
) {
    ZToolQuickHelpDialog(
        title = stringResource(R.string.ai_input_quick_help_title),
        summary = stringResource(R.string.ai_input_quick_help_summary),
        quickLabel = stringResource(R.string.quick_help_lookup_title),
        examplesLabel = stringResource(R.string.quick_help_examples_title),
        items = listOf(
            QuickHelpItem(
                stringResource(R.string.ai_input_quick_help_scope),
                stringResource(R.string.ai_input_quick_help_scope_desc)
            ),
            QuickHelpItem(
                stringResource(R.string.ai_input_quick_help_separator),
                stringResource(R.string.ai_input_quick_help_separator_desc)
            ),
            QuickHelpItem(
                stringResource(R.string.ai_input_quick_help_conflict),
                stringResource(R.string.ai_input_quick_help_conflict_desc)
            )
        ),
        examples = listOf(
            QuickHelpExample(
                stringResource(R.string.ai_input_quick_help_example_default_value),
                stringResource(R.string.ai_input_quick_help_example_default)
            ),
            QuickHelpExample(
                stringResource(R.string.ai_input_quick_help_example_custom_value),
                stringResource(R.string.ai_input_quick_help_example_custom)
            )
        ),
        note = stringResource(R.string.ai_input_quick_help_note),
        onDismiss = onDismiss,
        confirmButtonText = stringResource(android.R.string.ok)
    )
}

@Composable
private fun RestartSystemDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_system_title)) },
        text = { Text(stringResource(R.string.restart_system_message)) },
        confirmButton = {
            TextButton(
                enabled = countdown == 0,
                onClick = onConfirm
            ) {
                Text(
                    if (countdown > 0) {
                        stringResource(R.string.confirm) + " ($countdown)"
                    } else {
                        stringResource(R.string.confirm)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
