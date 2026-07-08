package com.qimian233.ztool.utils

import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination

/**
 * 作用域工具类。
 * <p>
 * 集中定义每个功能入口的作用域包列表和统一的作用域重启逻辑，
 * 供前端 FeaturesRoute 和各 Repository 共同使用。
 * </p>
 */
object ScopeUtils {

    private const val TAG = "ScopeUtils"

    /**
     * 返回某功能入口涉及的所有作用域包名（含主包名）。
     * 所有这些包名都必须在 LSPosed 作用域内，该功能的 Hook 才能完整生效。
     */
    fun getScopePackages(destination: FeatureDestination): List<String> {
        return when (destination) {
            FeatureDestination.SettingsDetail -> listOf(
                "com.android.settings",
                "com.android.permissioncontroller",
                "com.zui.safecenter"
            )
            FeatureDestination.Ota -> listOf(
                "com.lenovo.ota",
                "com.lenovo.tbengine",
                "com.android.settings"
            )
            FeatureDestination.SafeCenter -> listOf(
                "com.zui.safecenter",
                "com.lenovo.safecenter",
                "com.android.documentsui"
            )
            FeatureDestination.Framework -> listOf("android", "system")
            FeatureDestination.GameTool -> listOf("com.zui.game.service")
            FeatureDestination.PackageInstaller -> listOf("com.android.packageinstaller")
            FeatureDestination.SystemUi -> listOf("com.android.systemui", "com.zui.wallpapersetting")
            FeatureDestination.Launcher -> listOf("com.zui.launcher")
            FeatureDestination.MobileDesktop -> listOf("com.motorola.mobiledesktop")
        }
    }

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
     * 重启一组包名的作用域进程。
     * 先尝试 [am force-stop]，失败时回退到 [killall]。
     */
    fun restartScope(
        packages: List<String>,
        shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
        timeoutSeconds: Int = 5
    ): RestartResult {
        if (packages.isEmpty()) return RestartResult.Success

        val failed = mutableListOf<String>()
        for (pkg in packages) {
            if (pkg != "com.android.systemui") {
                val result = shellExecutor.executeRootCommand("am force-stop $pkg", timeoutSeconds)
                if (result.isSuccess) {
                    Log.d(TAG, "Force stop $pkg: success")
                    continue
                }
            } else {
                Log.i(TAG, "Target APP is SystemUI, skip am force-stop")
            }
            // 回退到 killall
            Log.w(TAG, "am force-stop $pkg failed, trying killall")
            val fallback = shellExecutor.executeRootCommand("killall $pkg", timeoutSeconds)
            if (fallback.isSuccess) {
                Log.d(TAG, "killall $pkg: success")
            } else {
                Log.e(TAG, "killall $pkg: failed — ${fallback.error}")
                failed.add(pkg)
            }
        }

        return when {
            failed.isEmpty() -> RestartResult.Success
            failed.size == packages.size -> RestartResult.Failure("All packages failed to restart")
            else -> RestartResult.PartialSuccess(failed)
        }
    }
}
