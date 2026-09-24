package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Forces relative app launches to open the target Activity in freeform (small window) mode.
 *
 * Hooks com.android.server.wm.ZuiWmAutoRunManager.isAllowRelativeStart; when a
 * cross-app relative launch is detected, injects WINDOWING_MODE_FREEFORM=5 into
 * the Bundle options and SafeActivityOptions.
 */
@SuppressLint("PrivateApi")
class ForceRelativeAppFreeform: SystemHookModule() {
    override fun getModuleName(): String = "force_relative_app_freeform"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    companion object {
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

        // Default launcher package name cache
        @Volatile private var launcherPkgs: Set<String>? = null
        @Volatile private var launcherCacheExpire: Long = 0L
        private const val LAUNCHER_CACHE_TTL = 60_000L

        private val LOCK = Any()

        private fun resolveLauncherPackages(
            autoRunInstance: Any,
            log: (String) -> Unit
        ): Set<String> {
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
                        // Query both HOME and DEFAULT categories so launchers whose
                        // MAIN activity lacks CATEGORY_HOME (e.g. leanback / dual-home)
                        // are still collected.
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        }
                        val pkgs = pm.queryIntentActivities(homeIntent, 0)
                            .mapNotNull { it.activityInfo?.packageName }
                            .toSet()
                        if (pkgs.isNotEmpty()) {
                            launcherPkgs = pkgs
                            launcherCacheExpire = System.currentTimeMillis() + LAUNCHER_CACHE_TTL
                        }
                        log(
                            "resolveLauncherPackages: query ACTION_MAIN|HOME|DEFAULT -> $pkgs" +
                                " (cached=${pkgs.isNotEmpty()})"
                        )
                        return if (pkgs.isNotEmpty()) pkgs
                        else setOf(ScopeKeys.LAUNCHER.packageName)
                    }
                    log("resolveLauncherPackages: mContext field resolved to null, fallback")
                } catch (e: Exception) {
                    log("resolveLauncherPackages: query failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            return setOf(ScopeKeys.LAUNCHER.packageName)
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
            // Prefer component.packageName (the real owning package of the target Activity)
            // over intent.package (which the SDK may set to the caller's own package)
            val targetPackage = intent?.component?.packageName ?: intent?.getPackage()

            val launchers = resolveLauncherPackages(chain.thisObject) { logger.debug(it) }

            // Reliable "launched from a visible foreground context" signal: the framework's
            // own ZuiWmAutoRunManager.isTopAppPackage(callingPackage) reports whether the
            // caller currently owns a visible task or the focused window. A launcher icon
            // tap always satisfies this; a background relative-start usually does not.
            val callerIsTop = try {
                val isTopMethod = findMethod(
                    chain.thisObject.javaClass, "isTopAppPackage", String::class.java)
                isTopMethod.invoke(chain.thisObject, callingPackage) as Boolean
            } catch (e: Exception) {
                logger.debug("isTopAppPackage lookup failed: ${e.javaClass.simpleName}: ${e.message}")
                null // unknown — fall back to launcher-set exclusion only
            }

            // Inject freeform only for cross-app relative launches.
            // Excluded: same-package self launches, launchers, and callers that are
            // currently the top/visible app (e.g. launched from the home screen).
            val isRelativeLaunch = callingPackage != null
                && callingPackage != targetPackage
                && callingPackage !in launchers
                && callerIsTop != true

            logger.debug(
                "relative-start check: caller=$callingPackage target=$targetPackage" +
                    " launchers=$launchers callerIsTop=$callerIsTop" +
                    " intent=($intent) inject=$isRelativeLaunch"
            )

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
