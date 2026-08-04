package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class AllowRelativeAppLaunch: SystemHookModule() {
    override fun getModuleName(): String = "allow_relative_app_launch"

    override fun getTargetPackages(): Array<out String> = arrayOf("system")

    companion object {
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

        // 默认启动器包名缓存
        @Volatile private var launcherPkgs: Set<String>? = null
        @Volatile private var launcherCacheExpire: Long = 0L
        private const val LAUNCHER_CACHE_TTL = 60_000L // 1 minute

        private val LOCK = Any()

        /**
         * 通过 ZuiWmAutoRunManager.mContext 查询系统所有注册为启动器的包名，
         * 结果缓存 60 秒。解析失败时回退到硬编码 ZUI 启动器。
         */
        private fun resolveLauncherPackages(autoRunInstance: Any): Set<String> {
            val now = System.currentTimeMillis()
            val cached = launcherPkgs
            if (cached != null && now < launcherCacheExpire) {
                return cached
            }
            synchronized(LOCK) {
                // double-check
                val cached2 = launcherPkgs
                if (cached2 != null && now < launcherCacheExpire) {
                    return cached2
                }
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
                } catch (_: Exception) {
                    // fall through to fallback
                }
            }
            // fallback: 硬编码 ZUI 默认启动器
            return setOf("com.zui.launcher")
        }
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

            logger.debug("callingPackage: $callingPackage, targetPackage: $targetPackage")
            // 仅在跨 APP 关联启动时注入 freeform
            // 排除：同包名自启动、启动器
            // 打开网页链接时目标包名是 null, 这个时候也应该打开小窗
            val launchers = resolveLauncherPackages(chain.thisObject)
            val isRelativeLaunch = callingPackage != null
                // && targetPackage != null
                && callingPackage != targetPackage
                && callingPackage !in launchers

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