package com.qimian233.ztool.data.theme

import android.content.Context
import android.content.SharedPreferences
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import androidx.core.content.edit

class ThemePreferencesRepository(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): ZToolThemeSettings {
        val legacyPaletteMode = prefs.getString(KEY_MATERIAL_PALETTE_MODE, null)
        return ZToolThemeSettings(
            frontendStyle = prefs.getEnum(KEY_FRONTEND_STYLE, FrontendStyle.Material3Expressive),
            themeMode = prefs.getEnum(KEY_THEME_MODE, ThemeMode.FollowSystem),
            materialColorSpec = prefs.getEnum(
                KEY_MATERIAL_COLOR_SPEC,
                legacyColorSpec(legacyPaletteMode)
            ),
            materialPalette = prefs.getEnum(
                KEY_MATERIAL_PALETTE,
                legacyPalette(legacyPaletteMode)
            ),
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, true),
            amoledBlackEnabled = prefs.getBoolean(KEY_AMOLED_BLACK_ENABLED, false),
            predictiveBackGestureEnabled = prefs.getBoolean(KEY_PREDICTIVE_BACK_GESTURE_ENABLED, true),
            manualColorEnabled = prefs.getBoolean(KEY_MANUAL_COLOR_ENABLED, false),
            manualSeedColor = prefs.getLong(
                KEY_MANUAL_SEED_COLOR,
                ZToolThemeSettings.DEFAULT_MANUAL_SEED_COLOR
            )
        )
    }

    @Suppress("unused")
    fun saveSettings(settings: ZToolThemeSettings) {
        prefs.edit {
            putString(KEY_FRONTEND_STYLE, settings.frontendStyle.name)
                .putString(KEY_THEME_MODE, settings.themeMode.name)
                .putString(KEY_MATERIAL_COLOR_SPEC, settings.materialColorSpec.name)
                .putString(KEY_MATERIAL_PALETTE, settings.materialPalette.name)
                .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, settings.dynamicColorEnabled)
                .putBoolean(KEY_AMOLED_BLACK_ENABLED, settings.amoledBlackEnabled)
                .putBoolean(KEY_PREDICTIVE_BACK_GESTURE_ENABLED, settings.predictiveBackGestureEnabled)
                .putBoolean(KEY_MANUAL_COLOR_ENABLED, settings.manualColorEnabled)
                .putLong(KEY_MANUAL_SEED_COLOR, settings.manualSeedColor)
        }
    }

    fun saveFrontendStyle(style: FrontendStyle) {
        prefs.edit { putString(KEY_FRONTEND_STYLE, style.name) }
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun saveMaterialColorSpec(spec: MaterialColorSpec) {
        prefs.edit { putString(KEY_MATERIAL_COLOR_SPEC, spec.name) }
    }

    fun saveMaterialPalette(palette: MaterialPalette) {
        prefs.edit { putString(KEY_MATERIAL_PALETTE, palette.name) }
    }

    fun saveDynamicColorEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR_ENABLED, enabled) }
    }

    fun saveAmoledBlackEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_BLACK_ENABLED, enabled) }
    }

    fun savePredictiveBackGestureEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PREDICTIVE_BACK_GESTURE_ENABLED, enabled) }
    }

    fun saveManualColorEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MANUAL_COLOR_ENABLED, enabled) }
    }

    fun saveManualSeedColor(color: Long) {
        prefs.edit { putLong(KEY_MANUAL_SEED_COLOR, color) }
    }

    fun observeSettings(onChanged: (ZToolThemeSettings) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in THEME_KEYS) {
                onChanged(loadSettings())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
        key: String,
        defaultValue: T
    ): T {
        val value = getString(key, null) ?: return defaultValue
        return enumValues<T>().firstOrNull { it.name == value } ?: defaultValue
    }

    private fun legacyColorSpec(value: String?): MaterialColorSpec {
        return when (value) {
            "MaterialYou2021" -> MaterialColorSpec.Spec2021
            "Expressive2025" -> MaterialColorSpec.Spec2025
            else -> MaterialColorSpec.Spec2025
        }
    }

    private fun legacyPalette(value: String?): MaterialPalette {
        return enumValues<MaterialPalette>().firstOrNull { it.name == value } ?: MaterialPalette.TonalSpot
    }

    companion object {
        private const val PREF_NAME = "ztool_ui_theme_preferences"
        private const val KEY_FRONTEND_STYLE = "frontend_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MATERIAL_COLOR_SPEC = "material_color_spec"
        private const val KEY_MATERIAL_PALETTE = "material_palette"
        private const val KEY_MATERIAL_PALETTE_MODE = "material_palette_mode"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        private const val KEY_AMOLED_BLACK_ENABLED = "amoled_black_enabled"
        private const val KEY_PREDICTIVE_BACK_GESTURE_ENABLED = "predictive_back_gesture_enabled"
        private const val KEY_MANUAL_COLOR_ENABLED = "manual_color_enabled"
        private const val KEY_MANUAL_SEED_COLOR = "manual_seed_color"
        private val THEME_KEYS = setOf(
            KEY_FRONTEND_STYLE,
            KEY_THEME_MODE,
            KEY_MATERIAL_COLOR_SPEC,
            KEY_MATERIAL_PALETTE,
            KEY_MATERIAL_PALETTE_MODE,
            KEY_DYNAMIC_COLOR_ENABLED,
            KEY_AMOLED_BLACK_ENABLED,
            KEY_PREDICTIVE_BACK_GESTURE_ENABLED,
            KEY_MANUAL_COLOR_ENABLED,
            KEY_MANUAL_SEED_COLOR
        )
    }
}
