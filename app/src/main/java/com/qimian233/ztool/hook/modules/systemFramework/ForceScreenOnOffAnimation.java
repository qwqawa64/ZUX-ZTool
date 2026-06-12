package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceScreenOnOffAnimation extends BaseHookModule {
    private static final String DISPLAY_POWER_CONTROLLER =
            "com.android.server.display.DisplayPowerController";
    private static final String DISPLAY_POWER_CONTROLLER_INJECTOR =
            DISPLAY_POWER_CONTROLLER + "$Injector";

    @Override
    public String getModuleName() {
        return "force_screen_on_off_animation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"android"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        try {
            log("Executing hook for DisplayPowerController screen on/off animation...");
            XposedHelpers.findAndHookMethod(
                    DISPLAY_POWER_CONTROLLER_INJECTOR,
                    lpparam.classLoader,
                    "isColorFadeEnabled",
                    XC_MethodReplacement.returnConstant(true)
            );
            hookDisplayPowerControllerConstructor(lpparam.classLoader);
        } catch (Exception e) {
            logError("Failed to hook DisplayPowerController: ", e);
        }
    }

    private void hookDisplayPowerControllerConstructor(ClassLoader classLoader) {
        XposedHelpers.findAndHookConstructor(
                DISPLAY_POWER_CONTROLLER,
                classLoader,
                XposedHelpers.findClass("android.content.Context", classLoader),
                XposedHelpers.findClass(DISPLAY_POWER_CONTROLLER_INJECTOR, classLoader),
                XposedHelpers.findClass(
                        "android.hardware.display.DisplayManagerInternal$DisplayPowerCallbacks",
                        classLoader),
                XposedHelpers.findClass("android.os.Handler", classLoader),
                XposedHelpers.findClass("android.hardware.SensorManager", classLoader),
                XposedHelpers.findClass("com.android.server.display.DisplayBlanker", classLoader),
                XposedHelpers.findClass("com.android.server.display.LogicalDisplay", classLoader),
                XposedHelpers.findClass("com.android.server.display.BrightnessTracker", classLoader),
                XposedHelpers.findClass("com.android.server.display.BrightnessSetting", classLoader),
                XposedHelpers.findClass("java.lang.Runnable", classLoader),
                XposedHelpers.findClass(
                        "com.android.server.display.HighBrightnessModeMetadata",
                        classLoader),
                boolean.class,
                XposedHelpers.findClass(
                        "com.android.server.display.feature.DisplayManagerFlags",
                        classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedHelpers.setBooleanField(param.thisObject, "mColorFadeEnabled", true);
                        XposedHelpers.setBooleanField(param.thisObject, "mColorFadeFadesConfig", true);
                        log("Forced DisplayPowerController color fade animation enabled.");
                    }
                }
        );
    }
}
