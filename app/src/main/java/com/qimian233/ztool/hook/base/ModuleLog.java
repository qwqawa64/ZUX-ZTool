package com.qimian233.ztool.hook.base;

import android.util.Log;

import com.qimian233.ztool.hook.HookInit;

import io.github.libxposed.api.XposedInterface;

/**
 * Hook 模块日志工具。
 * <p>
 * 从 {@link BaseHookModule} 拆分出来的日志关注点，负责：
 * <ul>
 *   <li>详细日志开关（{@link #DEBUG}）及其定时刷新</li>
 *   <li>{@link #log} — 通过 XposedInterface 输出 info 级别日志</li>
 *   <li>{@link #logError} — 通过 XposedInterface 输出 error 级别日志（含堆栈截断）</li>
 * </ul>
 * 所有方法均为静态方法，不依赖实例状态。
 * </p>
 */
public final class ModuleLog {

    private static final String TAG = "ZToolXposedModule";
    private static final String PREFS_NAME = "xposed_module_config";
    private static final long DEBUG_REFRESH_INTERVAL_MS = 1000L;

    /** 详细日志开关，由 {@link #refreshDebugLoggingEnabled()} 定期从远程配置刷新。 */
    public static volatile boolean DEBUG = false;
    private static volatile long lastDebugRefreshTime = 0L;

    private ModuleLog() {}

    /**
     * 从远程配置读取详细日志开关，并刷新 {@link #DEBUG}。
     * <p>调用频率受 {@code DEBUG_REFRESH_INTERVAL_MS} 限制。</p>
     */
    public static void refreshDebugLoggingEnabled() {
        long now = System.currentTimeMillis();
        if (now - lastDebugRefreshTime < DEBUG_REFRESH_INTERVAL_MS) return;
        synchronized (ModuleLog.class) {
            if (now - lastDebugRefreshTime >= DEBUG_REFRESH_INTERVAL_MS) {
                DEBUG = isDetailedLoggingEnabled();
                lastDebugRefreshTime = now;
            }
        }
    }

    private static boolean isDetailedLoggingEnabled() {
        try {
            XposedInterface xi = HookInit.getXposedInterface();
            if (xi != null) {
                return xi.getRemotePreferences(PREFS_NAME)
                        .getBoolean("isDetailedLogging", false);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 通过 XposedInterface 输出 info 级别日志。
     *
     * @param xposed     XposedInterface 实例（可为 null）
     * @param moduleName 模块名称，会以 {@code [moduleName]} 前缀拼入日志
     * @param message    日志正文
     */
    public static void log(XposedInterface xposed, String moduleName, String message) {
        if (xposed != null) {
            xposed.log(4, TAG, "[" + moduleName + "] " + message);
        }
    }

    /**
     * 通过 XposedInterface 输出 error 级别日志（含堆栈）。
     * <p>
     * 当 {@link #DEBUG} 为 {@code true} 时输出最多 10 行堆栈；
     * 否则仅输出堆栈首行。
     * </p>
     *
     * @param xposed     XposedInterface 实例（可为 null）
     * @param moduleName 模块名称
     * @param message    错误描述
     * @param t          {@link Throwable}
     */
    public static void logError(XposedInterface xposed, String moduleName,
                                String message, Throwable t) {
        refreshDebugLoggingEnabled();
        StringBuilder sb = new StringBuilder("[")
                .append(moduleName).append("] ").append(message).append("\n");
        String fullStackTrace = Log.getStackTraceString(t);
        String[] lines = fullStackTrace.split("\n");
        if (DEBUG) {
            int max = Math.min(lines.length, 10);
            for (int i = 0; i < max; i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines[i]);
            }
        } else if (lines.length > 0) {
            sb.append(lines[0]).append("\n");
        }
        if (xposed != null) {
            xposed.log(6, TAG, sb.toString());
        }
    }
}
