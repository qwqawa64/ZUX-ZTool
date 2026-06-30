package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class NoMorePasswordPer24H extends BaseHookModule {
    private static final String TAG = "NoMorePasswordPer24H";

    public NoMorePasswordPer24H() {}

    @Override
    public String getModuleName(){return TAG;}
    @Override
    public String[] getTargetPackages(){return new String[]{"system"};}

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();

        Class<?> lockSettingsClass = classLoader.loadClass(
                "com.android.server.locksettings.LockSettingsStrongAuth");

        Method rescheduleMethod = lockSettingsClass.getDeclaredMethod(
                "rescheduleStrongAuthTimeoutAlarm", long.class, int.class);
        this.xposed.hook(rescheduleMethod).intercept(chain -> null);

        Method handleIdleMethod = lockSettingsClass.getDeclaredMethod(
                "handleScheduleNonStrongBiometricIdleTimeout", int.class);
        this.xposed.hook(handleIdleMethod).intercept(chain -> null);

        Method handleTimeoutMethod = lockSettingsClass.getDeclaredMethod(
                "handleScheduleNonStrongBiometricTimeout", int.class);
        this.xposed.hook(handleTimeoutMethod).intercept(chain -> null);
    }
}
