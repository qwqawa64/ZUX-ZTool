package com.qimian233.ztool.utils

import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.data.keys.HowToRestart
import com.qimian233.ztool.data.keys.Scope
import com.qimian233.ztool.data.keys.ScopeKeys

/**
 * Scope utilities.
 * <p>
 * Centrally defines each feature entry's scope list and the unified scope restart
 * logic, shared by the frontend FeaturesRoute and the repositories.
 * All scope package names and preferred restart methods come from [ScopeKeys];
 * hardcoding package names here is forbidden.
 * </p>
 */
object ScopeUtils {

    private const val TAG = "ScopeUtils"

    /**
     * Returns all scopes (package name + preferred restart method) involved in a
     * feature entry. All these packages must be inside the LSPosed scope for the
     * feature's hooks to take full effect.
     */
    fun getScopes(destination: FeatureDestination): List<Scope> {
        return when (destination) {
            FeatureDestination.SettingsDetail -> listOf(
                ScopeKeys.SETTINGS,
                ScopeKeys.PERMISSION_CONTROLLER,
                ScopeKeys.ZUI_SAFE_CENTER
            )
            FeatureDestination.Ota -> listOf(
                ScopeKeys.OTA,
                ScopeKeys.TB_ENGINE,
                ScopeKeys.SETTINGS
            )
            FeatureDestination.SafeCenter -> listOf(
                ScopeKeys.ZUI_SAFE_CENTER,
                ScopeKeys.LENOVO_SAFE_CENTER,
                ScopeKeys.DOCUMENTS_UI
            )
            FeatureDestination.Framework -> listOf(
                ScopeKeys.ANDROID_SYSTEM,
                ScopeKeys.SYSTEM_SERVER
            )
            FeatureDestination.GameTool -> listOf(
                ScopeKeys.GAME_SERVICE,
            )
            FeatureDestination.PackageInstaller -> listOf(ScopeKeys.PACKAGE_INSTALLER)
            FeatureDestination.SystemUi -> listOf(
                ScopeKeys.SYSTEM_UI,
                ScopeKeys.WALLPAPER_SETTINGS
            )
            FeatureDestination.Launcher -> listOf(ScopeKeys.LAUNCHER)
            FeatureDestination.MobileDesktop -> listOf(
                ScopeKeys.MOBILE_DESKTOP,
            )
            FeatureDestination.TbEngine -> listOf(ScopeKeys.TB_ENGINE)
            FeatureDestination.ZuiPerformance -> listOf(ScopeKeys.ZUI_PERFORMANCE)
        }
    }

    /**
     * Returns all scope package names (including the primary package) for a feature entry.
     */
    fun getScopePackages(destination: FeatureDestination): List<String> =
        getScopes(destination).map { it.packageName }

    /**
     * Unified scope restart result.
     */
    sealed interface RestartResult {
        /** All succeeded */
        data object Success : RestartResult
        /** Partial success; [failed] holds the failed package names */
        data class PartialSuccess(val failed: List<String>) : RestartResult
        /** All failed */
        data class Failure(val message: String) : RestartResult
    }

    /**
     * Restarts a group of scope processes, dispatching by each Scope's registered
     * [HowToRestart] strategy:
     * - [HowToRestart.AmStop]: try [am force-stop] first, fall back to killall on failure;
     * - [HowToRestart.KillAll]: killall directly (e.g. SystemUI cannot be force-stopped);
     * - [HowToRestart.Reboot]: system framework processes cannot be restarted per package;
     *   skipped with a note that a system reboot is required.
     */
    fun restartScope(
        scopes: List<Scope>,
        shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
        timeoutSeconds: Int = 5
    ): RestartResult {
        if (scopes.isEmpty()) return RestartResult.Success

        val failed = mutableListOf<String>()
        for (scope in scopes) {
            when (scope.howToRestart) {
                HowToRestart.AmStop -> {
                    val result = shellExecutor.executeRootCommand(
                        "am force-stop ${scope.packageName}",
                        timeoutSeconds
                    )
                    if (result.isSuccess) {
                        Log.d(TAG, "Force stop ${scope.packageName}: success")
                        continue
                    }
                    // Fall back to killall
                    Log.w(TAG, "am force-stop ${scope.packageName} failed, trying killall")
                    if (!killPackage(scope.packageName, shellExecutor, timeoutSeconds)) {
                        failed.add(scope.packageName)
                    }
                }
                HowToRestart.KillAll -> {
                    if (!killPackage(scope.packageName, shellExecutor, timeoutSeconds)) {
                        failed.add(scope.packageName)
                    }
                }
                HowToRestart.Reboot -> {
                    // System framework processes cannot be restarted via force-stop/killall; a system reboot is required
                    Log.i(TAG, "${scope.packageName} requires system reboot, skipped")
                }
            }
        }

        return when {
            failed.isEmpty() -> RestartResult.Success
            failed.size == scopes.size -> RestartResult.Failure("All packages failed to restart")
            else -> RestartResult.PartialSuccess(failed)
        }
    }

    private fun killPackage(
        pkg: String,
        shellExecutor: EnhancedShellExecutor,
        timeoutSeconds: Int
    ): Boolean {
        val result = shellExecutor.executeRootCommand("killall $pkg", timeoutSeconds)
        if (result.isSuccess) {
            Log.d(TAG, "killall $pkg: success")
            return true
        }
        Log.e(TAG, "killall $pkg: failed — ${result.error}")
        return false
    }
}
