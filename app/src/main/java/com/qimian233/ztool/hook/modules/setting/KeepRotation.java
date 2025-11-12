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
        log("[keep_rotation] Module initialized.");
        if ("android".equals(lpparam.processName)) {
            log("[keep_rotation] Hooking DisplayRotation.isRotationCts");
            try{
                XposedHelpers.findAndHookMethod("com.zui.server.wm.ZuiDisplayRotation", lpparam.classLoader, "isRotationCts", XC_MethodReplacement.returnConstant(Boolean.TRUE));
                log("[keep_rotation] Hooked DisplayRotation.isRotationCts [OK]");
            } catch (Exception e) {
                logError("[keep_rotation] Error hooking DisplayRotation.isRotationCts", e);
            }
            //
        }
    }
}
