package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 游戏服务设备型号伪装Hook模块
 * 将设备型号伪装为TB322FC，用于绕过游戏服务的设备检测
 */
public class DeviceModelDisguiseHook extends AppHookModule {

    public DeviceModelDisguiseHook() {}

    @Override
    public String getModuleName() {
        return "disguise_TB322FC";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.GAME_SERVICE.packageName
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (ScopeKeys.GAME_SERVICE.packageName.equals(packageName)) {
            hookDeviceUtils(classLoader);
        }
    }

    private void hookDeviceUtils(ClassLoader classLoader) {
        try {
            // 查找DeviceUtils类
            Class<?> deviceUtilsClass = classLoader.loadClass("com.zui.util.DeviceUtils");

            // Hook getBuildModel方法，强制返回目标型号
            Method getBuildModelMethod = deviceUtilsClass.getDeclaredMethod("getBuildModel");
            hookWithId(getBuildModelMethod, "get_build_model", chain -> "TB322FC");

            logger.info("Successfully hooked DeviceUtils.getBuildModel for com.zui.game.service");

        } catch (Exception e) {
            logger.error("Failed to hook DeviceUtils.getBuildModel", e);
        }
    }
}
