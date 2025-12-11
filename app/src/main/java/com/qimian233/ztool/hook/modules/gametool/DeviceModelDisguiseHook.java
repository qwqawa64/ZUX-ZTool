package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 游戏服务设备型号伪装Hook模块
 * 将设备型号伪装为TB322FC，用于绕过游戏服务的设备检测
 */
public class DeviceModelDisguiseHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "disguise_TB322FC";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.zui.game.service"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.zui.game.service".equals(packageName)) {
            hookDeviceUtils(lpparam);
        }
    }

    private void hookDeviceUtils(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 查找DeviceUtils类
            Class<?> deviceUtilsClass = XposedHelpers.findClass("com.zui.util.DeviceUtils", lpparam.classLoader);

            // Hook getBuildModel方法，强制返回目标型号
            XposedHelpers.findAndHookMethod(deviceUtilsClass, "getBuildModel", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    // 强制返回目标型号TB322FC
                    return "TB322FC";
                }
            });

            log("Successfully hooked DeviceUtils.getBuildModel for com.zui.game.service");

        } catch (Exception e) {
            logError("Failed to hook DeviceUtils.getBuildModel", e);
        }
    }
}
