package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Excludes whitelisted freeform apps from the launcher's recents "clean all"
 * (com.android.quickstep.views.RecentsView dismissAllUnlimitedTasks, the method that
 * ends by calling OverviewUtilities.removeAllRunningAppProcesses).
 *
 * The batch method receives (ArrayList<Task>, IntSet protectedTaskIds) and already
 * skips every task whose id is in the IntSet (that is how ZUI keeps user-locked
 * cards), and it feeds the same task list into the batch killer as the SURVIVE set.
 * So adding the whitelisted task ids to the IntSet makes the native skip machinery
 * protect them on both fronts — no task removal and no batch kill.
 *
 * Manual swipe-dismiss does NOT go through this method: the removeTask/killUid calls
 * of a single dismissal are deliberately left alone so the user can always kill a
 * whitelisted app by hand (agreed semantics: keep-alive protects against automatic
 * cleanups only).
 *
 * The target method is located by signature (single void method taking
 * (ArrayList, IntSet)) instead of by its obfuscated name, which changes between
 * launcher builds.
 *
 * Never touch the ArrayList<Task> argument itself — injecting package-name strings
 * into it crashes the AsyncTask with a ClassCastException (String cannot be cast to
 * Task).
 */
@SuppressLint("PrivateApi")
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
        logger.info("Installing FreeformKeepAliveLauncherHook on RecentsView")
        try {
            val recentsViewClass: Class<*> = classLoader.loadClass(
                "com.android.quickstep.views.RecentsView")
            val intSetClass: Class<*> = classLoader.loadClass(
                "com.android.launcher3.util.IntSet")
            val taskClass: Class<*> = classLoader.loadClass(
                "com.android.systemui.shared.recents.model.Task")
            val taskKeyClass: Class<*> = classLoader.loadClass(
                $$"com.android.systemui.shared.recents.model.Task$TaskKey"
            )

            val candidates = recentsViewClass.declaredMethods.filter {
                it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == java.util.ArrayList::class.java &&
                    it.parameterTypes[1] == intSetClass &&
                    it.returnType == Void.TYPE
            }
            logger.info(
                "RecentsView (ArrayList, IntSet) void candidates: " +
                    candidates.joinToString { it.name })
            // dismissAllUnlimitedTasks is obfuscated per build ("P5" on current ZUI
            // builds). Prefer the known name; degrade to the first candidate with a
            // logged warning when the name changes.
            val batchClean: Method = candidates.firstOrNull { it.name == "P5" }
                ?: candidates.firstOrNull()
                ?: run {
                    logger.error(
                        "dismissAllUnlimitedTasks-like method not found in RecentsView, " +
                            "batch clean-all will not skip whitelisted apps")
                    return
                }
            logger.info("Batch clean-all method selected: ${batchClean.name}")
            val keyField = findField(taskClass, "key")
            val baseIntentField = findField(taskKeyClass, "baseIntent")
            val idField = findField(taskKeyClass, "id")
            val intSetAdd: Method = intSetClass.getMethod(
                "add", Int::class.javaPrimitiveType)

            hookWithId(batchClean, "freeform_keep_alive_clean_all_skip") { chain ->
                logger.debug("clean-all hook fired, args=${chain.args.map { it?.javaClass?.name }}")
                try {
                    val whitelist = currentWhitelist()
                    val tasks = chain.args[0] as? ArrayList<*>
                    val protectedIds = chain.args[1]
                    if (whitelist.isNotEmpty() && tasks != null && protectedIds != null) {
                        for (task in tasks) {
                            if (task == null) continue
                            val key = keyField.get(task) ?: continue
                            val intent = baseIntentField.get(key) ?: continue
                            val pkg = (intent as android.content.Intent)
                                .component?.packageName ?: continue
                            if (whitelist.contains(pkg)) {
                                val taskId = idField.getInt(key)
                                intSetAdd.invoke(protectedIds, taskId)
                                logger.debug(
                                    "Protected whitelisted task $taskId ($pkg) " +
                                        "from batch clean-all"
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    logger.error("clean-all skip failed, letting batch proceed", t)
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
