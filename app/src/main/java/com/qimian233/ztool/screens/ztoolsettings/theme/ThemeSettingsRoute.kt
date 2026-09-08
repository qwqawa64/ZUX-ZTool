package com.qimian233.ztool.screens.ztoolsettings.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.screens.ztoolsettings.rememberSettingsViewModel
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolArgbColorTextFieldRow
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalThemeRevealController
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import com.qimian233.ztool.viewmodel.SettingsUiState

@Composable
fun ThemeSettingsRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = rememberSettingsViewModel(activity)
    val uiState by viewModel.uiState.collectAsState()

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

    val revealController = LocalThemeRevealController.current

    ThemeSettingsScreen(
        state = uiState,
        onBack = onBack,
        onFrontendStyleChanged = { newStyle ->
            if (newStyle != uiState.themeSettings.frontendStyle) {
                revealController.triggerReveal(onAction = { viewModel.setFrontendStyle(newStyle) })
            }
        },
        onThemeModeChanged = viewModel::setThemeMode,
        onMaterialColorSpecChanged = viewModel::setMaterialColorSpec,
        onMaterialPaletteChanged = viewModel::setMaterialPalette,
        onDynamicColorChanged = viewModel::setDynamicColorEnabled,
        onAmoledBlackChanged = viewModel::setAmoledBlackEnabled,
        onPredictiveBackGestureChanged = viewModel::setPredictiveBackGestureEnabled,
        onEnableFloatingBottomBarChanged = viewModel::setEnableFloatingBottomBar,
        onEnableFloatingBottomBarBlurChanged = viewModel::setEnableFloatingBottomBarBlur,
        onManualColorChanged = viewModel::setManualColorEnabled,
        onManualSeedColorTextChanged = viewModel::setManualSeedColorText,
        onManualSeedColorEditingFinished = viewModel::finishManualSeedColorEditing,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    )

}

