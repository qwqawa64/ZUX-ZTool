package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AllowUntrustedTouch extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "allow_untrusted_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"android"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod("com.android.server.wm.WindowState",
                lpparam.classLoader,
                "getTouchOcclusionMode",
                XC_MethodReplacement.returnConstant(2));
    }
}
