package com.qimian233.ztool.hook.modules.launcher;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import com.qimian233.ztool.hook.base.BaseHookModule;

public class DisableForceStop extends BaseHookModule {
    public static final String FEATURE_NAME = "disable_force_stop";
    public String[] TARGET_PACKAGES = {"com.zui.launcher"};

    public String getModuleName() {return FEATURE_NAME;}
    public String[] getTargetPackages() {return TARGET_PACKAGES;}
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("DisableForceStop started, finding classes...");
        var amwclass = XposedHelpers.findClass("com.android.systemui.shared.system.ActivityManagerWrapper", lpparam.classLoader);
        try {
            log("Start hooking ActivityManagerWrapper...");
            XposedHelpers.findAndHookMethod(amwclass, "removeAllRunningAppProcesses", android.content.Context.class, java.util.ArrayList.class, XC_MethodReplacement.returnConstant(null));
            XposedHelpers.findAndHookMethod(amwclass, "removeAppProcess", android.content.Context.class, int.class, String.class, int.class, XC_MethodReplacement.returnConstant(null));
            log("ActicityManagerWrapper hooked [OK]");
        }catch (Exception e){
            logError("Error hooking ActivityManagerWrapper", e);
        }

    }
}
