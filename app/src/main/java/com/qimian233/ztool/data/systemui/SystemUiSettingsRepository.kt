package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
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
            guestModeController = prefsUtils.loadBooleanSetting(KEY_GUEST_MODE_CONTROLLER, false)
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

    fun saveGuestModeController(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
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
        private const val KEY_FORCE_NATIVE_AOD = "ForceNativeAOD"
        private const val KEY_FORCE_LENOVO_AOD = "ForceLenovoAOD"
        private const val KEY_NO_CHARGE_ANIMATION = "No_ChargeAnimation"
        private const val KEY_CHARGE_ANIMATION_FIX = "charge_animation_fix"
        private const val KEY_GUEST_MODE_CONTROLLER = "guest_mode_controller"
    }
}

data class ShellActionResult(
    val success: Boolean,
    val error: String,
    val exitCode: Int
)
