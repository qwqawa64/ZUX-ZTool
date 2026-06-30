package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

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
            this.xposed.hook(opToDefaultMode).intercept(chain -> {
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
            this.xposed.hook(checkOperation).intercept(chain -> {
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
