package com.qimian233.ztool

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolDropdownField
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.ExpressiveSectionItems
import com.qimian233.ztool.ui.components.materialExpressiveSettingsSectionColor
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.ui.theme.MaterialPaletteMode
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import com.qimian233.ztool.viewmodel.SettingsUiState
import com.qimian233.ztool.viewmodel.SettingsViewModel

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsMainRoute() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val viewModel = remember {
        val repository = SettingsRepository(context.applicationContext)
        ViewModelProvider(
            activity,
            SettingsViewModelFactory(repository)
        )[SettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupConfig(uri) { result ->
                activity.runOnUiThread {
                    if (result) {
                        showSettingsToast(context, context.getString(R.string.config_backup_success))
                    }
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreConfig(uri) { result ->
                activity.runOnUiThread {
                    if (result) {
                        showSettingsToast(context, context.getString(R.string.config_restore_success))
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
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

    SettingsRoute(
        state = uiState,
        onBackup = { backupLauncher.launch(viewModel.backupFileName()) },
        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
        onRestoreDefault = viewModel::showRestoreConfirmDialog,
        onLogServiceChanged = {
            viewModel.setLogServiceEnabled(it)
            showSettingsToast(
                context,
                context.getString(
                    if (it) R.string.log_service_started else R.string.log_service_stopped
                )
            )
        },
        onDetailedLoggingChanged = viewModel::setDetailedLoggingEnabled,
        onHomepageYiyanChanged = viewModel::setHomepageYiyanEnabled,
        onFrontendStyleChanged = viewModel::setFrontendStyle,
        onThemeModeChanged = viewModel::setThemeMode,
        onMaterialPaletteModeChanged = viewModel::setMaterialPaletteMode,
        onDynamicColorChanged = viewModel::setDynamicColorEnabled,
        onAmoledBlackChanged = viewModel::setAmoledBlackEnabled,
        onManualColorChanged = viewModel::setManualColorEnabled,
        onManualSeedColorTextChanged = viewModel::setManualSeedColorText,
        onManualSeedColorEditingFinished = viewModel::finishManualSeedColorEditing,
        onAbout = viewModel::showAboutDialog
    )

    if (uiState.showRestoreConfirmDialog) {
        RestoreDefaultDialog(
            onConfirm = {
                viewModel.restoreDefaultConfig()
                showSettingsToast(context, context.getString(R.string.default_config_restored))
            },
            onDismiss = viewModel::dismissRestoreConfirmDialog
        )
    }

    if (uiState.showAboutDialog) {
        AboutDialog(
            version = uiState.moduleVersion,
            onDismiss = viewModel::dismissAboutDialog,
            onOpenGithub = {
                openSettingsExternalLink(context, "https://github.com/qwqawa64/ZUX-ZTool", false, "")
            },
            onOpenCredits = {
                openSettingsExternalLink(context, "https://github.com/dantmnf/UnfuckZUI", false, "")
            },
            onOpenAuthor = {
                openSettingsExternalLink(context, "http://www.coolapk.com/u/10099756", true, "com.coolapk.market")
            },
            onOpenCollaborator = {
                openSettingsExternalLink(context, "http://www.coolapk.com/u/18634835", true, "com.coolapk.market")
            }
        )
    }
}

private fun openSettingsExternalLink(
    context: android.content.Context,
    link: String,
    shouldDeterminePackage: Boolean,
    packageName: String
) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, link.toUri()).apply {
                if (shouldDeterminePackage) setPackage(packageName)
            }
        )
    } catch (_: Exception) {
        showSettingsToast(context, context.getString(R.string.open_web_link_failed))
    }
}

private fun showSettingsToast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SettingsRoute(
    state: SettingsUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreDefault: () -> Unit,
    onLogServiceChanged: (Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onHomepageYiyanChanged: (Boolean) -> Unit,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialPaletteModeChanged: (MaterialPaletteMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit,
    onAbout: () -> Unit
) {
    ZToolScaffold (
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.settingsFragment_title),
                addNavIcon = false
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
                .padding(horizontal = 32.dp, vertical = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.backupAndRestore)) {
                ExpressiveSectionItems(count = 3) { itemModifier ->
                    SettingsActionRow(
                        title = stringResource(R.string.backupConfigToFile),
                        onClick = onBackup,
                        modifier = itemModifier()
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.restoreConfigFromFile),
                        onClick = onRestore,
                        modifier = itemModifier()
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.restoreDefaultConfig),
                        onClick = onRestoreDefault,
                        modifier = itemModifier()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ThemeSettingsSection(
                settings = state.themeSettings,
                manualSeedColorText = state.manualSeedColorText,
                manualSeedColorError = state.manualSeedColorError,
                onFrontendStyleChanged = onFrontendStyleChanged,
                onThemeModeChanged = onThemeModeChanged,
                onMaterialPaletteModeChanged = onMaterialPaletteModeChanged,
                onDynamicColorChanged = onDynamicColorChanged,
                onAmoledBlackChanged = onAmoledBlackChanged,
                onManualColorChanged = onManualColorChanged,
                onManualSeedColorTextChanged = onManualSeedColorTextChanged,
                onManualSeedColorEditingFinished = onManualSeedColorEditingFinished
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.moreSettings)) {
                ExpressiveSectionItems(count = 3) { itemModifier ->
                    ZToolSwitchRow(
                        title = stringResource(R.string.enableLogService),
                        summary = stringResource(R.string.enableLogServiceDescription),
                        checked = state.isLogServiceEnabled,
                        onCheckedChange = onLogServiceChanged,
                        modifier = itemModifier()
                    )
                    ZToolSwitchRow(
                        title = stringResource(R.string.enableDetailedLogging),
                        summary = stringResource(R.string.enableDetailedLoggingDescription),
                        checked = state.isDetailedLoggingEnabled,
                        onCheckedChange = onDetailedLoggingChanged,
                        modifier = itemModifier()
                    )
                    ZToolSwitchRow(
                        title = stringResource(R.string.enableHomePageYiyan),
                        summary = stringResource(R.string.enableHomePageYiyanSummary),
                        checked = state.isHomepageYiyanEnabled,
                        onCheckedChange = onHomepageYiyanChanged,
                        modifier = itemModifier()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection {
                ExpressiveSectionItems(count = 1) { itemModifier ->
                    SettingsActionRow(
                        title = stringResource(R.string.showAboutPage),
                        onClick = onAbout,
                        modifier = itemModifier()
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }
}

@Composable
private fun ThemeSettingsSection(
    settings: ZToolThemeSettings,
    manualSeedColorText: String,
    manualSeedColorError: Boolean,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialPaletteModeChanged: (MaterialPaletteMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit
) {
    val frontendStyleOptions = listOf(
        LabeledOption(
            value = FrontendStyle.Material3Expressive,
            label = stringResource(R.string.frontend_style_material3)
        ),
        LabeledOption(
            value = FrontendStyle.Miuix,
            label = stringResource(R.string.frontend_style_miuix)
        )
    )
    val themeModeOptions = listOf(
        LabeledOption(
            value = ThemeMode.FollowSystem,
            label = stringResource(R.string.theme_mode_follow_system)
        ),
        LabeledOption(
            value = ThemeMode.Light,
            label = stringResource(R.string.theme_mode_light)
        ),
        LabeledOption(
            value = ThemeMode.Dark,
            label = stringResource(R.string.theme_mode_dark)
        )
    )
    val paletteModeOptions = listOf(
        LabeledOption(
            value = MaterialPaletteMode.MaterialYou2021,
            label = stringResource(R.string.material_palette_mode_2021)
        ),
        LabeledOption(
            value = MaterialPaletteMode.Expressive2025,
            label = stringResource(R.string.material_palette_mode_2025)
        )
    )

    SettingsSection(title = stringResource(R.string.app_ui_theme_settings)) {
        val themeItemCount = 5 +
            if (settings.frontendStyle == FrontendStyle.Material3Expressive) 1 else 0 +
            if (settings.manualColorEnabled) 1 else 0
        ExpressiveSectionItems(count = themeItemCount) { itemModifier ->
            DropdownSettingRow(
                title = stringResource(R.string.frontend_style_title),
                value = frontendStyleOptions.first { it.value == settings.frontendStyle }.label,
                options = frontendStyleOptions,
                optionLabel = { it.label },
                onOptionSelected = { onFrontendStyleChanged(it.value) },
                modifier = itemModifier()
            )
            DropdownSettingRow(
                title = stringResource(R.string.theme_mode_title),
                value = themeModeOptions.first { it.value == settings.themeMode }.label,
                options = themeModeOptions,
                optionLabel = { it.label },
                onOptionSelected = { onThemeModeChanged(it.value) },
                modifier = itemModifier()
            )
            if (settings.frontendStyle == FrontendStyle.Material3Expressive) {
                DropdownSettingRow(
                    title = stringResource(R.string.material_palette_mode_title),
                    value = paletteModeOptions.first { it.value == settings.materialPaletteMode }.label,
                    options = paletteModeOptions,
                    optionLabel = { it.label },
                    onOptionSelected = { onMaterialPaletteModeChanged(it.value) },
                    modifier = itemModifier()
                )
            }
            ZToolSwitchRow(
                title = stringResource(R.string.dynamic_color_title),
                summary = stringResource(R.string.dynamic_color_summary),
                checked = settings.dynamicColorEnabled,
                onCheckedChange = onDynamicColorChanged,
                enabled = !settings.manualColorEnabled,
                modifier = itemModifier()
            )
            ZToolSwitchRow(
                title = stringResource(R.string.manual_color_title),
                summary = stringResource(R.string.manual_color_summary),
                checked = settings.manualColorEnabled,
                onCheckedChange = onManualColorChanged,
                modifier = itemModifier()
            )
            if (settings.manualColorEnabled) {
                ManualSeedColorRow(
                    color = settings.manualSeedColor,
                    colorText = manualSeedColorText,
                    isError = manualSeedColorError,
                    onColorTextChanged = onManualSeedColorTextChanged,
                    onEditingFinished = onManualSeedColorEditingFinished,
                    modifier = itemModifier()
                )
            }
            ZToolSwitchRow(
                title = stringResource(R.string.amoled_black_title),
                summary = stringResource(R.string.amoled_black_summary),
                checked = settings.amoledBlackEnabled,
                onCheckedChange = onAmoledBlackChanged,
                modifier = itemModifier()
            )
        }
    }
}

private data class LabeledOption<T>(
    val value: T,
    val label: String
)

@Composable
private fun <T> DropdownSettingRow(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        ZToolDropdownField(
            label = "",
            value = value,
            options = options,
            optionLabel = optionLabel,
            onOptionSelected = onOptionSelected,
            modifier = Modifier.widthIn(min = 160.dp, max = 220.dp)
        )
    }
}

@Composable
private fun ManualSeedColorRow(
    color: Long,
    colorText: String,
    isError: Boolean,
    onColorTextChanged: (String) -> Unit,
    onEditingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .background(Color(color), RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedTextField(
            value = colorText,
            onValueChange = onColorTextChanged,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onEditingFinished()
                    }
                },
            label = { Text(stringResource(R.string.manual_seed_color_title)) },
            supportingText = {
                if (isError) {
                    Text(stringResource(R.string.manual_seed_color_error))
                } else {
                    Text(stringResource(R.string.manual_seed_color_summary))
                }
            },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    onEditingFinished()
                    focusManager.clearFocus()
                }
            )
        )
    }
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isExpressive = LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isExpressive) {
            materialExpressiveSettingsSectionColor()
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isExpressive) 12.dp else 0.dp)
                .padding(vertical = if (isExpressive) 0.dp else 12.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RestoreDefaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.final_confirmation_title)) },
        text = { Text(stringResource(R.string.restore_default_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restart_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}

@Composable
private fun AboutDialog(
    version: String,
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenCollaborator: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_ztool_title)) },
        text = {
            Column {
                Text(
                    text = version,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_description)
                        .replace("<br>", "\n")
                        .replace("&lt;br&gt;", "\n"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenGithub
                ) {
                    Text(stringResource(R.string.button_project_homepage))
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenCredits
                ) {
                    Text("Credits")
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenAuthor
                ) {
                    Text("Qimian233")
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenCollaborator
                ) {
                    Text("WASD")
                }
            }
        }
    )
}
