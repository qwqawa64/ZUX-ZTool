package com.qimian233.ztool.utils

import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.data.HowToRestart
import com.qimian233.ztool.data.Scope
import com.qimian233.ztool.data.ScopeKeys

/**
 * 作用域工具类。
 * <p>
 * 集中定义每个功能入口的作用域列表和统一的作用域重启逻辑，
 * 供前端 FeaturesRoute 和各 Repository 共同使用。
 * 所有作用域包名与推荐重启方式均来自 [ScopeKeys]，禁止在此处硬编码包名。
 * </p>
 */
object ScopeUtils {

    private const val TAG = "ScopeUtils"

    /**
     * 返回某功能入口涉及的所有作用域（包名 + 推荐重启方式）。
     * 所有这些包名都必须在 LSPosed 作用域内，该功能的 Hook 才能完整生效。
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
        }
    }

    /**
     * 返回某功能入口涉及的所有作用域包名（含主包名）。
     */
    fun getScopePackages(destination: FeatureDestination): List<String> =
        getScopes(destination).map { it.packageName }

    /**
     * 统一的作用域重启结果。
     */
    sealed interface RestartResult {
        /** 全部成功 */
        data object Success : RestartResult
        /** 部分成功，[failed] 为失败的包名列表 */
        data class PartialSuccess(val failed: List<String>) : RestartResult
        /** 全部失败 */
        data class Failure(val message: String) : RestartResult
    }

    /**
     * 重启一组作用域进程，按每个 Scope 注册的 [HowToRestart] 分发策略：
     * - [HowToRestart.AmStop]：先尝试 [am force-stop]，失败时回退到 [killall]；
     * - [HowToRestart.KillAll]：直接 [killall]（例如 SystemUI 无法被 force-stop）；
     * - [HowToRestart.Reboot]：系统框架进程无法按包重启，跳过并提示需要重启系统。
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
                    // 回退到 killall
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
                    // 系统框架进程无法通过 force-stop/killall 重启，需要重启系统
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
