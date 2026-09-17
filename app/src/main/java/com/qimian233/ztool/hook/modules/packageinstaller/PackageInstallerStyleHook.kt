package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.Boolean
import kotlin.Array
import kotlin.Exception
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOf

/**
 * ZUI package installer hook module.
 * Function: bypasses ZUI system installation restrictions and modifies the
 * package installer UI style.
 * Target: com.android.packageinstaller (ZUI system package installer).
 */
@SuppressLint("PrivateApi")
class PackageInstallerStyleHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.PACKAGE_INSTALLER_STYLE_HOOK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookZuiPackageInstaller(classLoader)
        doNotShowWarnTextHook(classLoader)
    }

    private fun hookZuiPackageInstaller(classLoader: ClassLoader) {
        try {
            // 1. Hook Utils.isCTSandGTS to bypass installation restrictions
            hookInstallationRestrictions(classLoader)

            // 2. Hook Activity styles to modify the UI
            hookActivityStyles(classLoader)

            logger.info("ZUI Package Installer Hook loaded successfully")
        } catch (t: Throwable) {
            logger.error("Failed to load ZUI Package Installer Hook", t)
        }
    }

    private fun hookInstallationRestrictions(classLoader: ClassLoader) {
        try {
            val utilsClass = classLoader.loadClass(
                "com.android.packageinstaller.extra.Utils"
            )

            // Hook the overloads of isCTSandGTS
            val isCTSandGTS1 = utilsClass.getDeclaredMethod("isCTSandGTS", String::class.java)
            hookWithId(
                isCTSandGTS1,
                "is_ct_sand_gts1"
            ) { Boolean.TRUE }

            val isCTSandGTS2 =
                utilsClass.getDeclaredMethod("isCTSandGTS", String::class.java, Intent::class.java)
            hookWithId(
                isCTSandGTS2,
                "is_ct_sand_gts2"
            ) { Boolean.TRUE }

            logger.info("Successfully hooked installation restriction checks")
        } catch (t: Throwable) {
            logger.error("Failed to hook installation restriction checks", t)
        }
    }

    private fun hookActivityStyles(classLoader: ClassLoader) {
        try {
            // Get the resource ID of Theme_AlertDialogActivity
            val styleClass = classLoader.loadClass(
                $$"com.android.packageinstaller.R$style"
            )
            val themeField = styleClass.getDeclaredField("Theme_AlertDialogActivity")
            themeField.isAccessible = true
            val themeAlertDialogActivity = themeField.getInt(null)

            // Hook Activity.onCreate to modify the theme and window attributes
            val onCreate = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            hookWithId(onCreate, "on_create") { chain ->
                val activity = chain.thisObject as Activity
                // Check whether this is the target package installer Activity
                if (activity.packageName == ScopeKeys.PACKAGE_INSTALLER.packageName) {
                    try {
                        // Apply the dialog theme
                        activity.setTheme(themeAlertDialogActivity)

                        // Enable a transparent background
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            activity.setTranslucent(true)
                        }

                        // Request no title bar
                        activity.requestWindowFeature(1) // 1 = Window.FEATURE_NO_TITLE

                        // Disable window animations
                        activity.window.setWindowAnimations(0)

                        logger.debug("Successfully modified package installer Activity style")
                    } catch (t: Throwable) {
                        logger.error("Error while modifying Activity style", t)
                    }
                }
                chain.proceed()
            }

            logger.info("Successfully hooked Activity style modification")
        } catch (t: Throwable) {
            logger.error("Failed to hook Activity style modification", t)
        }
    }

    private fun doNotShowWarnTextHook(classLoader: ClassLoader) {
        try {
            val activityClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivity"
            )
            val startInstallConfirm = activityClass.getDeclaredMethod("startInstallConfirm")
            hookWithId(
                startInstallConfirm,
                "start_install_confirm"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val resourcesClass =
                        classLoader.loadClass($$"com.android.packageinstaller.R$id")
                    val warnTextViewIdField =
                        resourcesClass.getDeclaredField("install_confirm_question_warning")
                    warnTextViewIdField.isAccessible = true
                    val warnTextViewId = warnTextViewIdField.getInt(null)

                    val mDialogField =
                        chain.thisObject.javaClass.getDeclaredField("mDialog")
                    mDialogField.isAccessible = true
                    val dialog = mDialogField.get(chain.thisObject) as AlertDialog?

                    val tv = dialog!!.findViewById<TextView>(warnTextViewId)
                    tv.visibility = TextView.GONE
                    logger.debug("Successfully set install warn visibility to GONE")
                } catch (e: Exception) {
                    logger.error("Exception happened when trying to set warn text to GONE!", e)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook doNotShowWarnTextHook", t)
        }
    }
}
