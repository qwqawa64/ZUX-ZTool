package com.qimian233.ztool.hook.modules.launcher;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomGridSize extends BaseHookModule {
    private static int CUSTOM_COLUMNS = 8;
    private static int CUSTOM_ROWS = 6;

    @Override
    public String getModuleName() {
        return "CustomGridSize";
    }
    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.launcher"};
    }
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (DEBUG) log("Load CustomGridSize!");
        // We directly hook the constructor of GridOption class
        // But before hook, let us load custom grid size from shared prefs first
        getCustomGridSize();
        try {
            // First find GridOption class (I DO NOT believe Lenovo will mod this class)
            Class<?> gridOptionClass = XposedHelpers.findClassIfExists(
                    "com.android.launcher3.InvariantDeviceProfile$GridOption",
                    lpparam.classLoader
            );

            if (gridOptionClass != null) {
                if (DEBUG) log("Found GridOption class!");
                // Then formally start our job
                // Find arg class to construct correct method signature
                Class<?> contextClass = Context.class;
                Class<?> attributeSetClass = XposedHelpers.findClass("android.util.AttributeSet", lpparam.classLoader);
                Class<?> displayInfoClass = XposedHelpers.findClass("com.android.launcher3.util.DisplayController$Info", lpparam.classLoader);

                XposedHelpers.findAndHookConstructor(gridOptionClass,
                        contextClass, attributeSetClass, displayInfoClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    // Directly set int fields
                                    XposedHelpers.setIntField(param.thisObject, "numColumns", CUSTOM_COLUMNS);
                                    XposedHelpers.setIntField(param.thisObject, "numRows", CUSTOM_ROWS);

                                    if (DEBUG) log("GridOption config modded to " + CUSTOM_COLUMNS + "x" + CUSTOM_ROWS);
                                } catch (NoSuchMethodError e) {
                                    logError("No such method! Probably you are using a newer ZUXOS version!", e);
                                }
                            }
                        });
            }
        } catch (Exception e) {
            logError("Failed to hook GridOption!", e);
        }
    }

    private void getCustomGridSize() {
        XSharedPreferences prefs = new XSharedPreferences("com.qimian233.ztool",
                "xposed_module_config");
        prefs.reload();
        CUSTOM_ROWS = prefs.getInt("CustomLauncherRow", 4);
        CUSTOM_COLUMNS = prefs.getInt("CustomLauncherColumn", 6);
    }
}
