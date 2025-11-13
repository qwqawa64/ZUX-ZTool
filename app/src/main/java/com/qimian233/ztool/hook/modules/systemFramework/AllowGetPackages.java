package com.qimian233.ztool.hook.modules.systemFramework;

import android.provider.Settings;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import com.qimian233.ztool.hook.base.BaseHookModule;

public class AllowGetPackages extends BaseHookModule {
    public static final String FEATURE_NAME = "allow_get_packages";

    private static final int OP_GET_INSTALLED_APP = 214;

    public String getModuleName() {
        return FEATURE_NAME;
    }

    public String[] getTargetPackages() {
        return new String[] {"android"};
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.processName)) {
            try {
                log("Start hooking android.app.AppOpsManager, SystemFramework");
                XposedHelpers.findAndHookMethod("android.app.AppOpsManager", lpparam.classLoader, "opToDefaultMode", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        int op = (int) param.args[0];
                        if (op == OP_GET_INSTALLED_APP) {
                            param.setResult(0);
                        }
                    }
                });
                log("Hooked android.app.AppOpsManager [OK]");
            }catch (Exception e){
                logError("Failed hooking android.app.AppOpsManager",e);
            }
            try {
                log("Start hooking com.android.server.appop.AppOpsService, SystemFramework");
                XposedHelpers.findAndHookMethod("com.android.server.appop.AppOpsService", lpparam.classLoader, "checkOperationRawZui", int.class, int.class, "java.lang.String", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        int op = (int) param.args[0];
                        if (op == OP_GET_INSTALLED_APP) {
                            param.setResult(0);
                        }
                    }
                });
                log("Hooked com.android.server.appop.AppOpsService [OK]");
            }catch (Exception e){
                logError("Failed hooking com.android.server.appop.AppOpsService",e);
            }
        }
    }
}
