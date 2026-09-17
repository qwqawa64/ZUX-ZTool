package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook module to disable the "delete APK after install" prompt.
 * Intercepts the system package installer (com.android.packageinstaller) and
 * modifies the default "delete APK after installation" behavior: the delete
 * option is unchecked by default after the first install, avoiding accidental
 * deletion of the installation file.
 */
class PackageInstallerNoDeleteModule : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.PACKAGE_INSTALLER_DISABLE_DELETE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookPackageInstaller(classLoader)
    }

    /**
     * Core hook logic for the system package installer.
     * Intercepts the initView method of InstallSuccessExtra to modify the default
     * delete-APK behavior. Newer PackageInstaller versions moved the CheckBox into
     * a local variable of initView(), and its OnCheckedChangeListener directly
     * overwrites mDeleteApk, therefore:
     * 1. Force mDeleteApk = false
     * 2. Locate the CheckBox via findViewById and replace its listener so a manual
     *    user check cannot override the boolean
     * 3. Safety net: hook clearCachedApkIfNeededAndFinish to ensure mDeleteApk = false again
     */
    private fun hookPackageInstaller(classLoader: ClassLoader) {
        try {
            logger.info("Starting hook for package installer")

            @SuppressLint("PrivateApi") val installSuccessExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.InstallSuccessExtra"
            )

            // --- Hook 1: initView() — initial setup + UI fix ---
            val initView = installSuccessExtraClass.getDeclaredMethod("initView")
            val mDeleteApkField = installSuccessExtraClass.getDeclaredField("mDeleteApk")
            mDeleteApkField.isAccessible = true

            hookWithId(initView, "init_view") { chain ->
                val result = chain.proceed()
                if (!isEnabled()) {
                    return@hookWithId result
                }

                try {
                    val instance = chain.thisObject
                    logger.debug("Inside initView method for package installer")

                    // Force mDeleteApk = false (regardless of configuration change)
                    mDeleteApkField.setBoolean(instance, false)

                    // Locate the CheckBox via findViewById (a local variable in newer
                    // versions, not reachable via field reflection)
                    try {
                        val activity = instance as Activity
                        @SuppressLint("DiscouragedApi") val checkBoxId =
                            activity.resources.getIdentifier(
                                "del_check_box", "id", ScopeKeys.PACKAGE_INSTALLER.packageName
                            )
                        if (checkBoxId != 0) {
                            val view = activity.findViewById<View?>(checkBoxId)
                            if (view is CheckBox) {
                                // Update the UI to the unchecked state
                                view.isChecked = false
                                // Replace the listener: prevent a manual user check from overwriting mDeleteApk
                                view.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                                    try {
                                        mDeleteApkField.setBoolean(instance, false)
                                    } catch (_: Throwable) {
                                    }
                                    // Always display as unchecked
                                    if (isChecked) {
                                        buttonView!!.isChecked = false
                                    }
                                }
                                logger.debug("Successfully updated UI checkbox and replaced listener")
                            }
                        } else {
                            logger.warn("CheckBox resource ID 'del_check_box' not found, may be new version")
                        }
                    } catch (uiError: Throwable) {
                        logger.error("Failed to update checkbox UI", uiError)
                    }
                } catch (t: Throwable) {
                    logger.error("Error in afterHookedMethod for initView", t)
                }
                result
            }

            logger.info("Successfully hooked InstallSuccessExtra.initView()")

            // --- Hook 2: clearCachedApkIfNeededAndFinish() — safety net ---
            // Called after the delete thread finishes, or from onStop.
            // Ensures mDeleteApk = false again as multi-layer protection.
            try {
                val clearMethod = installSuccessExtraClass.getDeclaredMethod(
                    "clearCachedApkIfNeededAndFinish"
                )
                hookWithId(clearMethod, "clear") { chain ->
                    if (isEnabled()) {
                        try {
                            mDeleteApkField.setBoolean(chain.thisObject, false)
                        } catch (_: Throwable) {
                        }
                    }
                    chain.proceed()
                }
                logger.info("Successfully hooked InstallSuccessExtra.clearCachedApkIfNeededAndFinish()")
            } catch (t: Throwable) {
                logger.error("Failed to hook clearCachedApkIfNeededAndFinish", t)
            }
        } catch (t: Throwable) {
            logger.error("Failed to initialize package installer hook", t)
        }
    }
}
