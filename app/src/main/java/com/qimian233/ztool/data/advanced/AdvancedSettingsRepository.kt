package com.qimian233.ztool.data.advanced

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.XposedServiceBridge
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Advanced options repository.
 * <p>
 * Encapsulates developer features such as module hot reload,
 * handling thread switching and result aggregation.
 * </p>
 */
class AdvancedSettingsRepository(
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Get the API version, or 0 when not activated */
    fun getApiVersion(): Int = XposedServiceBridge.getApiVersion()

    /** Get the list of running hook targets */
    fun getRunningTargets(): List<HookedTarget> = XposedServiceBridge.getRunningTargets()

    /**
     * Hot-reload all running targets not in the RELOADING state.
     *
     * @param onProgress callback after each target completes (main thread), params: (processName, status, message)
     * @param onComplete callback after all targets complete (main thread), params: (succeeded, failed, unsupported, died)
     */
    fun performHotReloadAll(
        onProgress: (target: HookedTarget, result: HotReloadResult) -> Unit,
        onComplete: (succeededCount: Int, failedCount: Int, unsupportedCount: Int, diedCount: Int, details: List<HotReloadDetail>) -> Unit
    ) {
        if (XposedServiceBridge.getApiVersion() < 102) {
            Log.e(TAG, "Low API version, unable to perform hot-reload")
            return
        }
        val targets = getRunningTargets()
        if (targets.isEmpty()) {
            onComplete(0, 0, 0, 0, emptyList())
            return
        }

        val eligible = targets.filter { it.state != HookedTarget.State.RELOADING }
        if (eligible.isEmpty()) {
            onComplete(0, 0, 0, 0, emptyList())
            return
        }

        val total = eligible.size
        val completed = AtomicInteger(0)
        val succeeded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val unsupported = AtomicInteger(0)
        val died = AtomicInteger(0)
        val details = java.util.Collections.synchronizedList(mutableListOf<HotReloadDetail>())

        for (target in eligible) {
            val callback = object : XposedService.HotReloadCallback {
                override fun onHotReloadResult(target: HookedTarget, result: HotReloadResult) {
                    val status = result.status()
                    val message = result.message() ?: ""
                    val processName = target.processName
                    val detail = HotReloadDetail(processName, status.name, message)
                    details.add(detail)

                    when (status) {
                        HotReloadResult.Status.SUCCEEDED -> {
                            Log.d(TAG, "Hot reload succeeded: $processName")
                            succeeded.incrementAndGet()
                        }
                        HotReloadResult.Status.FAILED -> {
                            Log.w(TAG, "Hot reload failed: $processName — $message")
                            failed.incrementAndGet()
                        }
                        HotReloadResult.Status.UNSUPPORTED -> {
                            Log.w(TAG, "Hot reload unsupported: $processName — $message")
                            unsupported.incrementAndGet()
                        }
                        HotReloadResult.Status.PROCESS_DIED -> {
                            Log.w(TAG, "Target process died: $processName — $message")
                            died.incrementAndGet()
                        }
                        HotReloadResult.Status.IN_PROGRESS -> { return }
                    }

                    mainHandler.post {
                        onProgress(target, result)
                        if (completed.incrementAndGet() >= total) {
                            mainHandler.post {
                                onComplete(
                                    succeeded.get(),
                                    failed.get(),
                                    unsupported.get(),
                                    died.get(),
                                    details.toList()
                                )
                            }
                        }
                    }
                }
            }

            try {
                XposedServiceBridge.hotReloadModule(target, Bundle(), callback)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while starting hot reload: ${target.processName}", e)
                failed.incrementAndGet()
                val detail = HotReloadDetail(target.processName, "FAILED", e.message ?: "unknown")
                details.add(detail)
                mainHandler.post {
                    onProgress(target, HotReloadResult(HotReloadResult.Status.FAILED, e.message))
                    if (completed.incrementAndGet() >= total) {
                        mainHandler.post {
                            onComplete(succeeded.get(), failed.get(), unsupported.get(), died.get(), details.toList())
                        }
                    }
                }
            }
        }
    }

    /**
     * Reset all persistent values modified by this app's hooks, executing item by item and aggregating results.
     *
     * Currently supported:
     * - doze_always_on: clears residue written by older versions via `settings put secure doze_always_on 1`.
     * - autorun: clears whitelist bits written by hooks in the attr column of the SafeCenter AutoRunManager table.
     * - mistouch: clears the Game Center mistouch-prevention persistence (Settings.Global.key_game_assistant_prevent_misoperation).
     *
     * @param onComplete callback after all items complete (caller thread), params: (succeeded, failed, unsupported, details)
     */
    fun resetPersistentValues(
        onComplete: (succeeded: Int, failed: Int, unsupported: Int, details: List<PersistentResetDetail>) -> Unit
    ) {
        val details = mutableListOf<PersistentResetDetail>()
        var succeeded = 0
        var failed = 0
        val unsupported = 0

        // 1. Native AOD switch (residue written by older shell versions)
        val aod = resetDozeAlwaysOn()
        if (aod.success) succeeded++ else failed++
        details += PersistentResetDetail(
            KEY_RESET_AOD, if (aod.success) "SUCCEEDED" else "FAILED", aod.message
        )

        // 2. App autorun state (SafeCenter AutoRunManager.attr whitelist bits)
        val autorun = resetAutorun()
        if (autorun.success) succeeded++ else failed++
        details += PersistentResetDetail(
            KEY_RESET_AUTORUN, if (autorun.success) "SUCCEEDED" else "FAILED", autorun.message
        )

        // 3. Game mistouch-prevention state (Game Center SettingsValueUtilKt → Settings.Global)
        val mistouch = resetMistakeTouch()
        if (mistouch.success) succeeded++ else failed++
        details += PersistentResetDetail(
            KEY_RESET_MISTOUCH, if (mistouch.success) "SUCCEEDED" else "FAILED", mistouch.message
        )

        onComplete(succeeded, failed, unsupported, details)
    }

    /**
     * Clear residue values written by older versions via `settings put secure doze_always_on 1`.
     * Native AOD is now handled by a hook (ForceNativeAod); deleting the residue lets the system restore its default.
     */
    private fun resetDozeAlwaysOn(): ResetOutcome {
        val current = shellExecutor.executeRootCommand("settings get secure doze_always_on")
        if (current.isSuccess) {
            val value = current.output.trim()
            if (value.isEmpty() || value.equals("null", ignoreCase = true)) {
                return ResetOutcome(true, "doze_always_on 无残留值，无需重置")
            }
        }
        val result = shellExecutor.executeRootCommand("settings delete secure doze_always_on")
        return if (result.isSuccess) {
            ResetOutcome(true, "已清除 doze_always_on 残留值")
        } else {
            ResetOutcome(false, "清除 doze_always_on 失败：${result.error}")
        }
    }

    /**
     * Clear whitelist bits written by the EnableAutorunByDefault hook in the SafeCenter AutoRunManager table.
     * Database: databases/perf_leemcenter.db of com.zui.safecenter / com.lenovo.safecenter,
     * attr column of the AutoRunManager table. Bitmask:
     * - USER_WHITE_LIST_APP = 0x20000000
     * - RELATIVE_APP_WHITE_LIST = 0x40000000
     * Only whitelist bits are cleared; the state column (user-toggled autorun switches) is untouched.
     */
    @SuppressLint("SdCardPath")
    private fun resetAutorun(): ResetOutcome {
        val whitelistMask = 0x20000000 or 0x40000000 // 1610612736
        val dbPaths = listOf(
            "/data/user/0/com.zui.safecenter/databases/perf_leemcenter.db",
            "/data/user/0/com.lenovo.safecenter/databases/perf_leemcenter.db"
        )
        var cleared = false
        for (dbPath in dbPaths) {
            // Skip if the database does not exist (may be the other package variant or no residue)
            val exists = shellExecutor.executeRootCommand("ls $dbPath")
            if (!exists.isSuccess) continue
            // The table may not exist (autorun manager never opened); verify the table first to avoid false sqlite3-unavailable reports
            val tableCheck = shellExecutor.executeRootCommand(
                "sqlite3 \"$dbPath\" \"SELECT name FROM sqlite_master WHERE type='table' AND name='AutoRunManager';\""
            )
            if (!tableCheck.isSuccess) {
                return ResetOutcome(false, "清除自启动白名单失败：sqlite3 不可用或数据库无法访问")
            }
            if (tableCheck.output.trim().isEmpty()) continue
            // Pre-check residue count
            val count = shellExecutor.executeRootCommand(
                "sqlite3 \"$dbPath\" \"SELECT count(*) FROM AutoRunManager WHERE (attr & $whitelistMask) != 0;\""
            )
            if (!count.isSuccess) {
                return ResetOutcome(false, "清除自启动白名单失败：${count.error}")
            }
            if (count.output.trim() == "0") continue
            // Clear the whitelist bits (keep other bits such as stubborn / relative)
            val update = shellExecutor.executeRootCommand(
                "sqlite3 \"$dbPath\" \"UPDATE AutoRunManager SET attr = attr & ~$whitelistMask;\""
            )
            if (!update.isSuccess) {
                return ResetOutcome(false, "清除自启动白名单失败：${update.error}")
            }
            cleared = true
        }
        return if (cleared) {
            ResetOutcome(true, "已清除自启动白名单残留")
        } else {
            ResetOutcome(true, "未发现自启动白名单残留")
        }
    }

    /**
     * Clear the Game Center mistouch-prevention persisted value.
     * Writer: `com.zui.util.SettingsValueUtilKt.setPreventMisoperation`,
     * ultimately landing in `Settings.Global.key_game_assistant_prevent_misoperation`.
     * AutoMistakeTouchHook only intercepts in-memory writes; deleting the residue lets the system restore its default.
     */
    private fun resetMistakeTouch(): ResetOutcome {
        val current = shellExecutor.executeRootCommand(
            "settings get global key_game_assistant_prevent_misoperation"
        )
        if (current.isSuccess) {
            val value = current.output.trim()
            if (value.isEmpty() || value.equals("null", ignoreCase = true)) {
                return ResetOutcome(true, "防误触无残留值，无需重置")
            }
        }
        val result = shellExecutor.executeRootCommand(
            "settings delete global key_game_assistant_prevent_misoperation"
        )
        return if (result.isSuccess) {
            ResetOutcome(true, "已清除防误触持久化值")
        } else {
            ResetOutcome(false, "清除防误触失败：${result.error}")
        }
    }

    private data class ResetOutcome(val success: Boolean, val message: String)

    companion object {
        private const val TAG = "AdvancedRepo"
        private const val KEY_RESET_AOD = "doze_always_on"
        private const val KEY_RESET_AUTORUN = "autorun"
        private const val KEY_RESET_MISTOUCH = "mistouch"
    }
}

/**
 * Detail of a single hot reload operation, for UI display.
 */
data class HotReloadDetail(
    val processName: String,
    val status: String,
    val message: String
)

/**
 * Detail of a single persistent value reset, for UI display.
 */
data class PersistentResetDetail(
    val key: String,
    val status: String,
    val message: String
)
