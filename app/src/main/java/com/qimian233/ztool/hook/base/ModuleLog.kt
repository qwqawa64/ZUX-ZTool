package com.qimian233.ztool.hook.base

import android.util.Log
import com.qimian233.ztool.hook.HookInit
import io.github.libxposed.api.XposedInterface

/**
 * Hook module logger (Log4j style, six levels).
 *
 * Each [BaseHookModule] subclass uses this via the base class's
 * [logger][BaseHookModule.logger] field, with a Log4j-style six-level API:
 * - `trace` → Android VERBOSE (priority 2), always emitted
 * - `debug` → Android DEBUG (priority 3), gated by the [DEBUG] switch
 * - `info`  → Android INFO (priority 4), always emitted
 * - `warn`  → Android WARN (priority 5), always emitted
 * - `error` → Android ERROR (priority 6), always emitted, optional [Throwable]
 * - `fatal` → Android ASSERT (priority 7), always emitted, optional [Throwable]
 *
 * When [error] and [fatal] carry a [Throwable], behavior matches the legacy
 * `logError`: up to 10 stack lines when [DEBUG] is on, first line only when off.
 *
 * The global [DEBUG] switch and [refreshDebugLoggingEnabled] are kept in the companion.
 */
class ModuleLog(
    private val moduleName: String,
    @Volatile var xposed: XposedInterface? = null
) {

    // ── Instance log methods ──────────────────────────────────

    /** VERBOSE — always emitted, for lowest-priority diagnostics. */
    fun trace(msg: String) {
        xposed?.log(2, TAG, "[$moduleName] $msg")
    }

    /** DEBUG — gated by the [DEBUG] switch, for verbose debugging info. */
    fun debug(msg: String) {
        if (DEBUG) {
            xposed?.log(3, TAG, "[$moduleName] $msg")
        }
    }

    /** INFO — always emitted, for routine operational logs. */
    fun info(msg: String) {
        xposed?.log(4, TAG, "[$moduleName] $msg")
    }

    /** WARN — always emitted, for warnings. */
    fun warn(msg: String) {
        xposed?.log(5, TAG, "[$moduleName] $msg")
    }

    /**
     * ERROR — always emitted, for errors.
     *
     * @param msg error description
     * @param t   optional [Throwable]; when provided, appends the stack (length truncated per [DEBUG])
     */
    fun error(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        xposed?.log(6, TAG, body)
    }

    /**
     * FATAL — always emitted, for fatal errors.
     *
     * @param msg error description
     * @param t   optional [Throwable]
     */
    fun fatal(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        xposed?.log(7, TAG, body)
    }

    /** Whether debug logging is currently on (convenience query of [DEBUG]). */
    fun isDebugEnabled(): Boolean = DEBUG

    // ── Internal helpers ──────────────────────────────────────

    private fun formatWithStack(msg: String, t: Throwable): String {
        refreshDebugLoggingEnabled()
        val sb = StringBuilder("[$moduleName] $msg\n")
        val lines = Log.getStackTraceString(t).split("\n")
        if (DEBUG) {
            val max = minOf(lines.size, 10)
            for (i in 0 until max) {
                if (i > 0) sb.append("\n")
                sb.append(lines[i])
            }
        } else if (lines.isNotEmpty()) {
            sb.append(lines[0]).append("\n")
        }
        return sb.toString()
    }

    // ── companion: global state ───────────────────────────────

    companion object {
        private const val TAG = "ZToolXposedModule"
        private const val PREFS_NAME = "xposed_module_config"
        private const val DEBUG_REFRESH_INTERVAL_MS = 1000L

        /** Detailed logging switch. */
        @Volatile
        var DEBUG: Boolean = false
        @Volatile
        private var lastDebugRefreshTime: Long = 0L

        /**
         * Refreshes the [DEBUG] switch from remote preferences.
         * Call frequency is limited by `DEBUG_REFRESH_INTERVAL_MS`.
         */
        fun refreshDebugLoggingEnabled() {
            val now = System.currentTimeMillis()
            if (now - lastDebugRefreshTime < DEBUG_REFRESH_INTERVAL_MS) return
            synchronized(this) {
                if (now - lastDebugRefreshTime >= DEBUG_REFRESH_INTERVAL_MS) {
                    DEBUG = isDetailedLoggingEnabled()
                    lastDebugRefreshTime = now
                }
            }
        }

        private fun isDetailedLoggingEnabled(): Boolean {
            return try {
                val xi = HookInit.getXposedInterface()
                xi?.getRemotePreferences(PREFS_NAME)
                    ?.getBoolean("isDetailedLogging", false) ?: false
            } catch (_: Throwable) {
                false
            }
        }
    }
}
