package com.qimian233.ztool.hook.base;

import android.util.Log;

import com.qimian233.ztool.config.ModuleConfig;

import java.util.Arrays;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook模块基类
 * 所有具体的Hook模块都应该继承这个类
 */
public abstract class BaseHookModule {
    protected static final String TAG = "XposedHook";
    private static final long DEBUG_REFRESH_INTERVAL_MS = 1000L;

    public static volatile boolean DEBUG = false;
    private static volatile long lastDebugRefreshTime = 0L;

    /**
     * 获取模块名称（用于日志和配置）
     */
    public abstract String getModuleName();

    /**
     * 获取目标包名（支持多个包名）
     */
    public abstract String[] getTargetPackages();

    /**
     * 检查是否支持当前包
     */
    public boolean supportsPackage(String packageName) {
        String[] targets = getTargetPackages();
        if (targets == null) return false;

        for (String target : targets) {
            if (target.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否启用该模块
     */
    public boolean isEnabled() {
        return ModuleConfig.isModuleEnabled(getModuleName());
    }

    protected static boolean refreshDebugLoggingEnabled() {
        long now = System.currentTimeMillis();
        if (now - lastDebugRefreshTime < DEBUG_REFRESH_INTERVAL_MS) {
            return DEBUG;
        }

        synchronized (BaseHookModule.class) {
            if (now - lastDebugRefreshTime >= DEBUG_REFRESH_INTERVAL_MS) {
                DEBUG = ModuleConfig.isDetailedLoggingEnabled();
                lastDebugRefreshTime = now;
            }
            return DEBUG;
        }
    }

    /**
     * 执行Hook操作
     */
    public abstract void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;

    /**
     * 安全执行Hook（捕获异常，防止一个模块崩溃影响其他模块）
     */
    public void safeHandleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        refreshDebugLoggingEnabled();
        if (!supportsPackage(lpparam.packageName) || Arrays.asList(getTargetPackages()).contains(lpparam.packageName))
            return; // If not supported, return directly.
        if (!isEnabled()) {
            if (DEBUG)
                Log.d(TAG, "module disabled: " + getModuleName()); // If module is disabled, log it and return.
            return;
        }

        try {
            if (DEBUG)
                Log.d(TAG, "Executing hook module: " + getModuleName() + " for package: " + lpparam.packageName);
            handleLoadPackage(lpparam);
            if (DEBUG) Log.d(TAG, "Hook module executed successfully: " + getModuleName());
        } catch (Throwable t) {
            Log.e(TAG, "Error in hook module: " + getModuleName(), t);
        }
    }

    protected void log(String message) {
        // 使用统一的标签，便于日志收集服务过滤
        android.util.Log.i("ZToolXposedModule", "[" + getModuleName() + "] " + message);
    }

    protected void logError(String message, Throwable t) {
        refreshDebugLoggingEnabled();
        String finalMessage = "[" + getModuleName() + "] " + message + "\n";
        String fullStackTrace = android.util.Log.getStackTraceString(t);
        StringBuilder truncatedStack = new StringBuilder();
        String[] lines = fullStackTrace.split("\n");
        if (DEBUG) {
            // 调试模式只取前 10 行
            final int MAX_STACK_LINES = 10;
            int linesToTake = Math.min(lines.length, MAX_STACK_LINES);
            for (int i = 0; i < linesToTake; i++) {
                truncatedStack.append(lines[i]);
                if (i < linesToTake - 1) {
                    truncatedStack.append("\n");
                }
            }
            finalMessage += truncatedStack.toString();
        } else {
            // 非调试模式只看错误消息
            if (lines.length > 0) {
                truncatedStack.append(lines[0]);
                truncatedStack.append("\n");
            }
            finalMessage += truncatedStack;
        }
        android.util.Log.e("ZToolXposedModule", finalMessage);
    }
}
