package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableAllVirusScans extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "disable_all_virus_scans";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        this.hookGetManager(lpparam);
        this.hookDbManager(lpparam);
        this.disableVirusPopup(lpparam);
    }

    private void hookGetManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking safecenter to block manager initialization.");
            XposedHelpers.findAndHookMethod("tmsdk.fg.creator.ManagerCreatorF",
                    lpparam.classLoader,
                    "getManager",
                    "java.lang.Class",
                    XC_MethodReplacement.returnConstant(null));
            log("Successfully hooked safecenter!");
        } catch (Exception e) {
            logError("Failed to hook scan manager! ", e);
        }
    }

    private void hookDbManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Set getVirusAppsCount return value to int 0");
            XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.db.AntiVirusDBManager",
                    lpparam.classLoader,
                    "getVirusAppsCount",
                    XC_MethodReplacement.returnConstant(0));
            log("getVirusAppsCount is set to 0.");
            log("Blocking AntiVirusDBHelper initialization.");
            XposedHelpers.findAndHookConstructor("com.lenovo.safecenter.antivirus.db.AntiVirusDBHelper",
                    lpparam.classLoader,
                    "android.content.Context",
                    XC_MethodReplacement.returnConstant(null));
        } catch (Exception e) {
            logError("Failed to hook DB manager! ", e);
        }
    }

    private void disableVirusPopup(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.views.NotiSMSActivity",
                lpparam.classLoader,
                "onCreate",
                "android.os.Bundle",
                XC_MethodReplacement.returnConstant(null)
        );
        log("Virus popup blocked successfully.");
    }

}
