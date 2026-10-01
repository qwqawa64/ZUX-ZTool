package com.qimian233.ztool.hook.modules.launcher

import android.content.Context
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Shields whitelisted freeform apps from the launcher's recents memory cleaner
 * (com.zui.launcher.util.OverviewUtilities, tag "Launcher.Recents").
 *
 * The batch cleaner's uid/killBackgroundProcesses kills are blocked system-side by
 * FreeformKeepAliveSystemHook (AMS.killUid / AMS.killBackgroundProcesses); here we
 * only block the single-package kill that carries the package name. Never touch the
 * ArrayList<Task> argument of removeAllRunningAppProcesses — injecting package names
 * into it crashes the AsyncTask with a ClassCastException.
 *
 * The whitelist is read with the same short throttle as the system-side hook so
 * edits apply without restarting the launcher, while the hot paths stay IPC-free.
 */
class FreeformKeepAliveLauncherHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FREEFORM_KEEP_ALIVE_ENABLED.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Volatile
    private var cachedWhitelist: Set<String> = emptySet()

    @Volatile
    private var whitelistLoadedAt: Long = 0L

    private fun currentWhitelist(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - whitelistLoadedAt > WHITELIST_REFRESH_INTERVAL_MS) {
            cachedWhitelist = (remotePreferences
                .getString(PreferenceKeys.FREEFORM_KEEP_ALIVE_PACKAGES.name, "") ?: "")
                .split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            whitelistLoadedAt = now
        }
        return cachedWhitelist
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("Installing FreeformKeepAliveLauncherHook on OverviewUtilities")
        try {
            val utilsClass: Class<*> = classLoader.loadClass(
                "com.zui.launcher.util.OverviewUtilities")

            // static removeAppProcess(Context, int userId, String packageName, int uid) —
            // single task swipe-dismiss and the PRC force-kill path. The batch cleaner's
            // uid/killBackgroundProcesses calls are blocked system-side (see
            // FreeformKeepAliveSystemHook); the task ArrayList must not be touched —
            // injecting package names into it crashes the AsyncTask with a
            // ClassCastException (String cannot be cast to Task).
            val singleKill: Method = findMethod(
                utilsClass, "removeAppProcess",
                Context::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(singleKill, "freeform_keep_alive_single_kill") { chain ->
                val packageName = chain.args[2] as? String
                if (packageName != null && currentWhitelist().contains(packageName)) {
                    logger.debug("Blocked recents kill of whitelisted $packageName")
                    return@hookWithId null
                }
                chain.proceed()
            }
            logger.info("FreeformKeepAliveLauncherHook installed [OK]")
        } catch (t: Throwable) {
            logger.error("Failed to install FreeformKeepAliveLauncherHook", t)
        }
    }

    private companion object {
        const val WHITELIST_REFRESH_INTERVAL_MS = 5000L
    }
}
