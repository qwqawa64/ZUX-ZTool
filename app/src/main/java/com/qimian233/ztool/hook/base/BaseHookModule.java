package com.qimian233.ztool.hook.base;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Hook 模块基类（libxposed 版）。
 * <p>
 * 所有 Hook 模块继承此类。通过 {@link #xposed} 字段访问 libxposed API：
 * {@code hook()}, {@code log()}, {@code getRemotePreferences()} 等。
 * <p>
 * 日志关注点已拆分至 {@link ModuleLog}，反射辅助已拆分至 {@link HookReflectionHelper}。
 * 本类保留向后兼容的委托方法。
 * </p>
 */
public abstract class BaseHookModule {

    protected static final String TAG = "ZToolXposedModule";
    protected static final String PREFS_NAME = "xposed_module_config";

    /**
     * 详细日志开关（向后兼容字段，实际状态由 {@link ModuleLog#DEBUG} 管理）。
     * @see ModuleLog#refreshDebugLoggingEnabled()
     */
    public static volatile boolean DEBUG = false;

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

    /**
     * 刷新详细日志开关。
     * <p>委托给 {@link ModuleLog#refreshDebugLoggingEnabled()}，并将结果同步到本类 {@link #DEBUG} 字段。</p>
     */
    protected static void refreshDebugLoggingEnabled() {
        ModuleLog.refreshDebugLoggingEnabled();
        DEBUG = ModuleLog.DEBUG;
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

    // ── logging (delegates to ModuleLog) ───────────────────────

    protected void log(String message) {
        ModuleLog.log(this.xposed, getModuleName(), message);
    }

    protected void logError(String message, Throwable t) {
        ModuleLog.logError(this.xposed, getModuleName(), message, t);
    }

    // ── helpers (delegates to HookReflectionHelper) ─────────────

    /**
     * Hook with a stable id for hot-reload atomic replacement.
     * Equivalent to {@code xposed.hook(target).setId(id).intercept(hooker)}.
     * <p>
     * During hot reload, a new hook registered with the same id on the same executable
     * will atomically replace the old hook in the framework, eliminating the hook vacuum
     * window.
     * </p>
     *
     * @param target the method or constructor to hook
     * @param id     a stable, module-unique identifier for the hook
     * @param hooker the interception callback
     * @return the hook handle
     */
    protected HookHandle hookWithId(Executable target, String id, Hooker hooker) {
        return this.xposed.getApiVersion() >= 102
                ? this.xposed.hook(target).setId(id).intercept(hooker)
                : this.xposed.hook(target).intercept(hooker);
    }

    /*
     * XposedHelpers-style field finder. Delegates to {@link HookReflectionHelper#findField}.
     *
     * Always ensure you have filters to avoid unexpected field hits.
     */
    public static java.lang.reflect.Field findField(Class<?> startClass, String name)
            throws NoSuchFieldException {
        return HookReflectionHelper.findField(startClass, name);
    }

    /*
     * XposedHelpers-style method finder. Delegates to {@link HookReflectionHelper#findMethod}.
     *
     * Always ensure you have filters to avoid unexpected method hits.
     */
    public static java.lang.reflect.Method findMethod(Class<?> startClass, String name,
                                                      Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return HookReflectionHelper.findMethod(startClass, name, parameterTypes);
    }
}
