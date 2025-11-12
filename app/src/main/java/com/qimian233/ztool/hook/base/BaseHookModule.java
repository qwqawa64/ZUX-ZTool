package com.qimian233.ztool.hook.base;

import android.util.Log;

import com.qimian233.ztool.config.ModuleConfig;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook模块基类
 * 所有具体的Hook模块都应该继承这个类
 */
public abstract class BaseHookModule {
    protected static final String TAG = "XposedHook";

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

    /**
     * 执行Hook操作
     */
    public abstract void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;

    /**
     * 安全执行Hook（捕获异常，防止一个模块崩溃影响其他模块）
     */
    public void safeHandleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!supportsPackage(lpparam.packageName) || !isEnabled()) {
            Log.d(TAG, "module disable: " + getModuleName());
            return;
        }

        try {
            Log.d(TAG, "Executing hook module: " + getModuleName() + " for package: " + lpparam.packageName);
            handleLoadPackage(lpparam);
            Log.d(TAG, "Hook module executed successfully: " + getModuleName());
        } catch (Throwable t) {
            Log.e(TAG, "Error in hook module: " + getModuleName(), t);
        }
    }

    protected void log(String message) {
        // 使用统一的标签，便于日志收集服务过滤
        android.util.Log.i("ZToolXposedModule", "[" + getModuleName() + "] " + message);
    }

    protected void logError(String message, Throwable t) {
        android.util.Log.e("ZToolXposedModule", "[" + getModuleName() + "] " + message, t);
    }
}
