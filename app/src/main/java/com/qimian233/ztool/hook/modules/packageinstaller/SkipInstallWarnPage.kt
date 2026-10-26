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
            val onResume = findMethod(activityExtraClass, "onResume")
            hookWithId(onResume, "on_resume") { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject

                // Delay execution to ensure the UI is fully loaded
                Handler(Looper.getMainLooper()).postDelayed({
                    val invoked =
                        invokeInstallStep(activity, activityExtraClass, METHOD_HANDLE_DIRECT_INSTALL) ||
                            invokeInstallStep(activity, activityExtraClass, METHOD_ON_DIRECT_INSTALL)
                    if (!invoked && !installStepWarningLogged) {
                        installStepWarningLogged = true
                        logger.warn("Neither $METHOD_HANDLE_DIRECT_INSTALL nor $METHOD_ON_DIRECT_INSTALL is invokable")
                    }
                }, 50) // Execute immediately
                result
            }

            logger.info("Successfully hooked PackageInstallerActivityExtra.onResume")
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstallerActivityExtra", t)
        }
    }

    /**
     * Invokes a no-arg install step, resolving it on the hooked class before the runtime class.
     *
     * @return true when the method was found and invoked
     */
    private fun invokeInstallStep(activity: Any?, owner: Class<*>, methodName: String): Boolean {
        val target = activity ?: return false
        val candidates = if (target.javaClass == owner) {
            arrayOf(owner)
        } else {
            arrayOf(owner, target.javaClass)
        }
        for (candidate in candidates) {
            try {
                findMethod(candidate, methodName).invoke(target)
                logger.debug("Successfully called $methodName")
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private var installStepWarningLogged = false

    companion object {
        // Both methods are private in the Android 17 PackageInstallerActivityExtra
        private const val METHOD_HANDLE_DIRECT_INSTALL = "handleDirectInstallInFindSameAppCase"
        private const val METHOD_ON_DIRECT_INSTALL = "onDirectInstall"
    }
}
