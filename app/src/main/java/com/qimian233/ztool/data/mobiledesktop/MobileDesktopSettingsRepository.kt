package com.qimian233.ztool.data.mobiledesktop

import android.content.Context
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.MobileDesktopSettingsUiState

class MobileDesktopSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    private val prefs = ModulePreferencesUtils(context)
    fun restartScope(packageName: String): MobileDesktopRestartResult {
        if (packageName.isBlank()) {
            return MobileDesktopRestartResult.EmptyPackageName
        }

        return try {
            val result = shellExecutor.executeRootCommand("am force-stop $packageName", 5)
            if (result.isSuccess) {
                Log.d(TAG, "Force stop result: success")
                MobileDesktopRestartResult.Success
            } else {
                Log.w(TAG, "am force-stop failed, trying killall")
                val fallbackResult = shellExecutor.executeRootCommand("killall $packageName", 5)
                if (fallbackResult.isSuccess) {
                    MobileDesktopRestartResult.Success
                } else {
                    MobileDesktopRestartResult.Failure(
                        fallbackResult.error.ifBlank { result.error }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart scope: ${e.message}")
            MobileDesktopRestartResult.Failure(e.message.orEmpty())
        }
    }

    fun loadState(): MobileDesktopSettingsUiState {
        return MobileDesktopSettingsUiState(
            skipExposeWarn = prefs.loadBooleanSetting(KEY_SKIP_EXPOSE_WARN, false),
            autoAcceptFileTransfer = prefs.loadBooleanSetting(KEY_AUTO_ACCEPT_FILE_TRANSFER, false)
        )
    }

    fun saveSkipExposeWarn(enabled: Boolean) {
        prefs.saveBooleanSetting(KEY_SKIP_EXPOSE_WARN, enabled)
    }

    fun saveAutoAcceptFileTransfer(enabled: Boolean) {
        prefs.saveBooleanSetting(KEY_AUTO_ACCEPT_FILE_TRANSFER, enabled)
    }

    companion object {
        private const val TAG = "MobileDesktopSettings"
        private const val KEY_SKIP_EXPOSE_WARN = "bypass_share_warning"
        private const val KEY_AUTO_ACCEPT_FILE_TRANSFER = "auto_accept_file_transfer"
    }
}

sealed interface MobileDesktopRestartResult {
    data object Success : MobileDesktopRestartResult
    data object EmptyPackageName : MobileDesktopRestartResult
    data class Failure(val error: String) : MobileDesktopRestartResult
}
