package com.qimian233.ztool.hook.modules.systemframework;

import com.qimian233.ztool.hook.base.SystemHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class KeepRotation extends SystemHookModule {
    public static final String FEATURE_NAME = "keep_rotation";
    public static final String TARGET_PACKAGE = "system";

    public KeepRotation() {}

    public String getModuleName() { return FEATURE_NAME; }
    public String[] getTargetPackages() { return new String[]{TARGET_PACKAGE};}

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        logger.info("Hooking DisplayRotation.isRotationCts");
        try{
            Method method = classLoader.loadClass("com.zui.server.wm.ZuiDisplayRotation")
                    .getDeclaredMethod("isRotationCts");
            hookWithId(method, "is_rotation_cts", chain -> Boolean.TRUE);
            logger.info("Hooked DisplayRotation.isRotationCts [OK]");
        } catch (Exception e) {
            logger.error("Error hooking DisplayRotation.isRotationCts", e);
        }
    }
}
