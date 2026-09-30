package com.qimian233.ztool.hook.base

import android.util.Log
import com.qimian233.ztool.data.keys.LogLevel
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.HookInit
import io.github.libxposed.api.XposedInterface

/**
 * Hook module logger (Log4j style, six levels).
 *
 * Each [BaseHookModule] subclass uses this via the base class's
 * [logger][BaseHookModule.logger] field, with a Log4j-style six-level API:
 * - `trace` → Android VERBOSE (priority 2)
 * - `debug` → Android DEBUG (priority 3)
 * - `info`  → Android INFO (priority 4)
 * - `warn`  → Android WARN (priority 5)
 * - `error` → Android ERROR (priority 6), optional [Throwable]
 * - `fatal` → Android ASSERT (priority 7), optional [Throwable]
 *
 * Emission is threshold-gated: a call is logged only when its level's priority
 * is greater than or equal to the configured [LogLevel] (read from remote
 * preferences). The level is refreshed on every log call, throttled by
 * [LEVEL_REFRESH_INTERVAL_MS], so a level change takes effect across all live
 * processes within about one second — no scope restart needed.
 *
 * When [error] and [fatal] carry a [Throwable], behavior matches the legacy
 * `logError`: up to 10 stack lines when the level is DEBUG or finer, first
 * line only otherwise.
 *
 * The global [LEVEL] switch and [refreshLogLevel] are kept in the companion.
 */
class ModuleLog(
    private val moduleName: String,
    @Volatile var xposed: XposedInterface? = null
) {

    /** VERBOSE — lowest-priority diagnostics. */
    fun trace(msg: String) = log(LogLevel.TRACE, msg)

    /** DEBUG — verbose debugging info. */
    fun debug(msg: String) = log(LogLevel.DEBUG, msg)

    /** INFO — routine operational logs. */
    fun info(msg: String) = log(LogLevel.INFO, msg)

    /** WARN — warnings. */
    fun warn(msg: String) = log(LogLevel.WARN, msg)

    /**
     * ERROR — errors.
     *
     * @param msg error description
     * @param t   optional [Throwable]; when provided, appends the stack (length truncated per [LogLevel.DEBUG])
     */
    fun error(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        log(LogLevel.ERROR, body)
    }

    /**
     * FATAL — fatal errors.
     *
     * @param msg error description
     * @param t   optional [Throwable]
     */
    fun fatal(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        log(LogLevel.FATAL, body)
    }

    private fun log(level: LogLevel, msg: String) {
        refreshLogLevel()
        if (LEVEL.priority > level.priority) return
        xposed?.log(level.priority, TAG, "[$moduleName] $msg")
    }

    private fun formatWithStack(msg: String, t: Throwable): String {
        val sb = StringBuilder("[$moduleName] $msg\n")
        val lines = Log.getStackTraceString(t).split("\n")
        if (shouldLog(LogLevel.DEBUG)) {
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

    companion object {
        private const val TAG = "ZToolXposedModule"
        private const val PREFS_NAME = "xposed_module_config"
        private const val LEVEL_REFRESH_INTERVAL_MS = 1000L

        /** Currently configured threshold level. */
        @Volatile
        var LEVEL: LogLevel = LogLevel.DEFAULT
            private set
        @Volatile
        private var lastLevelRefreshTime: Long = 0L

        /**
         * Refreshes the [LEVEL] threshold from remote preferences.
         * Call frequency is limited by [LEVEL_REFRESH_INTERVAL_MS].
         */
        fun refreshLogLevel() {
            val now = System.currentTimeMillis()
            if (now - lastLevelRefreshTime < LEVEL_REFRESH_INTERVAL_MS) return
            synchronized(this) {
                if (now - lastLevelRefreshTime >= LEVEL_REFRESH_INTERVAL_MS) {
                    lastLevelRefreshTime = now
                    readConfiguredLevel()?.let { LEVEL = it }
                }
            }
        }

        /** Whether a call at [level] passes the currently configured threshold. */
        fun shouldLog(level: LogLevel): Boolean {
            refreshLogLevel()
            return LEVEL.priority <= level.priority
        }

        private fun readConfiguredLevel(): LogLevel? {
            return try {
                val xi = HookInit.getXposedInterface()
                xi?.getRemotePreferences(PREFS_NAME)
                    ?.getInt(PreferenceKeys.LOG_LEVEL.name, LogLevel.DEFAULT.priority)
                    ?.let(LogLevel::fromPriority)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
