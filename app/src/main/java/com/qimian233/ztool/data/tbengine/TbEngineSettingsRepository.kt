package com.qimian233.ztool.data.tbengine

import android.content.Context
import android.os.Build
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.TbEngineRestartResult
import com.qimian233.ztool.viewmodel.TbEngineSettingsUiState

class TbEngineSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun ensureCustomOtaParametersEnabled() {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_OTA_PARAMETERS, true)
    }

    fun loadState(): TbEngineSettingsUiState {
        return TbEngineSettingsUiState(
            disableAutoDownload = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, false),
            disableAutoInstall = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_INSTALL, false),
            disableAppUpdate = prefsUtils.loadBooleanSetting(KEY_DISABLE_APP_UPDATE, false),
            disablePush = prefsUtils.loadBooleanSetting(KEY_DISABLE_PUSH, false),
            signLocalOta = prefsUtils.loadBooleanSetting(KEY_SIGN_LOCAL_OTA, false),
            customVersion = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, ""),
            customDeviceId = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, ""),
            currentVersion = context.getString(R.string.system_update_loading_ellipsis),
            currentSn = context.getString(R.string.system_update_loading_ellipsis)
        )
    }

    fun saveDisableAutoDownload(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, enabled)
    }

    fun saveDisableAutoInstall(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_INSTALL, enabled)
    }

    fun saveDisableAppUpdate(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_APP_UPDATE, enabled)
    }

    fun saveDisablePush(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_PUSH, enabled)
    }

    fun saveSignLocalOta(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SIGN_LOCAL_OTA, enabled)
    }

    fun saveCustomVersion(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, value)
    }

    fun saveCustomDeviceId(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, value)
    }

    fun loadCurrentDeviceInfo(): TbEngineCurrentDeviceInfo {
        val version = Build.DISPLAY.ifEmpty {
            context.getString(R.string.common_unknown)
        }
        val sn = getMachineSnByProps()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.common_unknown)
        return TbEngineCurrentDeviceInfo(version = version, sn = sn)
    }

    fun getMachineSn(): String? = getMachineSnByProps()

    fun restartScope(): TbEngineRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.TbEngine)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> TbEngineRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> TbEngineRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> TbEngineRestartResult.Failure(result.message)
        }
    }

    private fun getMachineSnByProps(): String? {
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

    companion object {
        private val KEY_CUSTOM_OTA_PARAMETERS = PreferenceKeys.CUSTOM_OTA_PARAMETERS.name
        private val KEY_DISABLE_AUTO_DOWNLOAD = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_DOWNLOAD.name
        private val KEY_DISABLE_AUTO_INSTALL = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_INSTALL.name
        private val KEY_DISABLE_APP_UPDATE = PreferenceKeys.DISABLE_TB_ENGINE_APP_UPDATE.name
        private val KEY_DISABLE_PUSH = PreferenceKeys.DISABLE_TB_ENGINE_PUSH.name
        private val KEY_SIGN_LOCAL_OTA = PreferenceKeys.SIGN_TB_ENGINE_LOCAL_OTA.name
        private val KEY_CUSTOM_OTA_TARGET_VERSION = PreferenceKeys.CUSTOM_OTA_TARGET_VERSION_NAME.name
        private val KEY_CUSTOM_OTA_TARGET_DEVICE_ID = PreferenceKeys.CUSTOM_OTA_TARGET_DEVICE_ID.name
    }
}

data class TbEngineCurrentDeviceInfo(
    val version: String,
    val sn: String
)
