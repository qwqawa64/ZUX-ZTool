package com.qimian233.ztool.hook.base;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 普通 App Hook 模块基类。
 * <p>
 * 继承此类后 IDE 会提示实现 {@link #handleLoadPackage}，无需关心
 * {@link #handleSystemServerStarting}。
 * </p>
 */
public abstract class AppHookModule extends BaseHookModule {

    /**
     * 执行 App 包加载时的 Hook 操作（<b>abstract</b>，便于 IDE 自动补全）。
     */
    @Override
    public abstract void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable;
}
