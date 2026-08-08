package com.qimian233.ztool.data.systemui

import android.content.Context
import android.net.Uri
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
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

    fun saveVideo(uri: Uri, fileName: String): Boolean {
        try {
            val targetPath = "$CUSTOM_VIDEO_DIR/$fileName"
            val shellExecutor = EnhancedShellExecutor.getInstance()
            shellExecutor.executeRootCommand("mkdir -p $CUSTOM_VIDEO_DIR", 5)

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false

            val process = Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "cat > $targetPath && chmod 644 $targetPath"))
            process.outputStream.use { it.write(bytes) }
            return process.waitFor() == 0
        } catch (e: Exception) {
            return false
        }
    }

    fun forceStopScope(): ShellActionResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(scopes)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(success = false, error = "Partial failure: ${result.failed.joinToString()}", exitCode = -1)
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(success = false, error = result.message, exitCode = -1)
        }
    }

    companion object {
        private const val CUSTOM_VIDEO_DIR = "/sdcard/Download/ZTool"

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
