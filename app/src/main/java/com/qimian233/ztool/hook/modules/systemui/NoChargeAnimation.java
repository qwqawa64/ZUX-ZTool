package com.qimian233.ztool.hook.modules.systemui;

import android.os.Message;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class NoChargeAnimation extends BaseHookModule {
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    public NoChargeAnimation() {}

    @Override
    public String getModuleName() {
        return "No_ChargeAnimation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!isEnabled()) return;
        log("Loading module No_ChargeAnimation.");
        handleLoadSystemUi(classLoader);
    }

    public void handleLoadSystemUi(ClassLoader classLoader) {
        try {
            log("Hooking ChargingAnimationController...");
            Class<?> chargingAnimationControllerClass = classLoader.loadClass(
                    "com.android.keyguard.lockscreen.charge.ChargingAnimationController");
            java.lang.reflect.Field handlerField = chargingAnimationControllerClass.getDeclaredField("H");
            handlerField.setAccessible(true);
            Class<?> handlerType = handlerField.getType();
            Method handleMessageMethod = handlerType.getDeclaredMethod("handleMessage", Message.class);
            this.xposed.hook(handleMessageMethod).intercept(chain -> null);
            log("Hooked ChargingAnimationController [OK]");
        } catch (Exception e) {
            logError("Error hooking ChargingAnimationController", e);
        }
    }
}
