package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomizeAboutDeviceInfo extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.settings"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // CPU Info
        XposedHelpers.findAndHookMethod("com.lenovo.settings.deviceinfo.controller.CpuInfoDisplayPreferenceController",
                lpparam.classLoader, "getSummary", XC_MethodReplacement.returnConstant("Intel(R) Celeron(TM) CPU 2.6GHz"));
        // RAM Info
        XposedHelpers.findAndHookMethod("com.lenovo.settings.deviceinfo.controller.RamSizePreferenceController",
                lpparam.classLoader,
                "getSummary",
                XC_MethodReplacement.returnConstant("256 MB"));
        // ROM Info
        XposedHelpers.findAndHookMethod("com.lenovo.settings.deviceinfo.controller.RomSizePreferenceController",
                lpparam.classLoader,
                "getSummary", XC_MethodReplacement.returnConstant("ExcelStor IDE HDD 80 GB"));
        // Device Model
        XposedHelpers.findAndHookMethod("com.android.settings.deviceinfo.hardwareinfo.DeviceModelPreferenceController",
                lpparam.classLoader,
                "getSummary", XC_MethodReplacement.returnConstant("HASEE Xinxi 2000 Series PC"));
        // Software Version
        XposedHelpers.findAndHookMethod("com.android.settings.deviceinfo.BuildNumberPreferenceController",
                lpparam.classLoader,
                "getSummary", XC_MethodReplacement.returnConstant("Windows XP Professional, Service Pack 3"));
    }
}
