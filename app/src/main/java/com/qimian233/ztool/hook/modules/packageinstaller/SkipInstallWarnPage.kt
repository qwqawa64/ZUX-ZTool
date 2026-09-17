package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook module to skip the package installer warning page.
 * Automatically clicks the install button, skipping the user confirmation step.
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
            // Hook onResume to run after the UI is shown
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val onResume = activityExtraClass.getDeclaredMethod("onResume")
            hookWithId(onResume, "on_resume") { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject

                // Delay execution to ensure the UI is fully loaded
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Call handleDirectInstallInFindSameAppCase directly
                        activity.javaClass.getDeclaredMethod("handleDirectInstallInFindSameAppCase")
                            .invoke(activity)
                        logger.debug("Successfully called handleDirectInstallInFindSameAppCase")
                    } catch (_: Exception) {
                        // If the method above does not exist, try onDirectInstall
                        try {
                            activity.javaClass.getDeclaredMethod("onDirectInstall")
                                .invoke(activity)
                            logger.debug("Successfully called onDirectInstall")
                        } catch (e2: Exception) {
                            logger.error("Both installation methods failed", e2)
                        }
                    }
                }, 50) // Execute immediately
                result
            }

            logger.info("Successfully hooked PackageInstallerActivityExtra.onResume")
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstallerActivityExtra", t)
        }
    }
}
