package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 强制关联启动时以小窗（freeform）模式打开目标 Activity。
 *
 * Hook com.android.server.wm.ZuiWmAutoRunManager.isAllowRelativeStart，
 * 当判定为跨 APP 关联启动时，注入 WINDOWING_MODE_FREEFORM=5 到
 * Bundle options 和 SafeActivityOptions 中。
 */
@SuppressLint("PrivateApi")
class ForceRelativeAppFreeform: SystemHookModule() {
    override fun getModuleName(): String = "force_relative_app_freeform"

    override fun getTargetPackages(): Array<out String> = arrayOf("system")

    companion object {
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

        // 默认启动器包名缓存
        @Volatile private var launcherPkgs: Set<String>? = null
        @Volatile private var launcherCacheExpire: Long = 0L
        private const val LAUNCHER_CACHE_TTL = 60_000L

        private val LOCK = Any()

        private fun resolveLauncherPackages(autoRunInstance: Any): Set<String> {
            val now = System.currentTimeMillis()
            val cached = launcherPkgs
            if (cached != null && now < launcherCacheExpire) return cached
            synchronized(LOCK) {
                val cached2 = launcherPkgs
                if (cached2 != null && now < launcherCacheExpire) return cached2
                try {
                    val ctxField: Field = autoRunInstance.javaClass.getDeclaredField("mContext")
                    ctxField.isAccessible = true
                    val ctx = ctxField.get(autoRunInstance) as? android.content.Context
                    if (ctx != null) {
                        val pm: PackageManager = ctx.packageManager
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                        }
                        val pkgs = pm.queryIntentActivities(homeIntent, 0)
                            .mapNotNull { it.activityInfo?.packageName }
                            .toSet()
                        launcherPkgs = pkgs
                        launcherCacheExpire = System.currentTimeMillis() + LAUNCHER_CACHE_TTL
                        return pkgs
                    }
                } catch (_: Exception) { }
            }
            return setOf("com.zui.launcher")
        }
    }

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader: ClassLoader = param.classLoader

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
            Intent::class.java,                            // intent
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
            val intent = chain.getArg(3) as Intent?
            // 优先使用 component.packageName（目标 Activity 真实归属包名），
            // 而非 intent.package（可能被 SDK 设为调用方自身包名）
            val targetPackage = intent?.component?.packageName ?: intent?.getPackage()

            // 仅在跨 APP 关联启动时注入 freeform
            // 排除：同包名自启动、启动器
            val launchers = resolveLauncherPackages(chain.thisObject)
            val isRelativeLaunch = callingPackage != null
                && callingPackage != targetPackage
                && callingPackage !in launchers

            if (isRelativeLaunch) {
                val bundle = chain.getArg(10) as Bundle?
                bundle?.putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)

                val safeOpts = chain.getArg(13)
                if (safeOpts != null) {
                    try {
                        val f = safeOptsClass.getDeclaredField("mOriginalOptions")
                        f.isAccessible = true
                        val originalOpts = f.get(safeOpts)
                        if (originalOpts != null) {
                            val setWM = originalOpts.javaClass.getMethod(
                                "setLaunchWindowingMode", Int::class.javaPrimitiveType)
                            setWM.invoke(originalOpts, WINDOWING_MODE_FREEFORM)
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
