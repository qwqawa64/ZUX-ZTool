package com.qimian233.ztool.data.systemui

import android.content.Context
import android.net.Uri
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SystemUiSettingsUiState

class SystemUiSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SystemUiSettingsUiState {
        return SystemUiSettingsUiState(
            nativeAod = prefsUtils.loadBooleanSetting(KEY_FORCE_NATIVE_AOD, false),
            lenovoAod = prefsUtils.loadBooleanSetting(KEY_FORCE_LENOVO_AOD, false),
            noChargeAnimation = prefsUtils.loadBooleanSetting(KEY_NO_CHARGE_ANIMATION, false),
            chargeAnimationFix = prefsUtils.loadBooleanSetting(KEY_CHARGE_ANIMATION_FIX, false),
            customChargeAnimation = prefsUtils.loadBooleanSetting(KEY_CUSTOM_CHARGE_ANIMATION, false),
            guestModeController = prefsUtils.loadBooleanSetting(KEY_GUEST_MODE_CONTROLLER, false),
            disableBiometricErrorVibration = prefsUtils.loadBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, false)
        )
    }

    fun saveNativeAod(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FORCE_NATIVE_AOD, enabled)
    }

    fun saveLenovoAod(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FORCE_LENOVO_AOD, enabled)
    }

    fun isLenovoAodEnabled(): Boolean {
        return prefsUtils.loadBooleanSetting(KEY_FORCE_LENOVO_AOD, false)
    }

    fun saveNoChargeAnimation(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NO_CHARGE_ANIMATION, enabled)
    }

    fun saveChargeAnimationFix(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CHARGE_ANIMATION_FIX, enabled)
    }

    fun saveCustomChargeAnimation(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_CHARGE_ANIMATION, enabled)
    }

    fun saveGuestModeController(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
    }

    fun saveDisableBiometricErrorVibration(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, enabled)
    }

    /**
     * 将用户选择的视频文件通过 root shell 直接写入 ZTool 目录，
     * 全程不使用应用私有目录作为中转。
     */
    fun saveChargeAnimationVideo(context: Context, uri: Uri, fileName: String): Boolean {
        try {
            val targetPath = "$CUSTOM_VIDEO_DIR/$fileName"
            // 确保目标目录存在
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

    fun openLenovoAodSettings(): ShellActionResult {
        val result = shellExecutor.executeRootCommand(
            "am start -n com.android.systemui/com.android.systemui.aod.setting.AoDSettingActivity",
            5
        )
        return result.toShellActionResult()
    }

    fun setNativeAodEnabled(enabled: Boolean): ShellActionResult {
        val command = "settings put secure doze_always_on " + if (enabled) "1" else "0"
        val result = shellExecutor.executeRootCommand(command, 5)
        return result.toShellActionResult()
    }

    fun isNativeAodEnabled(): Boolean {
        return try {
            val result = shellExecutor.executeRootCommand("settings get secure doze_always_on", 5)
            result.isSuccess && result.output != null && result.output.trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    fun forceStopScope(): ShellActionResult {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(packages, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(
                success = false,
                error = "Partial failure: ${result.failed.joinToString()}",
                exitCode = -1
            )
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(
                success = false,
                error = result.message,
                exitCode = -1
            )
        }
    }

    private fun EnhancedShellExecutor.ShellResult.toShellActionResult(): ShellActionResult {
        return ShellActionResult(
            success = isSuccess,
            error = error.orEmpty(),
            exitCode = exitCode
        )
    }

    companion object {
        private val KEY_FORCE_NATIVE_AOD = PreferenceKeys.FORCE_NATIVE_AOD.name
        private val KEY_FORCE_LENOVO_AOD = PreferenceKeys.FORCE_LENOVO_AOD.name
        private val KEY_NO_CHARGE_ANIMATION = PreferenceKeys.NO_CHARGE_ANIMATION.name
        private val KEY_CHARGE_ANIMATION_FIX = PreferenceKeys.CHARGE_ANIMATION_FIX.name
        private val KEY_CUSTOM_CHARGE_ANIMATION = PreferenceKeys.CUSTOM_CHARGE_ANIMATION.name
        private val KEY_GUEST_MODE_CONTROLLER = PreferenceKeys.GUEST_MODE_CONTROLLER.name
        private val KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION = PreferenceKeys.DISABLE_BIOMETRIC_ERROR_VIBRATION.name
        private const val CUSTOM_VIDEO_DIR = "/sdcard/Download/ZTool"
    }
}

data class ShellActionResult(
    val success: Boolean,
    val error: String,
    val exitCode: Int
)
