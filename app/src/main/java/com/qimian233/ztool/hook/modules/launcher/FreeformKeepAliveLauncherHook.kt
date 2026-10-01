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
 * On every recents cleanup ZUI kills every process that is not part of the visible
 * task list — via ActivityManager.killBackgroundProcesses on overseas builds and via
 * SystemUiProxy.killUid(uid, "ZuiMemoryCleaner_Recents") on PRC builds. Both paths
 * are closed here without touching the obfuscated RecentsView internals:
 *
 * - `removeAllRunningAppProcesses(Context, ArrayList, boolean)` receives the package
 *   names of the tasks that must SURVIVE the cleanup (they are excluded from both
 *   kill sets inside the AsyncTask). We inject the whitelist into that list, so the
 *   batch cleaner skips those packages on both PRC and overseas builds.
 * - `removeAppProcess(Context, int, String, int)` kills one package (single task
 *   swipe-dismiss and the PRC force-kill path); calls for whitelisted packages are
 *   short-circuited.
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
    private var whitelistLoadedAt: Long = Long.MIN_VALUE

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
            val arrayListClass: Class<*> = java.util.ArrayList::class.java

            // removeAllRunningAppProcesses(Context, ArrayList<Task> keepPackages, boolean)
            val batchClean: Method = findMethod(
                utilsClass, "removeAllRunningAppProcesses",
                Context::class.java,
                arrayListClass,
                Boolean::class.javaPrimitiveType
            )
            hookWithId(batchClean, "freeform_keep_alive_batch_clean") { chain ->
                @Suppress("UNCHECKED_CAST")
                val keepList = chain.args[1] as? ArrayList<Any?>
                if (keepList != null) {
                    keepList.addAll(currentWhitelist())
                    logger.debug(
                        "Injected ${currentWhitelist().size} keep-alive packages " +
                            "into the recents batch cleaner"
                    )
                }
                chain.proceed()
            }

            // static removeAppProcess(Context, int userId, String packageName, int uid)
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