@Composable
private fun ThemeSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialColorSpecChanged: (MaterialColorSpec) -> Unit,
    onMaterialPaletteChanged: (MaterialPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onPredictiveBackGestureChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarBlurChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZToolScaffold(
        modifier = modifier,
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_settings_app_ui_theme_settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = themeSettingsSections(
                        settings = state.themeSettings,
                        manualSeedColorText = state.manualSeedColorText,
                        manualSeedColorError = state.manualSeedColorError,
                        onFrontendStyleChanged = onFrontendStyleChanged,
                        onThemeModeChanged = onThemeModeChanged,
                        onMaterialColorSpecChanged = onMaterialColorSpecChanged,
                        onMaterialPaletteChanged = onMaterialPaletteChanged,
                        onDynamicColorChanged = onDynamicColorChanged,
                        onAmoledBlackChanged = onAmoledBlackChanged,
                        onPredictiveBackGestureChanged = onPredictiveBackGestureChanged,
                        onEnableFloatingBottomBarChanged = onEnableFloatingBottomBarChanged,
                        onEnableFloatingBottomBarBlurChanged = onEnableFloatingBottomBarBlurChanged,
                        onManualColorChanged = onManualColorChanged,
                        onManualSeedColorTextChanged = onManualSeedColorTextChanged,
                        onManualSeedColorEditingFinished = onManualSeedColorEditingFinished
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

@Composable
private fun themeSettingsSections(
    settings: ZToolThemeSettings,
    manualSeedColorText: String,
    manualSeedColorError: Boolean,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialColorSpecChanged: (MaterialColorSpec) -> Unit,
    onMaterialPaletteChanged: (MaterialPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onPredictiveBackGestureChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarBlurChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit
) : List<SettingSection> {
    val frontendStyleOptions: List<LabeledOption<FrontendStyle>> = listOf(
        LabeledOption(
            value = FrontendStyle.Material3Expressive,
            label = stringResource(R.string.page_settings_frontend_style_material3)
        ),
        LabeledOption(
            value = FrontendStyle.Miuix,
            label = stringResource(R.string.page_settings_frontend_style_miuix)
        )
    )
    val themeModeOptions: List<LabeledOption<ThemeMode>> = listOf(
        LabeledOption(
            value = ThemeMode.FollowSystem,
            label = stringResource(R.string.page_settings_theme_mode_follow_system)
        ),
        LabeledOption(
            value = ThemeMode.Light,
            label = stringResource(R.string.page_settings_theme_mode_light)
        ),
        LabeledOption(
            value = ThemeMode.Dark,
            label = stringResource(R.string.page_settings_theme_mode_dark)
        )
    )
    val colorSpecOptions: List<LabeledOption<MaterialColorSpec>> = listOf(
        LabeledOption(
            value = MaterialColorSpec.Spec2021,
            label = stringResource(R.string.page_settings_material_color_spec_2021)
        ),
        LabeledOption(
            value = MaterialColorSpec.Spec2025,
            label = stringResource(R.string.page_settings_material_color_spec_2025)
        )
    )
    val paletteOptions: List<LabeledOption<MaterialPalette>> = listOf(
        LabeledOption(
            value = MaterialPalette.TonalSpot,
            label = stringResource(R.string.page_settings_material_palette_mode_tonal_spot)
        ),
        LabeledOption(
            value = MaterialPalette.Neutral,
            label = stringResource(R.string.page_settings_material_palette_mode_neutral)
        ),
        LabeledOption(
            value = MaterialPalette.Vibrant,
            label = stringResource(R.string.page_settings_material_palette_mode_vibrant)
        ),
        LabeledOption(
            value = MaterialPalette.Expressive,
            label = stringResource(R.string.page_settings_material_palette_mode_expressive)
        ),
        LabeledOption(
            value = MaterialPalette.Rainbow,
            label = stringResource(R.string.page_settings_material_palette_mode_rainbow)
        ),
        LabeledOption(
            value = MaterialPalette.FruitSalad,
            label = stringResource(R.string.page_settings_material_palette_mode_fruit_salad)
        ),
        LabeledOption(
            value = MaterialPalette.MonoChrome,
            label = stringResource(R.string.page_settings_material_palette_mode_mono_chrome)
        ),
        LabeledOption(
            value = MaterialPalette.Fidelity,
            label = stringResource(R.string.page_settings_material_palette_mode_fidelity)
        ),
        LabeledOption(
            value = MaterialPalette.Content,
            label = stringResource(R.string.page_settings_material_palette_mode_content)
        )
    )
    val selectedPaletteLabel = paletteOptions
        .firstOrNull { it.value == settings.materialPalette }
        ?.label
        ?: stringResource(R.string.page_settings_material_palette_mode_tonal_spot)

    return listOf(
        SettingSection(
            title = stringResource(R.string.page_settings_app_ui_theme_settings),
            items = buildList {
                add(
                    SettingItem.Dropdown(
                        key = "frontend_style",
                        label = stringResource(R.string.page_settings_frontend_style_title),
                        value = frontendStyleOptions.first { it.value == settings.frontendStyle }.label,
                        options = frontendStyleOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { onFrontendStyleChanged(it.value) },
                        icon = Icons.Rounded.Dashboard
                    )
                )
                add(
                    SettingItem.Dropdown(
                        key = "theme_mode",
                        label = stringResource(R.string.page_settings_theme_mode_title),
                        value = themeModeOptions.first { it.value == settings.themeMode }.label,
                        options = themeModeOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { onThemeModeChanged(it.value) },
                        icon = Icons.Rounded.DarkMode
                    )
                )
                add(
                    SettingItem.Dropdown(
                        key = "material_color_spec",
                        label = stringResource(R.string.page_settings_material_color_spec_title),
                        value = colorSpecOptions.first { it.value == settings.materialColorSpec }.label,
                        options = colorSpecOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { onMaterialColorSpecChanged(it.value) },
                        icon = Icons.Rounded.DesignServices
                    )
                )
                add(
                    SettingItem.Dropdown(
                        key = "material_palette_mode",
                        label = stringResource(R.string.page_settings_material_palette_mode_title),
                        value = selectedPaletteLabel,
                        options = paletteOptions,
                        optionLabel = { it.label },
                        onOptionSelected = { onMaterialPaletteChanged(it.value) },
                        icon = Icons.Rounded.Style
                    )
                )
                add(
                    SettingItem.Switch(
                        key = "predictive_back_gesture",
                        title = stringResource(R.string.page_settings_predictive_back_gesture_title),
                        summary = stringResource(R.string.page_settings_predictive_back_gesture_summary),
                        checked = settings.predictiveBackGestureEnabled,
                        onCheckedChange = onPredictiveBackGestureChanged,
                        icon = Icons.Rounded.Swipe
                    )
                )
                if (LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive) {
                    add(
                        SettingItem.Switch(
                            key = "amoled_black",
                            title = stringResource(R.string.page_settings_amoled_black_title),
                            summary = stringResource(R.string.page_settings_amoled_black_summary),
                            checked = settings.amoledBlackEnabled,
                            onCheckedChange = onAmoledBlackChanged,
                            icon = Icons.Rounded.Contrast
                        )
                    )
                }
                add(
                    SettingItem.Switch(
                        key = "dynamic_color",
                        title = stringResource(R.string.page_settings_dynamic_color_title),
                        summary = stringResource(R.string.page_settings_dynamic_color_summary),
                        checked = settings.dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChanged,
                        enabled = !settings.manualColorEnabled,
                        icon = Icons.Rounded.Colorize
                    )
                )
                add(
                    SettingItem.Switch(
                        key = "manual_color",
                        title = stringResource(R.string.page_settings_manual_color_title),
                        summary = stringResource(R.string.page_settings_manual_color_summary),
                        checked = settings.manualColorEnabled,
                        onCheckedChange = onManualColorChanged,
                        icon = Icons.Rounded.FormatColorFill
                    )
                )
                if (settings.manualColorEnabled) {
                    add(
                        SettingItem.Custom(
                            key = "manual_seed_color",
                            content = {
                                ManualSeedColorRow(
                                    color = settings.manualSeedColor,
                                    colorText = manualSeedColorText,
                                    isError = manualSeedColorError,
                                    onColorTextChanged = onManualSeedColorTextChanged,
                                    onEditingFinished = onManualSeedColorEditingFinished,
                                    icon = Icons.Rounded.FormatColorFill
                                )
                            }
                        )
                    )
                }
                // Miuix-only: Floating bottom bar switches
                if (settings.frontendStyle == FrontendStyle.Miuix) {
                    add(
                        SettingItem.Switch(
                            key = "enable_floating_bottom_bar",
                            title = stringResource(R.string.page_settings_enable_floating_bottom_bar_title),
                            summary = stringResource(R.string.page_settings_enable_floating_bottom_bar_summary),
                            checked = settings.enableFloatingBottomBar,
                            onCheckedChange = onEnableFloatingBottomBarChanged,
                            icon = Icons.Rounded.CallToAction
                        )
                    )
                    if (settings.enableFloatingBottomBar) {
                        add(
                            SettingItem.Switch(
                                key = "enable_floating_bottom_bar_blur",
                                title = stringResource(R.string.page_settings_enable_floating_bottom_bar_blur_title),
                                summary = stringResource(R.string.page_settings_enable_floating_bottom_bar_blur_summary),
                                checked = settings.enableFloatingBottomBarBlur,
                                onCheckedChange = onEnableFloatingBottomBarBlurChanged,
                                icon = Icons.Rounded.BlurOn
                            )
                        )
                    }
                }
            }
        )
    )
}

private data class LabeledOption<T>(
    val value: T,
    val label: String
)

@Composable
private fun ManualSeedColorRow(
    modifier: Modifier = Modifier,
    color: Long,
    colorText: String,
    isError: Boolean,
    onColorTextChanged: (String) -> Unit,
    onEditingFinished: () -> Unit,
    icon: ImageVector? = null,
) {
    ZToolArgbColorTextFieldRow(
        label = stringResource(R.string.page_settings_manual_seed_color_title),
        value = colorText,
        onValueChange = onColorTextChanged,
        defaultText = color.toULong().toString(16).padStart(8, '0').takeLast(8).uppercase(),
        summary = stringResource(R.string.page_settings_manual_seed_color_summary),
        errorText = if (isError) stringResource(R.string.page_settings_manual_seed_color_error) else null,
        onEditingFinished = onEditingFinished,
        icon = icon,
        modifier = modifier
    )
}
