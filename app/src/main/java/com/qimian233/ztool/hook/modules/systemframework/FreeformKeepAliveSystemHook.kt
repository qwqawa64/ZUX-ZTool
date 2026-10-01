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
 * - Preferred: hook `dynamicComputeAdj(processName, key, pid, uid, curAdj, z)`, which
 *   runs inside applyOomAdjLSP BEFORE the adj is applied; returning the pinned value
 *   makes the freeze check and the kernel write see 200 in the same pass.
 * - Freeze bypass: `CachedAppOptimizer.onOomAdjustChanged` is skipped for whitelisted
 *   processes so the cached-app freezer never touches them.
 * - Fallback: after `applyOomAdjLSP(ProcessRecord, boolean, long, long, int, boolean)`
 *   applied a cached-tier adj, re-pin via `constructNewProtected(...)` (Lenovo's own
 *   AdjCustomize registration, re-pinned later by AdjCustomizeHandler and pruned when
 *   the pid dies) plus a direct write (setCurAdj/setSetAdj/ProcessList.setOomAdj),
 *   which is exactly what the handler does later.
 * - If mAdjCustomizeHandler was never created (LgsiFeatures "ZuiAdjCustomize"
 *   disabled), registration is skipped and only the direct write runs.
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
            val processNameField = findField(processRecordClass, "processName")

            // Preferred integration point: dynamicComputeAdj runs inside
            // applyOomAdjLSP BEFORE the computed adj is applied/frozen; returning the
            // pinned value here makes the freeze check and the kernel write see 200.
            // Signature (smali): (String processName, String key, int pid, int uid,
            // int curAdj, boolean z) -> int
            try {
                val dynamicComputeAdj: Method = findMethod(
                    oomAdjusterClass, "dynamicComputeAdj",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                hookWithId(dynamicComputeAdj, "freeform_keep_alive_dynamic_adj") { chain ->
                    val processName = chain.args[0] as? String
                    val computedAdj = chain.args[4] as? Int ?: 0
                    if (processName != null &&
                        matchesWhitelist(processName, currentWhitelist()) &&
                        computedAdj >= PREVIOUS_APP_ADJ
                    ) {
                        logger.debug(
                            "Pinned adj of $processName from $computedAdj " +
                                "to $PINNED_ADJ (dynamicComputeAdj)"
                        )
                        return@hookWithId PINNED_ADJ
                    }
                    chain.proceed()
                }
                logger.info("dynamicComputeAdj hook installed")
            } catch (t: Throwable) {
                logger.error("dynamicComputeAdj not hookable, relying on fallback", t)
            }

            // Freeze bypass: CachedAppOptimizer.onOomAdjustChanged(int setAdj,
            // int curAdj, ProcessRecord) is the standard cached-app freeze trigger.
            try {
                val cachedOptimizerClass: Class<*> = classLoader.loadClass(
                    "com.android.server.am.CachedAppOptimizer")
                val onOomAdjustChanged: Method = findMethod(
                    cachedOptimizerClass, "onOomAdjustChanged",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    processRecordClass
                )
                hookWithId(onOomAdjustChanged, "freeform_keep_alive_no_freeze") { chain ->
                    val process = chain.args[2]
                    val processName = process?.let {
                        findField(processRecordClass, "processName").get(it) as? String
                    }
                    if (processName != null &&
                        matchesWhitelist(processName, currentWhitelist())
                    ) {
                        logger.debug("Freeze suppressed for whitelisted $processName")
                        return@hookWithId null
                    }
                    chain.proceed()
                }
                logger.info("onOomAdjustChanged freeze bypass installed")
            } catch (t: Throwable) {
                logger.error("Freeze bypass (CachedAppOptimizer) not installed", t)
            }

            // Last killer: recents clean-all calls ActivityTaskManagerService.removeTask
            // (kill reason "remove task"), which force-finishes the task and kills the
            // process regardless of adj. Block removal for whitelisted packages —
            // the task then also stays in recents.
            try {
                val atmsClass: Class<*> = classLoader.loadClass(
                    "com.android.server.wm.ActivityTaskManagerService")
                val removeTask: Method = findMethod(
                    atmsClass, "removeTask", Int::class.javaPrimitiveType)
                val rootField = findField(atmsClass, "mRootWindowContainer")
                hookWithId(removeTask, "freeform_keep_alive_remove_task") { chain ->
                    try {
                        val atms = chain.thisObject
                        val root = rootField.get(atms)
                        val task = root.javaClass.methods
                            .firstOrNull {
                                it.name == "anyTaskForId" &&
                                    it.parameterTypes.size == 1 &&
                                    it.parameterTypes[0] == Int::class.javaPrimitiveType
                            }
                            ?.invoke(root, chain.args[0])
                        val intent = task?.javaClass?.getMethod("getBaseIntent")
                            ?.invoke(task)
                        val pkg = (intent as? android.content.Intent)?.component?.packageName
                        if (pkg != null && matchesWhitelist(pkg, currentWhitelist())) {
                            logger.debug("Blocked removeTask for whitelisted $pkg")
                            return@hookWithId false
                        }
                    } catch (t: Throwable) {
                        logger.error("removeTask filter failed, letting it pass", t)
                    }
                    chain.proceed()
                }
                logger.info("removeTask blocker installed")
            } catch (t: Throwable) {
                logger.error("removeTask blocker not installed", t)
            }

            // Both recents-cleaner kill primitives, blocked at the AMS entry points so
            // single kills, batch cleans and overseas killBackgroundProcesses paths are
            // all covered:
            // - killUid(int appId, int userId, int oomAdj, int, String reason): uid =
            //   userId * 100000 + appId; skip when any live process of that uid matches
            //   the whitelist (force-kill ignores adj, so this cannot be left open).
            // - killBackgroundProcesses(String packageName, int userId).
            try {
                val amsClass: Class<*> = classLoader.loadClass(
                    "com.android.server.am.ActivityManagerService")
                val processListField = findField(amsClass, "mProcessList")
                val getLruProcesses = processListClass.getMethod("getLruProcessesLOSP")
                val killUid: Method = findMethod(
                    amsClass, "killUid",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                hookWithId(killUid, "freeform_keep_alive_kill_uid") { chain ->
                    try {
                        val appId = chain.args[0] as? Int ?: 0
                        val userId = chain.args[1] as? Int ?: 0
                        val uid = userId * PER_USER_RANGE + appId
                        val ams = chain.thisObject
                        val processes = getLruProcesses.invoke(processListField.get(ams))
                                as ArrayList<*>
                        for (process in processes) {
                            if (process == null) continue
                            val procUid = process.javaClass.getField("uid").getInt(process)
                            if (procUid != uid) continue
                            val processName = processNameField.get(process) as String
                            if (matchesWhitelist(processName, currentWhitelist())) {
                                logger.debug(
                                    "Blocked killUid for whitelisted $processName (uid=$uid)"
                                )
                                return@hookWithId null
                            }
                        }
                    } catch (t: Throwable) {
                        logger.error("killUid filter failed, letting it pass", t)
                    }
                    chain.proceed()
                }
                val killBackgroundProcesses: Method = findMethod(
                    amsClass, "killBackgroundProcesses",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                hookWithId(killBackgroundProcesses, "freeform_keep_alive_kill_bg") { chain ->
                    val pkg = chain.args[0] as? String
                    if (pkg != null && matchesWhitelist(pkg, currentWhitelist())) {
                        logger.debug("Blocked killBackgroundProcesses for whitelisted $pkg")
                        return@hookWithId null
                    }
                    chain.proceed()
                }
                logger.info("AMS kill blockers installed")
            } catch (t: Throwable) {
                logger.error("AMS kill blockers not installed", t)
            }

            // Fallback: after applyOomAdjLSP has applied the (possibly cached) adj,
            // re-pin whitelisted processes directly, mirroring AdjCustomizeHandler.
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
        const val PER_USER_RANGE = 100000
        const val WHITELIST_REFRESH_INTERVAL_MS = 5000L
    }
}
