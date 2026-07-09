package com.qimian233.ztool.hook.modules.mobiledesktop;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 测试 Hook — 禁用超级互联附近分享的 10 分钟自动关闭倒计时。
 * <p>
 * 机制：{@code ra.c.q()} (FileUnionSwitchManager.startCountDown) 
 * 在附近分享开启后发送延迟消息 (what=1, delay=600000ms=10min)，
 * handler {@code d8.a.m()} 收到消息后调用 
 * {@code c0.setNearbyShareStatus(false)} 自动关闭。
 * 此 Hook 将 startCountDown 替换为空操作，阻止倒计时启动。
 * </p>
 */
public class DisableNearbyShareAutoOffHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    // ra.c = FileUnionSwitchManager
    private static final String START_COUNTDOWN_CLASS = "ra.c";

    public DisableNearbyShareAutoOffHook() {}

    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();

        try {
            Class<?> fileUnionSwitchManagerClass = classLoader.loadClass(START_COUNTDOWN_CLASS);

            // Hook q() — startCountDown: ()V
            for (Method method : fileUnionSwitchManagerClass.getDeclaredMethods()) {
                if (method.getName().equals("q")
                        && method.getParameterTypes().length == 0
                        && method.getReturnType() == void.class) {
                    this.xposed.hook(method).intercept(chain -> {
                        log("startCountDown() intercepted — auto-off timer prevented.");
                        return null;
                    });
                    log("Installed hook for FileUnionSwitchManager.startCountDown()");
                    return;
                }
            }
            logError("Could not find startCountDown() method in ra.c", null);
        } catch (ClassNotFoundException e) {
            logError("ra.c (FileUnionSwitchManager) not found", e);
        } catch (Throwable t) {
            logError("Failed to hook FileUnionSwitchManager.startCountDown()", t);
        }
    }
}
