package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.GetPCFlashFirmware
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemUiMiscSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SystemUiMiscSettingsUiState {
        return SystemUiMiscSettingsUiState(
            guestModeController = prefsUtils.loadBooleanSetting(KEY_GUEST_MODE_CONTROLLER, false),
            disableBiometricErrorVibration = prefsUtils.loadBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, false),
            bypassFaceAuthTimeout = prefsUtils.loadBooleanSetting(KEY_BYPASS_FACE_AUTH_TIMEOUT, false),
            aospScrollCapture = prefsUtils.loadBooleanSetting(KEY_FORCE_LONG_SCREENSHOT_AOSP, false),
            shadeReboundFix = prefsUtils.loadBooleanSetting(KEY_SHADE_REBOUND_FIX, false),
        )
    }

    fun saveGuestModeController(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
    fun saveDisableBiometricErrorVibration(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, enabled)
    fun saveBypassFaceAuthTimeout(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_BYPASS_FACE_AUTH_TIMEOUT, enabled)
    fun saveAospScrollCapture(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_FORCE_LONG_SCREENSHOT_AOSP, enabled)
    fun saveShadeReboundFix(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_SHADE_REBOUND_FIX, enabled)

    fun loadCurrentSn(): String? {
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

    suspend fun fetchFirmware(sn: String): FirmwareFetchResult {
        val firmwareInfo = GetPCFlashFirmware().queryFirmware(sn)
        return if (firmwareInfo != null && firmwareInfo.size >= 6) {
            FirmwareFetchResult.Success(
                FirmwareResult(
                    downloadUrl = firmwareInfo[0].orEmpty(),
                    password = firmwareInfo[1].orEmpty(),
                    platform = firmwareInfo[2].orEmpty(),
                    method = firmwareInfo[3].orEmpty(),
                    firstUploadTime = formatTimestamp(firmwareInfo[4]?.toLongOrNull() ?: 0L),
                    lastUpdateTime = formatTimestamp(firmwareInfo[5]?.toLongOrNull() ?: 0L)
                )
            )
        } else {
            FirmwareFetchResult.Failure(
                context.getString(R.string.system_update_pc_flash_firmware_fetch_failed_message)
            )
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

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return timestamp.toString()
        return try {
            SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp * 1000L))
        } catch (_: Exception) {
            timestamp.toString()
        }
    }

    companion object {
        private val KEY_GUEST_MODE_CONTROLLER = PreferenceKeys.GUEST_MODE_CONTROLLER.name
        private val KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION = PreferenceKeys.DISABLE_BIOMETRIC_ERROR_VIBRATION.name
        private val KEY_BYPASS_FACE_AUTH_TIMEOUT = PreferenceKeys.BYPASS_FACE_AUTH_TIMEOUT.name
        private val KEY_FORCE_LONG_SCREENSHOT_AOSP = PreferenceKeys.FORCE_LONG_SCREENSHOT_AOSP.name
        private val KEY_SHADE_REBOUND_FIX = PreferenceKeys.SHADE_REBOUND_FIX.name
    }
}

data class SystemUiMiscSettingsUiState(
    val guestModeController: Boolean = false,
    val disableBiometricErrorVibration: Boolean = false,
    val bypassFaceAuthTimeout: Boolean = false,
    val aospScrollCapture: Boolean = false,
    val shadeReboundFix: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false,
    val firmwareSnInput: String = "",
    val currentSn: String = "",
    val isFetchingFirmware: Boolean = false,
    val firmwareResult: FirmwareResult? = null,
    val errorDialogMessage: String? = null
)

data class FirmwareResult(
    val downloadUrl: String,
    val password: String,
    val platform: String,
    val method: String,
    val firstUploadTime: String,
    val lastUpdateTime: String
)

sealed interface FirmwareFetchResult {
    data class Success(val firmware: FirmwareResult) : FirmwareFetchResult
    data class Failure(val message: String) : FirmwareFetchResult
}
