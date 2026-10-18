package com.qimian233.ztool.utils

import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.features.FeatureCatalog
import com.qimian233.ztool.data.features.FeatureDestination
import com.qimian233.ztool.data.keys.HowToRestart
import com.qimian233.ztool.data.keys.Scope

/**
 * Scope utilities.
 *
 * Scope lists come from [FeatureCatalog]; package names and preferred restart
 * methods still come from `ScopeKeys`. [restartScope] implements the unified
 * restart logic shared by the frontend and the repositories.
 */
object ScopeUtils {

    private const val TAG = "ScopeUtils"

    /**
     * Returns all scopes (package name + preferred restart method) involved in a
     * feature entry, as declared by [FeatureCatalog].
     */
    fun getScopes(destination: FeatureDestination): List<Scope> =
        FeatureCatalog.card(destination).scopes()

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
