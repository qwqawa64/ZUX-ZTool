package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableVirusPopup extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "disable_virus_popup";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.views.NotiSMSActivity",
                lpparam.classLoader,
                "onCreate",
                "android.os.Bundle",
                XC_MethodReplacement.returnConstant(null)
        );
        log("Virus popup blocked successfully.");
    }
}
