package com.qimian233.ztool.hook.base;

import android.content.SharedPreferences;
import android.util.Log;

import com.qimian233.ztool.hook.HookInit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Hook 模块基类（libxposed 版）。
 * <p>
 * 所有 Hook 模块继承此类。通过 {@link #xposed} 字段访问 libxposed API：
 * {@code hook()}, {@code log()}, {@code getRemotePreferences()} 等。
 * </p>
 */
public abstract class BaseHookModule {

    protected static final String TAG = "ZToolXposedModule";
    private static final String PREFS_NAME = "xposed_module_config";
    private static final long DEBUG_REFRESH_INTERVAL_MS = 1000L;

    public static volatile boolean DEBUG = false;
    private static volatile long lastDebugRefreshTime = 0L;

    protected XposedInterface xposed;

    // ── abstract ──────────────────────────────────────────────

    public abstract String getModuleName();
    public abstract String[] getTargetPackages();

    /**
     * 执行 Hook 操作（<b>abstract</b>，便于 IDE 自动补全）。
     */
    public abstract void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable;

    // ── XposedInterface 注入 ───────────────────────────────────

    public void setXposedInterface(XposedInterface xposed) {
        this.xposed = xposed;
    }

    // ── package matching ───────────────────────────────────────

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

    // ── config ─────────────────────────────────────────────────

    public boolean isEnabled() {
        String moduleName = getModuleName();
        if ("hook_test".equals(moduleName) || "test_hook".equals(moduleName)) {
            return true;
        }
        if (moduleName == null) return false;
        try {
            SharedPreferences prefs = this.xposed.getRemotePreferences(PREFS_NAME);
            return prefs.getBoolean(moduleName, false);
        } catch (Throwable th) {
            return false;
        }
    }

    protected static boolean isDetailedLoggingEnabledStatic() {
        try {
            XposedInterface xi = HookInit.getXposedInterface();
            if (xi != null) {
                SharedPreferences prefs = xi.getRemotePreferences(PREFS_NAME);
                return prefs.getBoolean("isDetailedLogging", false);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    protected static void refreshDebugLoggingEnabled() {
        long now = System.currentTimeMillis();
        if (now - lastDebugRefreshTime < DEBUG_REFRESH_INTERVAL_MS) return;
        synchronized (BaseHookModule.class) {
            if (now - lastDebugRefreshTime >= DEBUG_REFRESH_INTERVAL_MS) {
                DEBUG = isDetailedLoggingEnabledStatic();
                lastDebugRefreshTime = now;
            }
        }
    }

    // ── system_server callback ─────────────────────────────────

    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param)
            throws Throwable {
        // default no-op
    }

    // ── safe dispatch wrappers ─────────────────────────────────

    public void safeHandleLoadPackage(XposedModuleInterface.PackageLoadedParam param) {
        refreshDebugLoggingEnabled();
        String packageName = param.getPackageName();
        if (!supportsPackage(packageName)) return;
        if (!isEnabled()) {
            if (DEBUG) Log.d(TAG, "module disabled: " + getModuleName());
            return;
        }
        try {
            if (DEBUG) Log.d(TAG, "Executing hook module: " + getModuleName()
                    + " for package: " + packageName);
            handleLoadPackage(param);
            if (DEBUG) Log.d(TAG, "Hook module executed successfully: " + getModuleName());
        } catch (Throwable t) {
            Log.e(TAG, "Error in hook module: " + getModuleName(), t);
        }
    }

    public void safeHandleSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        refreshDebugLoggingEnabled();
        if (!isEnabled()) {
            if (DEBUG) Log.d(TAG, "module disabled for system server: " + getModuleName());
            return;
        }
        try {
            if (DEBUG) Log.d(TAG, "Executing system server hook module: " + getModuleName());
            handleSystemServerStarting(param);
            if (DEBUG) Log.d(TAG, "System server hook module executed successfully: "
                    + getModuleName());
        } catch (Throwable t) {
            Log.e(TAG, "Error in system server hook module: " + getModuleName(), t);
        }
    }

    // ── logging ────────────────────────────────────────────────

    protected void log(String message) {
        if (this.xposed != null) {
            this.xposed.log(4, TAG, "[" + getModuleName() + "] " + message);
        }
    }

    protected void logError(String message, Throwable t) {
        refreshDebugLoggingEnabled();
        StringBuilder sb = new StringBuilder("[")
                .append(getModuleName()).append("] ").append(message).append("\n");
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
        if (this.xposed != null) {
            this.xposed.log(6, TAG, sb.toString());
        }
    }

    // ── helpers ────────────────────────────────────────────────

    /*
     * XposedHelpers-style field finder. It looks up fields recursively in current class and its parent classes.
     *
     * Always ensure you have filters to avoid unexpected field hits.
     */
    public static Field findField(Class<?> startClass, String name) throws NoSuchFieldException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + startClass);
    }

    /*
     * XposedHelpers-style method finder. It looks up methods recursively in current class and its parent classes.
     *
     * Always ensure you have filters to avoid unexpected method hits.
     */
    public static Method findMethod(Class<?> startClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + " in " + startClass);
    }
}
