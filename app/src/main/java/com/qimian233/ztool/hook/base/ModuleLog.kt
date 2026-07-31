package com.qimian233.ztool.hook.base

import android.util.Log
import com.qimian233.ztool.hook.HookInit
import io.github.libxposed.api.XposedInterface

/**
 * Hook 模块日志器（Log4j 风格，六级别）。
 *
 * 每个 [BaseHookModule] 子类通过基类的 [logger][BaseHookModule.logger] 字段使用，
 * 采用 Log4j 风格的六级 API：
 * - `trace` → Android VERBOSE (priority 2)，始终输出
 * - `debug` → Android DEBUG (priority 3)，受 [DEBUG] 开关控制
 * - `info`  → Android INFO (priority 4)，始终输出
 * - `warn`  → Android WARN (priority 5)，始终输出
 * - `error` → Android ERROR (priority 6)，始终输出，可选 [Throwable]
 * - `fatal` → Android ASSERT (priority 7)，始终输出，可选 [Throwable]
 *
 * [error] 和 [fatal] 携带 [Throwable] 时，行为与旧版 `logError` 一致：
 * [DEBUG] 开启时输出最多 10 行堆栈，关闭时仅输出首行。
 *
 * Companion 中保留全局 [DEBUG] 开关、[refreshDebugLoggingEnabled] 和
 * 向后兼容的静态方法 [logStatic] / [logErrorStatic]。
 */
class ModuleLog
@JvmOverloads
constructor(
    private val moduleName: String,
    @Volatile var xposed: XposedInterface? = null
) {

    // ── 实例日志方法 ──────────────────────────────────────────

    /** VERBOSE — 始终输出，用于最低优先级的诊断信息。 */
    fun trace(msg: String) {
        xposed?.log(2, TAG, "[$moduleName] $msg")
    }

    /** DEBUG — 受 [DEBUG] 开关控制，用于详细调试信息。 */
    fun debug(msg: String) {
        if (DEBUG) {
            xposed?.log(3, TAG, "[$moduleName] $msg")
        }
    }

    /** INFO — 始终输出，用于常规操作日志。 */
    fun info(msg: String) {
        xposed?.log(4, TAG, "[$moduleName] $msg")
    }

    /** WARN — 始终输出，用于警告信息。 */
    fun warn(msg: String) {
        xposed?.log(5, TAG, "[$moduleName] $msg")
    }

    /**
     * ERROR — 始终输出，用于错误信息。
     *
     * @param msg 错误描述
     * @param t   可选 [Throwable]；提供时附加堆栈（受 [DEBUG] 控制截断长度）
     */
    @JvmOverloads
    fun error(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        xposed?.log(6, TAG, body)
    }

    /**
     * FATAL — 始终输出，用于致命错误。
     *
     * @param msg 错误描述
     * @param t   可选 [Throwable]
     */
    @JvmOverloads
    fun fatal(msg: String, t: Throwable? = null) {
        val body = if (t != null) formatWithStack(msg, t) else "[$moduleName] $msg"
        xposed?.log(7, TAG, body)
    }

    /** 当前 debug 日志是否开启（简便查询 [DEBUG]）。 */
    fun isDebugEnabled(): Boolean = DEBUG

    // ── 内部工具 ──────────────────────────────────────────────

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

    // ── companion：全局状态 + 向后兼容静态方法 ──────────────────

    companion object {
        private const val TAG = "ZToolXposedModule"
        private const val PREFS_NAME = "xposed_module_config"
        private const val DEBUG_REFRESH_INTERVAL_MS = 1000L

        /** 详细日志开关。 */
        @JvmField
        @Volatile
        var DEBUG: Boolean = false
        @Volatile
        private var lastDebugRefreshTime: Long = 0L

        /**
         * 从远程配置刷新 [DEBUG] 开关。
         * 调用频率受 `DEBUG_REFRESH_INTERVAL_MS` 限制。
         */
        @JvmStatic
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

        // ── 向后兼容静态方法（供 BaseHookModule 废弃的 log/logError 委托使用）──

        /** @suppress 向后兼容，新代码请使用实例 [ModuleLog.info]。 */
        @JvmStatic
        fun logStatic(xposed: XposedInterface?, moduleName: String, msg: String) {
            xposed?.log(4, TAG, "[$moduleName] $msg")
        }

        /** @suppress 向后兼容，新代码请使用实例 [ModuleLog.error]。 */
        @JvmStatic
        fun logErrorStatic(
            xposed: XposedInterface?,
            moduleName: String,
            msg: String,
            t: Throwable?
        ) {
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
            xposed?.log(6, TAG, sb.toString())
        }
    }
}
