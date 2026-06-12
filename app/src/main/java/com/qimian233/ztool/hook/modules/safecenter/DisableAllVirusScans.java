package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableAllVirusScans extends BaseHookModule {
    @Override
    public String getModuleName () {return "disable_all_virus_scans";}
    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            log("Hooking safecenter to disable virus scanning.");
            this.hookGetManager(lpparam);
            log("Successfully hooked safecenter!");
        } catch (Exception e) {
            logError("Failed to hook safecenter: ", e);
        }
    }

    private void hookGetManager(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("tmsdk.fg.creator.ManagerCreatorF",
                lpparam.classLoader,
                "getManager",
                "java.lang.Class",
                XC_MethodReplacement.returnConstant(null));
    }
}
