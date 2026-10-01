package com.qimian233.ztool.hook.modules.systemframework

import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

/**
 * Keeps whitelisted freeform-window apps from ever entering the cached OOM tier.
 *
 * ZUI's freeform hide path only does `setAlwaysOnTop(false) + moveTaskToBack` (see
 * com.android.server.wm.OvfWmHideShowController#bringToBack), so the activities get
 * stopped and the process drops to the cached tier. This hook pins the process back
 * to the perceptible tier by reusing Lenovo's own AdjCustomize machinery in
 * com.android.server.am.OomAdjuster:
 *
 * - `applyOomAdjLSP(ProcessRecord, boolean, long, long, int, boolean)` runs for every
 *   process on every OOM adjustment; we hook it and, for whitelisted packages whose
 *   computed adj reached the previous-app/cached range (>= 700), we register the
 *   process via `constructNewProtected(processName, pid, uid, sysAdj, customizeTime)`
 *   and immediately write the pinned adj (setCurAdj/setSetAdj + the static
 *   ProcessList.setOomAdj), which is exactly what AdjCustomizeHandler does later.
 *   The registered entry makes the handler re-pin the value after `customizeTime`
 *   minutes and prune it once the pid dies; protection is released automatically by
 *   the vendor path once the process returns to the foreground (adj <= 200).
 * - `constructNewProtected` only schedules the handler message (no immediate write),
 *   hence the direct write here. If mAdjCustomizeHandler was never created
 *   (LgsiFeatures "ZuiAdjCustomize" disabled) the registration is skipped and only
 *   the direct write runs.
 *
 * The whitelist is read from remote preferences with a short throttle instead of
 * once at startup: applyOomAdjLSP is a hot path, so a live read per call would be an
 * IPC storm, but a cached set lets users change the list without a system reboot.
 */
class FreeformKeepAliveSystemHook : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FREEFORM_KEEP_ALIVE_ENABLED.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

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

    /** Exact package or its `:subprocess` variants, mirroring processName conventions. */
    private fun matchesWhitelist(processName: String, whitelist: Set<String>): Boolean =
        whitelist.any { pkg -> processName == pkg || processName.startsWith("$pkg:") }

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        logger.info("Installing FreeformKeepAliveSystemHook on OomAdjuster")
        try {
            val oomAdjusterClass: Class<*> = classLoader.loadClass(
                "com.android.server.am.OomAdjuster")
            val processRecordClass: Class<*> = classLoader.loadClass(
                "com.android.server.am.ProcessRecord")
            val processListClass: Class<*> = classLoader.loadClass(
                "com.android.server.am.ProcessList")
            val processStateClass: Class<*> = classLoader.loadClass(
                "com.android.server.am.ProcessStateRecord")

            // Overload confirmed via smali: (ProcessRecord, boolean, long, long, int, boolean)
            val applyOomAdj: Method = findMethod(
                oomAdjusterClass, "applyOomAdjLSP",
                processRecordClass,
                Boolean::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            val constructNewProtected: Method = findMethod(
                oomAdjusterClass, "constructNewProtected",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val handlerField = findField(oomAdjusterClass, "mAdjCustomizeHandler")
            val protectedAppsField = findField(oomAdjusterClass, "mAdjProtectedApps")
            // static ProcessList.setOomAdj(int pid, int uid, int adj)
            val setOomAdj: Method = findMethod(
                processListClass, "setOomAdj",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val stateField = findField(processRecordClass, "mState")
            val processNameField = findField(processRecordClass, "processName")
            val getCurAdj = processStateClass.getMethod(
                "getCurAdj")
            val setCurAdj = processStateClass.getMethod(
                "setCurAdj", Int::class.javaPrimitiveType)
            val setSetAdj = processStateClass.getMethod(
                "setSetAdj", Int::class.javaPrimitiveType)

            hookWithId(applyOomAdj, "freeform_keep_alive_apply_oom_adj") { chain ->
                val result = chain.proceed()
                try {
                    val whitelist = currentWhitelist()
                    if (whitelist.isEmpty()) {
                        return@hookWithId result
                    }
                    val oomAdjuster = chain.thisObject ?: return@hookWithId result
                    val process = chain.args[0] ?: return@hookWithId result
                    val processName = processNameField.get(process) as String
                    if (!matchesWhitelist(processName, whitelist)) {
                        return@hookWithId result
                    }
                    val state = stateField.get(process)
                    val curAdj = getCurAdj.invoke(state) as Int
                    if (curAdj < PREVIOUS_APP_ADJ) {
                        return@hookWithId result
                    }
                    val pid =
                        (process.javaClass.getMethod("getPid").invoke(process)) as Int
                    val uid = process.javaClass.getField("uid").getInt(process)
                    val handler = handlerField.get(oomAdjuster)
                    if (handler != null) {
                        val protectedApps = protectedAppsField.get(oomAdjuster)
                                as HashMap<*, *>
                        synchronized(protectedApps) {
                            if (!protectedApps.containsKey(pid)) {
                                constructNewProtected.invoke(
                                    oomAdjuster,
                                    processName,
                                    pid,
                                    uid,
                                    PINNED_ADJ,
                                    PINNED_DURATION_MINUTES
                                )
                            }
                        }
                    }
                    setCurAdj.invoke(state, PINNED_ADJ)
                    setSetAdj.invoke(state, PINNED_ADJ)
                    setOomAdj.invoke(null, pid, uid, PINNED_ADJ)
                    logger.debug(
                        "Pinned adj of $processName (pid=$pid, uid=$uid) " +
                            "from $curAdj to $PINNED_ADJ"
                    )
                } catch (t: Throwable) {
                    logger.error("FreeformKeepAlive pin failed", t)
                }
                result
            }
            logger.info("FreeformKeepAliveSystemHook installed [OK]")
        } catch (t: Throwable) {
            logger.error("Failed to install FreeformKeepAliveSystemHook", t)
        }
    }

    private companion object {
        const val PINNED_ADJ = 200
        const val PINNED_DURATION_MINUTES = 10080
        const val PREVIOUS_APP_ADJ = 700
        const val WHITELIST_REFRESH_INTERVAL_MS = 5000L
    }
}
