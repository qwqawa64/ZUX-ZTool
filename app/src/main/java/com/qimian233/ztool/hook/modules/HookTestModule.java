package com.qimian233.ztool.hook.modules;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 测试 Hook 模块 — 始终启用，用于验证 libxposed 框架回调是否正常工作。
 * <p>
 * 当 {@link #getModuleName()} 返回 {@code "hook_test"} 时，
 * {@link BaseHookModule#isEnabled()} 始终返回 {@code true}。
 * </p>
 */
public class HookTestModule extends BaseHookModule {

    public HookTestModule() {}

    @Override
    public String getModuleName() {
        return "hook_test";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        // 仅输出一条日志以确认模块被执行
        log("TEST HOOK EXECUTED — libxposed callbacks are working correctly, process="
                + android.os.Process.myPid());
    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        // 仅输出一条日志以确认模块被执行
        log("TEST HOOK EXECUTED — libxposed callbacks are working correctly, process="
                + android.os.Process.myPid());
    }
}
