package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomQsColor extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[0];
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

    }
}
