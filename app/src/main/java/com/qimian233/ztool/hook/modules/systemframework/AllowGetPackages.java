package com.qimian233.ztool.hook.modules.systemframework;

import android.annotation.SuppressLint;

import com.qimian233.ztool.data.ScopeKeys;
import com.qimian233.ztool.hook.base.SystemHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint({"SoonBlockedPrivateApi", "PrivateApi"})
public class AllowGetPackages extends SystemHookModule {
    public static final String FEATURE_NAME = "allow_get_packages";

    private static final int OP_GET_INSTALLED_APP = 214;

    public AllowGetPackages() {}

    public String getModuleName() {
        return FEATURE_NAME;
    }

    public String[] getTargetPackages() {
        return new String[] {ScopeKeys.SYSTEM_SERVER.packageName};
    }

    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        ClassLoader classLoader = param.getClassLoader();
        try {
            logger.info("Start hooking android.app.AppOpsManager, SystemFramework");
            Method opToDefaultMode = classLoader.loadClass("android.app.AppOpsManager")
                    .getDeclaredMethod("opToDefaultMode", int.class);
            hookWithId(opToDefaultMode, "op_to_default_mode", chain -> {
                int op = (int) chain.getArg(0);
                if (op == OP_GET_INSTALLED_APP) {
                    return 0;
                }
                return chain.proceed();
            });
            logger.info("Hooked android.app.AppOpsManager [OK]");
        }catch (Exception e){
            logger.error("Failed hooking android.app.AppOpsManager",e);
        }
        try {
            logger.info("Start hooking com.android.server.appop.AppOpsService, SystemFramework");
            Method checkOperation = classLoader.loadClass("com.android.server.appop.AppOpsService")
                    .getDeclaredMethod("checkOperationRawZui", int.class, int.class, String.class);
            hookWithId(checkOperation, "check_operation_raw_zui", chain -> {
                int op = (int) chain.getArg(0);
                if (op == OP_GET_INSTALLED_APP) {
                    return 0;
                }
                return chain.proceed();
            });
            logger.info("Hooked com.android.server.appop.AppOpsService [OK]");
        }catch (Exception e){
            logger.error("Failed hooking com.android.server.appop.AppOpsService",e);
        }
    }
}
