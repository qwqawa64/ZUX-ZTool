package com.qimian233.ztool.hook.modules.systemFramework;

import android.os.Handler;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.Date;

import de.robv.android.xposed.XC_MethodHook;
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
        if (!lpparam.packageName.equals("android")) return;
        // First, hook LockSettingsStrongAuth to bypass password timeout
        try {
            Class<?> alarmManagerClass = XposedHelpers.findClass(
                    "android.app.AlarmManager", lpparam.classLoader);

            Class<?> alarmListenerClass = XposedHelpers.findClass(
                    "android.app.AlarmManager$OnAlarmListener", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(alarmManagerClass, "setExact",
                    int.class, long.class, String.class, alarmListenerClass, Handler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handleAlarmSetExact(param);
                        }
                    });
            /*
            XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                    lpparam.classLoader, "rescheduleStrongAuthTimeoutAlarm", long.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int userId = (int) param.args[1];

                            // Set timeout to a huge value (1 Year later)
                            long oneYearLater = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
                            param.args[0] = oneYearLater;

                            log("Modded strong auth timeout, UserID: " + userId +
                                    ", new timeout (timestamp): " + oneYearLater);
                        }
                    });

            XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                    lpparam.classLoader, "handleScheduleNonStrongBiometricTimeout",
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            log("Blocked handleScheduleNonStrongBiometricTimeout");
                            return null;
                        }
                    });
            XposedHelpers.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth",
                    lpparam.classLoader, "handleScheduleNonStrongBiometricIdleTimeout",
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            log("Blocked handleScheduleNonStrongBiometricIdleTimeout");
                            return null;
                        }
                    });*/
            log("Blocked rescheduleStrongAuthTimeoutAlarm");
        } catch (Exception e) {
            logError("Failed to hook systemFramework， com.android.server.locksettings.LockSettingsStrongAuth$rescheduleStrongAuthTimeoutAlarm", e);
        }
    }

    private void handleAlarmSetExact(XC_MethodHook.MethodHookParam param) {
        try {
            String tag = (String) param.args[2];

            if (tag == null) {
                return;
            }

            // 检查是否是强认证相关的闹钟
            boolean isStrongAuthAlarm = tag.contains("strong_auth") ||
                    tag.contains("STRONG_AUTH") ||
                    tag.contains("LockSettings");

            if (isStrongAuthAlarm) {
                long originalTime = (long) param.args[1];
                long currentTime = System.currentTimeMillis();

                // 计算1年后的时间
                long oneYearInMillis = 365L * 24 * 60 * 60 * 1000;
                long newTime = currentTime + oneYearInMillis;

                // 确保时间不会溢出
                if (newTime < 0) {
                    newTime = Long.MAX_VALUE - 10000;
                }

                param.args[1] = newTime;

                log(String.format(
                        "[StrongAuthHook] 修改强认证闹钟:\n" +
                                "Tag: %s\n" +
                                "原时间: %d (%s)\n" +
                                "新时间: %d (%s)",
                        tag,
                        originalTime, new Date(originalTime),
                        newTime, new Date(newTime)
                ));
            }

        } catch (Exception e) {
            log("处理AlarmManager.setExact时出错: " + e);
        }
    }
}