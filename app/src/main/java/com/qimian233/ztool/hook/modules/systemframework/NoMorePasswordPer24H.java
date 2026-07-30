package com.qimian233.ztool.hook.modules.systemframework;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint({"PrivateApi"})
public class NoMorePasswordPer24H extends BaseHookModule {
    private static final String TAG = "NoMorePasswordPer24H";

    public NoMorePasswordPer24H() {}

    @Override
    public String getModuleName(){return TAG;}
    @Override
    public String[] getTargetPackages(){return new String[]{"system"};}

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {

    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();

        Class<?> lockSettingsClass = classLoader.loadClass(
                "com.android.server.locksettings.LockSettingsStrongAuth");

        Method rescheduleMethod = lockSettingsClass.getDeclaredMethod(
                "rescheduleStrongAuthTimeoutAlarm", long.class, int.class);
        hookWithId(rescheduleMethod, "reschedule_strong_auth", chain -> null);

        Method handleIdleMethod = lockSettingsClass.getDeclaredMethod(
                "handleScheduleNonStrongBiometricIdleTimeout", int.class);
        hookWithId(handleIdleMethod, "handle_idle_timeout", chain -> null);

        Method handleTimeoutMethod = lockSettingsClass.getDeclaredMethod(
                "handleScheduleNonStrongBiometricTimeout", int.class);
        hookWithId(handleTimeoutMethod, "handle_timeout", chain -> null);
    }
}
