package com.qimian233.ztool.hook.modules.systemFramework;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint({"SoonBlockedPrivateApi", "PrivateApi"})
public class AllowGetPackages extends BaseHookModule {
    public static final String FEATURE_NAME = "allow_get_packages";

    private static final int OP_GET_INSTALLED_APP = 214;

    public AllowGetPackages() {}

    public String getModuleName() {
        return FEATURE_NAME;
    }

    public String[] getTargetPackages() {
        return new String[] {"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        log("System server package, handleLoadPackage should not be loaded. Check getTargetPackages if you see this log.");
    }

    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        ClassLoader classLoader = param.getClassLoader();
        try {
            log("Start hooking android.app.AppOpsManager, SystemFramework");
            Method opToDefaultMode = classLoader.loadClass("android.app.AppOpsManager")
                    .getDeclaredMethod("opToDefaultMode", int.class);
            hookWithId(opToDefaultMode, "op_to_default_mode", chain -> {
                int op = (int) chain.getArg(0);
                if (op == OP_GET_INSTALLED_APP) {
                    return 0;
                }
                return chain.proceed();
            });
            log("Hooked android.app.AppOpsManager [OK]");
        }catch (Exception e){
            logError("Failed hooking android.app.AppOpsManager",e);
        }
        try {
            log("Start hooking com.android.server.appop.AppOpsService, SystemFramework");
            Method checkOperation = classLoader.loadClass("com.android.server.appop.AppOpsService")
                    .getDeclaredMethod("checkOperationRawZui", int.class, int.class, String.class);
            hookWithId(checkOperation, "check_operation_raw_zui", chain -> {
                int op = (int) chain.getArg(0);
                if (op == OP_GET_INSTALLED_APP) {
                    return 0;
                }
                return chain.proceed();
            });
            log("Hooked com.android.server.appop.AppOpsService [OK]");
        }catch (Exception e){
            logError("Failed hooking com.android.server.appop.AppOpsService",e);
        }
    }
}
