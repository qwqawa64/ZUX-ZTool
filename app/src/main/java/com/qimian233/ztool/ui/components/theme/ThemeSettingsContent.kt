package com.qimian233.ztool.ui.components.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolArgbColorTextFieldRow
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings

/**
 * Appearance previews followed by every theme setting row. The host owns the page
 * chrome and scrolling, so the content works in a settings screen and in the
 * first-run flow alike.
 */
@Composable
fun ThemeSettingsContent(
    settings: ZToolThemeSettings,
    manualSeedColorText: String,
    manualSeedColorError: Boolean,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialColorSpecChanged: (MaterialColorSpec) -> Unit,
    onMaterialPaletteChanged: (MaterialPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onUseAlternativeIconChanged: (Boolean) -> Unit,
    onPredictiveBackGestureChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarChanged: (Boolean) -> Unit,
    onEnableFloatingBottomBarBlurChanged: (Boolean) -> Unit,
    onManualColorChanged: (Boolean) -> Unit,
    onManualSeedColorTextChanged: (String) -> Unit,
    onManualSeedColorEditingFinished: () -> Unit,
    modifier: Modifier = Modifier,
    highlightRegistry: HighlightAnchorRegistry? = null,
    showSectionTitle: Boolean = true,
    bottomPadding: Dp = 32.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ThemePreviewCard(modifier = Modifier.padding(bottom = 16.dp))
        LauncherIconPreviewCard(
            useAlternativeIcon = settings.useAlternativeIcon,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ZToolSettingsList(
            highlightRegistry = highlightRegistry,
            sections = themeSettingsSections(
                settings = settings,
                manualSeedColorText = manualSeedColorText,
                manualSeedColorError = manualSeedColorError,
                sectionTitle = if (showSectionTitle) {
                    stringResource(R.string.page_settings_app_ui_theme_settings)
                } else {
                    null
                },
                onFrontendStyleChanged = onFrontendStyleChanged,
                onThemeModeChanged = onThemeModeChanged,
                onMaterialColorSpecChanged = onMaterialColorSpecChanged,
                onMaterialPaletteChanged = onMaterialPaletteChanged,
                onDynamicColorChanged = onDynamicColorChanged,
                onAmoledBlackChanged = onAmoledBlackChanged,
                onUseAlternativeIconChanged = onUseAlternativeIconChanged,
                onPredictiveBackGestureChanged = onPredictiveBackGestureChanged,
                onEnableFloatingBottomBarChanged = onEnableFloatingBottomBarChanged,
                onEnableFloatingBottomBarBlurChanged = onEnableFloatingBottomBarBlurChanged,
                onManualColorChanged = onManualColorChanged,
                onManualSeedColorTextChanged = onManualSeedColorTextChanged,
                onManualSeedColorEditingFinished = onManualSeedColorEditingFinished
            ),
            bottomPadding = bottomPadding
        )
    }
}

/** Builds the theme setting rows; a null [sectionTitle] drops the section header. */
@Composable
fun themeSettingsSections(
    settings: ZToolThemeSettings,
    manualSeedColorText: String,
    manualSeedColorError: Boolean,
    sectionTitle: String?,
    onFrontendStyleChanged: (FrontendStyle) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMaterialColorSpecChanged: (MaterialColorSpec) -> Unit,
    onMaterialPaletteChanged: (MaterialPalette) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledBlackChanged: (Boolean) -> Unit,
    onUseAlternativeIconChanged: (Boolean) -> Unit,
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
            title = sectionTitle,
            items = buildList {
                add(
                    SettingItem.Dropdown(
                        key = "theme_frontend_style",
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
                        key = "theme_theme_mode",
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
                        key = "theme_material_color_spec",
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
                        key = "theme_material_palette_mode",
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
                        key = "theme_use_alternative_icon",
                        title = stringResource(R.string.page_settings_use_alternative_icon_title),
                        checked = settings.useAlternativeIcon,
                        onCheckedChange = onUseAlternativeIconChanged,
                        icon = Icons.Rounded.Image
                    )
                )
                add(
                    SettingItem.Switch(
                        key = "theme_predictive_back_gesture",
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
                            key = "theme_amoled_black",
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
                        key = "theme_dynamic_color",
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
                        key = "theme_manual_color",
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
                            key = "theme_manual_seed_color",
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
                            key = "theme_enable_floating_bottom_bar",
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
                                key = "theme_floating_bottom_bar_blur",
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
