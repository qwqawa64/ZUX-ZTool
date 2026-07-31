package com.qimian233.ztool.hook.base;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 系统框架 Hook 模块基类。
 * <p>
 * 继承此类后 IDE 会提示实现 {@link #handleSystemServerStarting}，无需关心
 * {@link #handleLoadPackage}。
 * </p>
 */
public abstract class SystemHookModule extends BaseHookModule {

    /**
     * 执行系统服务器启动时的 Hook 操作（<b>abstract</b>，便于 IDE 自动补全）。
     */
    @Override
    public abstract void handleSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) throws Throwable;
}
