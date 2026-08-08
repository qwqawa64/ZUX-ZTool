package com.qimian233.ztool.hook.modules.setting

import android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Window
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.Boolean
import java.lang.invoke.MethodHandles
import kotlin.Array
import kotlin.Exception
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOf

@SuppressLint("PrivateApi")
class PermissionControllerHook : AppHookModule() {
    override fun getModuleName(): String = "PermissionControllerHook"

    override fun getTargetPackages(): Array<String> = arrayOf(
            ScopeKeys.PERMISSION_CONTROLLER.packageName,
            ScopeKeys.SETTINGS.packageName,
            ScopeKeys.ZUI_SAFE_CENTER.packageName
        )

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (!isEnabled()) return
        logger.info("Loading module PermissionControllerHook.")
        try {
            when (packageName) {
                ScopeKeys.PERMISSION_CONTROLLER.packageName -> {
                    logger.debug("com.android.permissioncontroller detected. Hooking...")
                    handleLoadPermissionController(classLoader)
                }
                ScopeKeys.SETTINGS.packageName -> {
                    logger.debug("com.android.settings detected. Hooking...")
                    handleLoadSettings(classLoader)
                }
                ScopeKeys.ZUI_SAFE_CENTER.packageName -> {
                    logger.debug("com.zui.safecenter detected. Hooking...")
                    handleLoadSafeCenter(classLoader)
                }
            }
            logger.info("Hook is successful.")
        } catch (e: Exception) {
            logger.error("Error hooking", e)
        }
    }

    @Throws(Throwable::class)
    private fun handleLoadSafeCenter(classLoader: ClassLoader) {
        val cls = classLoader.loadClass("com.lenovo.xuipermissionmanager.XuiPermissionManager")
        val superclass: Class<*>? = cls.getSuperclass()
        val onCreate = cls.getDeclaredMethod("onCreate", Bundle::class.java)
        val superOnCreateMethod = superclass!!.getDeclaredMethod("onCreate", Bundle::class.java)
        val superOnCreateMethodHandle =
            MethodHandles.lookup().unreflectSpecial(superOnCreateMethod, cls)
        hookWithId(onCreate, "on_create_1") { chain: XposedInterface.Chain? ->
            // redirect to AOSP permission manager
            superOnCreateMethodHandle.invoke(chain!!.thisObject, chain.getArg(0))
            val activity = chain.thisObject as Activity
            activity.startActivity(Intent("android.intent.action.MANAGE_PERMISSIONS"))
            activity.finish()
            null
        }

        val onDestroy = cls.getDeclaredMethod("onDestroy")
        hookWithId(onDestroy, "on_destroy") { null }
    }

    private val isRowVersionTls = ThreadLocal<kotlin.Boolean?>()

    @SuppressLint("PrivateApi")
    private fun handleLoadSettings(classLoader: ClassLoader) {
        try {
            // Hook LenovoUtils.isRowVersion with ThreadLocal check
            val isRowVersionMethod = classLoader
                .loadClass("com.lenovo.common.utils.LenovoUtils")
                .getDeclaredMethod("isRowVersion")
            hookWithId(isRowVersionMethod, "is_row_version") { chain: XposedInterface.Chain? ->
                val value = isRowVersionTls.get()
                if (value != null) {
                    return@hookWithId value
                }
                chain!!.proceed()
            }
        } catch (_: Throwable) {
        }

        // Helper: wraps a method with isRowVersionTls set/remove
        try {
            val startMethod = classLoader
                .loadClass("com.android.settings.applications.appinfo.AppPermissionPreferenceController")
                .getDeclaredMethod("startManagePermissionsActivity")
            hookWithId(startMethod, "start") { chain: XposedInterface.Chain? ->
                isRowVersionTls.set(true)
                try {
                    return@hookWithId chain!!.proceed()
                } finally {
                    isRowVersionTls.remove()
                }
            }
        } catch (_: Throwable) {
        }

        try {
            val prefClass = classLoader.loadClass("androidx.preference.Preference")
            val clickMethod = classLoader
                .loadClass("com.lenovo.settings.privacy.PrivacyManagerPreferenceController")
                .getDeclaredMethod("handlePreferenceTreeClick", prefClass)
            hookWithId(clickMethod, "click") { chain: XposedInterface.Chain? ->
                isRowVersionTls.set(true)
                try {
                    return@hookWithId chain!!.proceed()
                } finally {
                    isRowVersionTls.remove()
                }
            }
        } catch (_: Throwable) {
        }

        try {
            if (Build.VERSION.SDK_INT >= 36) {
                val permClickMethod = classLoader
                    .loadClass("com.lenovo.settings.applications.LenovoAppHeaderPreferenceController")
                    .getDeclaredMethod("handlePermissionClick")
                hookWithId(permClickMethod, "perm_click") { chain: XposedInterface.Chain? ->
                    isRowVersionTls.set(true)
                    try {
                        return@hookWithId chain!!.proceed()
                    } finally {
                        isRowVersionTls.remove()
                    }
                }
            } else {
                val viewClass = classLoader.loadClass("android.view.View")
                val lambdaMethod = classLoader
                    .loadClass("com.lenovo.settings.applications.LenovoAppHeaderPreferenceController")
                    .getDeclaredMethod(
                        $$"lambda$initAppEntryList$0$com-lenovo-settings-applications-LenovoAppHeaderPreferenceController",
                        viewClass
                    )
                hookWithId(lambdaMethod, "lambda") { chain: XposedInterface.Chain? ->
                    isRowVersionTls.set(true)
                    try {
                        return@hookWithId chain!!.proceed()
                    } finally {
                        isRowVersionTls.remove()
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleLoadPermissionController(classLoader: ClassLoader) {
        var zuiUtilsCls: Class<*>? = null
        try {
            zuiUtilsCls = classLoader.loadClass("com.android.permissioncontroller.extra.ZuiUtils")
        } catch (_: ClassNotFoundException) {
            try {
                zuiUtilsCls =
                    classLoader.loadClass("com.android.permissioncontroller.permission.utils.ZuiUtils")
            } catch (_: ClassNotFoundException) {
            }
        }
        if (zuiUtilsCls != null) {
            try {
                val m = zuiUtilsCls.getDeclaredMethod("isCTSandGTS", String::class.java)
                hookWithId(m, "hook_165") { Boolean.TRUE }
            } catch (_: Throwable) {
            }
        } else {
            logger.warn("[PermissionControllerHook] ZuiUtils not found")
        }

        if (Build.VERSION.SDK_INT <= 34) {
            try {
                val onCreateMethod = classLoader
                    .loadClass("com.android.permissioncontroller.permission.ui.GrantPermissionsActivity")
                    .getDeclaredMethod("onCreate", Bundle::class.java)
                hookWithId(onCreateMethod, "on_create_2") { chain: XposedInterface.Chain? ->
                    val activity = chain!!.thisObject as Activity
                    activity.setTheme(Theme_DeviceDefault_Light_Dialog_Alert)
                    activity.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    val rootView = activity.window.decorView
                    rootView.filterTouchesWhenObscured = true
                    rootView.setPadding(0, 0, 0, 0)
                    chain.proceed()
                }
            } catch (_: Throwable) {
            }
        }
    }
}
