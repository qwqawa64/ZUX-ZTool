package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils

class AnimationWallpaperSettingsRepository(private val context: Context) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): AnimationWallpaperSettingsUiState {
        return AnimationWallpaperSettingsUiState(
            noChargeAnimation = prefsUtils.loadBooleanSetting(KEY_NO_CHARGE_ANIM, false),
            chargeAnimationFix = prefsUtils.loadBooleanSetting(KEY_CHARGE_ANIM_FIX, false),
            customChargeAnimation = prefsUtils.loadBooleanSetting(KEY_CUSTOM_CHARGE_ANIM, false),
            desktopLiveWallpaper = prefsUtils.loadBooleanSetting(KEY_DESKTOP_LIVE_WP, false),
        )
    }

    fun saveNoChargeAnimation(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_NO_CHARGE_ANIM, enabled)
    fun saveChargeAnimationFix(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_CHARGE_ANIM_FIX, enabled)
    fun saveCustomChargeAnimation(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_CUSTOM_CHARGE_ANIM, enabled)
    fun saveDesktopLiveWallpaper(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_DESKTOP_LIVE_WP, enabled)

    fun forceStopScope(): ShellActionResult {
        val packages = listOf("com.android.systemui", "com.zui.wallpapersetting")
        return when (val result = ScopeUtils.restartScope(packages)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(success = false, error = "Partial failure: ${result.failed.joinToString()}", exitCode = -1)
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(success = false, error = result.message, exitCode = -1)
        }
    }

    companion object {
        private val KEY_NO_CHARGE_ANIM = PreferenceKeys.NO_CHARGE_ANIMATION.name
        private val KEY_CHARGE_ANIM_FIX = PreferenceKeys.CHARGE_ANIMATION_FIX.name
        private val KEY_CUSTOM_CHARGE_ANIM = PreferenceKeys.CUSTOM_CHARGE_ANIMATION.name
        private val KEY_DESKTOP_LIVE_WP = PreferenceKeys.DESKTOP_LIVE_WALLPAPER.name
    }
}

data class AnimationWallpaperSettingsUiState(
    val noChargeAnimation: Boolean = false,
    val chargeAnimationFix: Boolean = false,
    val customChargeAnimation: Boolean = false,
    val desktopLiveWallpaper: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
