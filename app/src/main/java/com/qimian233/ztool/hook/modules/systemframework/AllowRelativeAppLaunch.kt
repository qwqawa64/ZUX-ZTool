package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.os.Bundle
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class AllowRelativeAppLaunch: SystemHookModule() {
    override fun getModuleName(): String = "allow_relative_app_launch"

    override fun getTargetPackages(): Array<out String> = arrayOf("system")

    companion object {
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"
    }

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader: ClassLoader = param.classLoader

        // Hook 1: getRelativeAppStatus 始终返回 1（允许关联启动）
        val securityBinderClass: Class<*> = classLoader.loadClass(
            $$"com.android.server.ZuiSecurityService$ZuiSecurityServiceBinder")
        val getStatusMethod: Method = findMethod(securityBinderClass, "getRelativeAppStatus",
            String::class.java, String::class.java)
        hookWithId(getStatusMethod, "relative_app_status") { _ ->
            1
        }

        // Hook 2: 关联启动获准后，强制以小窗模式启动目标 Activity
        val autoRunClass: Class<*> = classLoader.loadClass(
            "com.android.server.wm.ZuiWmAutoRunManager")
        val iAppThreadClass: Class<*> = classLoader.loadClass(
            "android.app.IApplicationThread")
        val profilerInfoClass: Class<*> = classLoader.loadClass(
            "android.app.ProfilerInfo")
        val binderClass: Class<*> = classLoader.loadClass(
            "android.os.IBinder")
        val safeOptsClass: Class<*> = classLoader.loadClass(
            "com.android.server.wm.SafeActivityOptions")

        val isAllowRelativeStartMethod: Method = findMethod(autoRunClass, "isAllowRelativeStart",
            iAppThreadClass,                               // IApplicationThread caller
            String::class.java,                            // callingPackage
            String::class.java,                            // callingFeatureId
            android.content.Intent::class.java,            // intent
            String::class.java,                            // resolvedType
            binderClass,                                   // resultTo
            String::class.java,                            // resultWho
            Int::class.javaPrimitiveType,                  // requestCode
            Int::class.javaPrimitiveType,                  // startFlags
            profilerInfoClass,                             // profilerInfo
            Bundle::class.java,                            // options
            Int::class.javaPrimitiveType,                  // userId
            Boolean::class.javaPrimitiveType,              // z
            safeOptsClass                                  // safeActivityOptions
        )

        hookWithId(isAllowRelativeStartMethod, "relative_app_force_freeform") { chain ->
            val callingPackage = chain.getArg(1) as String?
            val intent = chain.getArg(3) as android.content.Intent?
            val targetPackage = intent?.getPackage() ?: intent?.component?.packageName

            // 仅在跨 APP 关联启动时注入 freeform
            // 排除：同包名自启动、启动器、无法判定目标包名
            val isRelativeLaunch = callingPackage != null
                && targetPackage != null
                && callingPackage != targetPackage
                && callingPackage != "com.zui.launcher"

            if (isRelativeLaunch) {
                // 修改 Bundle 中的 windowing mode
                val bundle = chain.getArg(10) as Bundle?
                bundle?.putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)

                // 修改 SafeActivityOptions 中的启动窗口模式
                val safeOpts = chain.getArg(13)
                if (safeOpts != null) {
                    try {
                        val originalOptsField = safeOptsClass.getDeclaredField("mOriginalOptions")
                        originalOptsField.isAccessible = true
                        val originalOpts = originalOptsField.get(safeOpts)
                        if (originalOpts != null) {
                            val setWindowingMode = originalOpts.javaClass.getMethod(
                                "setLaunchWindowingMode", Int::class.javaPrimitiveType)
                            setWindowingMode.invoke(originalOpts, WINDOWING_MODE_FREEFORM)
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to set freeform on SafeActivityOptions: ${e.message}")
                    }
                }
            }

            chain.proceed()
        }
    }
}