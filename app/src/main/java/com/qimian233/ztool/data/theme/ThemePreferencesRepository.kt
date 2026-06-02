package com.qimian233.ztool.data.theme

import android.content.Context
import android.content.SharedPreferences
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialPaletteMode
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import androidx.core.content.edit

class ThemePreferencesRepository(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): ZToolThemeSettings {
        return ZToolThemeSettings(
            frontendStyle = prefs.getEnum(KEY_FRONTEND_STYLE, FrontendStyle.Material3Expressive),
            themeMode = prefs.getEnum(KEY_THEME_MODE, ThemeMode.FollowSystem),
            materialPaletteMode = prefs.getEnum(KEY_MATERIAL_PALETTE_MODE, MaterialPaletteMode.Expressive2025),
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, true),
            amoledBlackEnabled = prefs.getBoolean(KEY_AMOLED_BLACK_ENABLED, false),
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
                .putString(KEY_MATERIAL_PALETTE_MODE, settings.materialPaletteMode.name)
                .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, settings.dynamicColorEnabled)
                .putBoolean(KEY_AMOLED_BLACK_ENABLED, settings.amoledBlackEnabled)
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

    fun saveMaterialPaletteMode(mode: MaterialPaletteMode) {
        prefs.edit { putString(KEY_MATERIAL_PALETTE_MODE, mode.name) }
    }

    fun saveDynamicColorEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR_ENABLED, enabled) }
    }

    fun saveAmoledBlackEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_BLACK_ENABLED, enabled) }
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

    companion object {
        private const val PREF_NAME = "ztool_ui_theme_preferences"
        private const val KEY_FRONTEND_STYLE = "frontend_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MATERIAL_PALETTE_MODE = "material_palette_mode"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        private const val KEY_AMOLED_BLACK_ENABLED = "amoled_black_enabled"
        private const val KEY_MANUAL_COLOR_ENABLED = "manual_color_enabled"
        private const val KEY_MANUAL_SEED_COLOR = "manual_seed_color"
        private val THEME_KEYS = setOf(
            KEY_FRONTEND_STYLE,
            KEY_THEME_MODE,
            KEY_MATERIAL_PALETTE_MODE,
            KEY_DYNAMIC_COLOR_ENABLED,
            KEY_AMOLED_BLACK_ENABLED,
            KEY_MANUAL_COLOR_ENABLED,
            KEY_MANUAL_SEED_COLOR
        )
    }
}
