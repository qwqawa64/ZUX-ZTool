package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 跳过包安装器警告页面Hook模块
 * 自动点击安装按钮，跳过用户确认步骤
 */
@SuppressLint("PrivateApi")
class SkipInstallWarnPage : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.SKIP_WARN_PAGE.name

    override fun getTargetPackages(): Array<String> = arrayOf(
            ScopeKeys.PACKAGE_INSTALLER.packageName
        )

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookPackageInstallerActivity(classLoader)
    }

    private fun hookPackageInstallerActivity(classLoader: ClassLoader) {
        try {
            // Hook onResume 方法，在界面显示后执行
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val onResume = activityExtraClass.getDeclaredMethod("onResume")
            hookWithId(onResume, "on_resume") { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject

                // 延迟执行，确保界面完全加载
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // 直接调用 handleDirectInstallInFindSameAppCase 方法
                        activity.javaClass.getDeclaredMethod("handleDirectInstallInFindSameAppCase")
                            .invoke(activity)
                        logger.debug("Successfully called handleDirectInstallInFindSameAppCase")
                    } catch (_: Exception) {
                        // 如果上面的方法不存在，尝试调用 onDirectInstall 方法
                        try {
                            activity.javaClass.getDeclaredMethod("onDirectInstall")
                                .invoke(activity)
                            logger.debug("Successfully called onDirectInstall")
                        } catch (e2: Exception) {
                            logger.error("Both installation methods failed", e2)
                        }
                    }
                }, 50) // 立刻执行
                result
            }

            logger.info("Successfully hooked PackageInstallerActivityExtra.onResume")
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstallerActivityExtra", t)
        }
    }
}
