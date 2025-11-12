package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class KeepRotation extends BaseHookModule {
    public static final String FEATURE_NAME = "keep_rotation";
    public static final String TARGET_PACKAGE = "android";
    public String getModuleName() { return FEATURE_NAME; }
    public String[] getTargetPackages() { return new String[]{TARGET_PACKAGE};}

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("Loading module keep_rotation.");
        if ("android".equals(lpparam.processName)) {
            log("Hooking DisplayRotation.isRotationCts");
            try{
                XposedHelpers.findAndHookMethod("com.zui.server.wm.ZuiDisplayRotation", lpparam.classLoader, "isRotationCts", XC_MethodReplacement.returnConstant(Boolean.TRUE));
                log("Hooked DisplayRotation.isRotationCts [OK]");
            } catch (Exception e) {
                logError("Error hooking DisplayRotation.isRotationCts", e);
            }
            //
        }
    }
}
