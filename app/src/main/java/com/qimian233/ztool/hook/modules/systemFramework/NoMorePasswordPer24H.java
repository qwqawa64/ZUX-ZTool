package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NoMorePasswordPer24H extends BaseHookModule {
    private static final String TAG = "NoMorePasswordPer24H";
    @Override
    public String getModuleName(){return TAG;}
    @Override
    public String[] getTargetPackages(){return new String[]{"android"};}

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                lpparam.classLoader,
                "rescheduleStrongAuthTimeoutAlarm",
                long.class, int.class,
                XC_MethodReplacement.DO_NOTHING);
        XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                lpparam.classLoader,
                "handleScheduleNonStrongBiometricIdleTimeout",
                int.class,
                XC_MethodReplacement.DO_NOTHING);
        XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                lpparam.classLoader,
                "handleScheduleNonStrongBiometricTimeout",
                int.class,
                XC_MethodReplacement.DO_NOTHING);
    }
}