package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class KeepRotation extends BaseHookModule {
    public static final String FEATURE_NAME = "keep_rotation";
    public static final String TARGET_PACKAGE = "system";

    public KeepRotation() {}

    public String getModuleName() { return FEATURE_NAME; }
    public String[] getTargetPackages() { return new String[]{TARGET_PACKAGE};}

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {

    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        log("Hooking DisplayRotation.isRotationCts");
        try{
            Method method = classLoader.loadClass("com.zui.server.wm.ZuiDisplayRotation")
                    .getDeclaredMethod("isRotationCts");
            hookWithId(method, "is_rotation_cts", chain -> Boolean.TRUE);
            log("Hooked DisplayRotation.isRotationCts [OK]");
        } catch (Exception e) {
            logError("Error hooking DisplayRotation.isRotationCts", e);
        }
    }
}
