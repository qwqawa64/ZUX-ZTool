package com.qimian233.ztool.settingactivity.systemframework

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolOutlinedTextField
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSliderRow
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.FrameworkSettingsUiState
import com.qimian233.ztool.viewmodel.FrameworkSettingsViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FrameworkSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("FrameworkSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            FrameworkSettingsViewModelFactory(
                FrameworkSettingsRepository(context.applicationContext)
            )
        )[FrameworkSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    FrameworkSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onRestart = viewModel::showRestartConfirmDialog,
        onKeepRotationChanged = viewModel::setKeepRotation,
        onAllowGetPackagesChanged = viewModel::setAllowGetPackages,
        onDisableFlagSecureChanged = viewModel::setDisableFlagSecure,
        onForceOnOffAnimationChanged = viewModel::setForceOnOffAnimation,
        onForceOnOffAnimationDurationChanged = viewModel::setOnOffScreenAnimationDuration,
        onAiInputExpandChanged = viewModel::setAiInputExpand,
        onAiInputSignsChanged = viewModel::setAiInputSigns,
        onShowAiInputInfo = viewModel::showAiInputInfoDialog,
        onNoPasswordPer24H = viewModel::setNoPasswordPer24H,
        onAllowUntrustedTouch = viewModel::setAllowUntrustedTouch,
    )

    if (uiState.showAiInputInfoDialog) {
        AiInputInfoDialog(
            onDismiss = viewModel::dismissAiInputInfoDialog
        )
    }
    val restartFailPrefix = stringResource(R.string.restart_fail_prefix)
    if (uiState.showRestartConfirmDialog) {
        RestartSystemDialog(
            onConfirm = {
                viewModel.restartSystem { error ->
                    Toast.makeText(
                        context,
                        restartFailPrefix + error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = viewModel::dismissRestartConfirmDialog
        )
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
    onForceOnOffAnimationChanged: (Boolean) -> Unit,
    onNoPasswordPer24H: (Boolean) -> Unit,
    onAllowUntrustedTouch: (Boolean) -> Unit,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit,
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
                onClick = onRestart,
                icon = {Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)},
                text = {Text(stringResource(R.string.restart_yes))})
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
                        onForceOnOffAnimationChanged = onForceOnOffAnimationChanged,
                        onForceOnOffAnimationDurationChanged = onForceOnOffAnimationDurationChanged,
                        onAiInputExpandChanged = onAiInputExpandChanged,
                        onAiInputSignsChanged = onAiInputSignsChanged,
                        onShowAiInputInfo = onShowAiInputInfo,
                        onNoPasswordPer24H = onNoPasswordPer24H,
                        onAllowUntrustedTouch = onAllowUntrustedTouch,
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
    onForceOnOffAnimationChanged: (Boolean) -> Unit,
    onNoPasswordPer24H: (Boolean) -> Unit,
    onAllowUntrustedTouch: (Boolean) -> Unit,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
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
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.no_password_per_24h),
                    summary = stringResource(R.string.no_password_per_24h_summary),
                    checked = state.noPasswordPer24H,
                    onCheckedChange = onNoPasswordPer24H
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.allow_untrusted_touch),
                    checked = state.allowUntrustedTouch,
                    onCheckedChange = onAllowUntrustedTouch
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.force_on_off_animation),
                    summary = stringResource(R.string.force_on_off_animation_summary),
                    checked = state.forceOnOffAnimation,
                    onCheckedChange = onForceOnOffAnimationChanged
                ),
                SettingItem.Custom(
                    content = {
                        ScreenOnOffAnimationDuration(
                            state = state,
                            onForceOnOffAnimationDurationChanged = onForceOnOffAnimationDurationChanged
                        )
                    }
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
private fun ScreenOnOffAnimationDuration(
    state: FrameworkSettingsUiState,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
) {
    if (state.forceOnOffAnimation) {
        ZToolSliderRow(
            title = stringResource(R.string.screen_on_off_animation_duration),
            value = state.forceOnOffAnimationDuration.toFloat(),
            valueText = state.forceOnOffAnimationDuration.toString() + "ms",
            onValueChange = {
                onForceOnOffAnimationDurationChanged(
                    snapToAnimationDuration(it)
                )
            },
            steps = ANIMATION_DURATION_STEPS,
            valueRange = 0f..1000f
        )
    }
}

private const val ANIMATION_DURATION_MIN_MS = 0
private const val ANIMATION_DURATION_MAX_MS = 1000
private const val ANIMATION_DURATION_STEP_MS = 50
private const val ANIMATION_DURATION_STEPS =
    (ANIMATION_DURATION_MAX_MS - ANIMATION_DURATION_MIN_MS) / ANIMATION_DURATION_STEP_MS - 1

private fun snapToAnimationDuration(value: Float): Int {
    return ((value / ANIMATION_DURATION_STEP_MS).roundToInt() * ANIMATION_DURATION_STEP_MS)
        .coerceIn(ANIMATION_DURATION_MIN_MS, ANIMATION_DURATION_MAX_MS)
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
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
        ZToolOutlinedTextField(
            value = state.aiInputSigns,
            onValueChange = onAiInputSignsChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            label = stringResource(R.string.custom_detector_hint),
            isError = state.aiInputSignsError != null,
            supportingText = state.aiInputSignsError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
            ZToolTextButton(
                enabled = countdown == 0,
                onClick = onConfirm,
                text = if (countdown > 0) {
                    stringResource(R.string.confirm) + " ($countdown)"
                } else {
                    stringResource(R.string.confirm)
                }
            )
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}
