package com.qimian233.ztool.data.safecenter

import android.content.Context
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.SafeCenterSettingsUiState

class SafeCenterSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SafeCenterSettingsUiState {
        return SafeCenterSettingsUiState(
            defaultEnableAutorun = prefsUtils.loadBooleanSetting(KEY_DEFAULT_ENABLE_AUTORUN, false),
            disableAllVirusScan = prefsUtils.loadBooleanSetting(KEY_DISABLE_ALL_VIRUS_SCANS, false),
            documentsUiBypass = prefsUtils.loadBooleanSetting(KEY_DOCUMENTS_UI_BYPASS, false)
        )
    }

    fun saveDefaultEnableAutorun(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DEFAULT_ENABLE_AUTORUN, enabled)
    }

    fun saveDocumentsUiBypass(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DOCUMENTS_UI_BYPASS, enabled)
    }

    fun saveDisableAllVirusScan(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_ALL_VIRUS_SCANS, enabled)
    }

    fun restartPackages(packageName: String): SafeCenterRestartResult {
        if (packageName.isEmpty()) {
            return SafeCenterRestartResult.EmptyPackageName
        }

        return try {
            val appResult = shellExecutor.executeRootCommand("am force-stop $packageName", 5)
            val appResult2nd = shellExecutor.executeRootCommand("am force-stop com.lenovo.safecenter", 5)
            val documentsResult = shellExecutor.executeRootCommand("am force-stop com.android.documentsui", 5)
            val success = (appResult.isSuccess || appResult2nd.isSuccess) && documentsResult.isSuccess

            if (success) {
                Log.d(TAG, "Force stop result: success")
                SafeCenterRestartResult.Success
            } else {
                Log.w(TAG, "am force-stop failed, trying killall")
                val fallbackResult = shellExecutor.executeRootCommand("killall $packageName", 5)
                val fallbackResult2nd = shellExecutor.executeRootCommand("killall com.lenovo.safecenter", 5)
                Log.d(TAG, "Force stop result: failed")
                if (fallbackResult.isSuccess || fallbackResult2nd.isSuccess) {
                    SafeCenterRestartResult.Success
                } else {
                    if (!fallbackResult.isSuccess) {
                        SafeCenterRestartResult.Failure(fallbackResult.error)
                    } else {
                        SafeCenterRestartResult.Failure(fallbackResult2nd.error)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop app: ${e.message}")
            SafeCenterRestartResult.Failure(e.message.orEmpty())
        }
    }

    companion object {
        private const val TAG = "SafeCenterSettings"
        private const val KEY_DEFAULT_ENABLE_AUTORUN = "default_enable_autorun"
        private const val KEY_DOCUMENTS_UI_BYPASS = "documents_ui_bypass"
        private const val KEY_DISABLE_ALL_VIRUS_SCANS = "disable_all_virus_scans"
    }
}

sealed interface SafeCenterRestartResult {
    data object Success : SafeCenterRestartResult
    data object EmptyPackageName : SafeCenterRestartResult
    data class Failure(val error: String) : SafeCenterRestartResult
}
