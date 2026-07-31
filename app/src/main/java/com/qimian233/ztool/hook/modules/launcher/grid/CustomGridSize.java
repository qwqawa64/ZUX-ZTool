package com.qimian233.ztool.hook.modules.launcher.grid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@SuppressLint("PrivateApi")
public class CustomGridSize extends AppHookModule {
    private static int CUSTOM_COLUMNS = 8;
    private static int CUSTOM_ROWS = 6;

    public CustomGridSize() {}

    @Override
    public String getModuleName() {
        return "CustomGridSize";
    }
    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.launcher"};
    }
    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        logger.info("Load CustomGridSize!");
        // We directly hook the constructor of GridOption class
        // But before hook, let us load custom grid size from shared prefs first
        getCustomGridSize();
        try {
            // First find GridOption class (I DO NOT believe Lenovo will mod this class)
            final Class<?> gridOptionClass;
            try {
                gridOptionClass = classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile$GridOption");
            } catch (ClassNotFoundException e) {
                logger.error("GridOption class not found on this ROM");
                return;
            }
            logger.debug("Found GridOption class!");
            // Then formally start our job
            // Find arg class to construct correct method signature
            Class<?> contextClass = Context.class;
            Class<?> attributeSetClass = classLoader.loadClass("android.util.AttributeSet");
            Class<?> displayInfoClass = null;
            try {
                displayInfoClass = classLoader.loadClass("com.android.launcher3.util.DisplayController$Info");
            } catch (ClassNotFoundException ignored) {
                logger.error("Unable to find DisplayInfo class");
            }
            Constructor<?> ctor;
            if (displayInfoClass != null) {
                try {
                    ctor = gridOptionClass.getDeclaredConstructor(
                            contextClass, attributeSetClass, displayInfoClass);
                } catch (Exception e) {
                    logger.error("Exception happened when trying to find GridOption class with constructor signature Context, AttributeSet, DisplayController$Info: ", e);
                    try {
                        ctor = gridOptionClass.getDeclaredConstructor(
                                contextClass, attributeSetClass);
                    } catch (Exception ignored) {
                        logger.error("Failed to get constructor with alternate way, exiting.");
                        return;
                    }
                }
            } else {
                logger.warn("Cannot find DisplayController$Info, use alternate constructor signature.");
                try {
                    ctor = gridOptionClass.getDeclaredConstructor(
                            contextClass, attributeSetClass);
                }
                catch (Exception e) {
                    logger.error("Failed to find constructor, exiting.", e);
                    return;
                }
            }
            hookWithId(ctor, "ctor", chain -> {
                chain.proceed();
                try {
                    Object thisObject = chain.getThisObject();
                    // Directly set int fields
                    Field numColsField = gridOptionClass.getDeclaredField("numColumns");
                    numColsField.setAccessible(true);
                    numColsField.set(thisObject, CUSTOM_COLUMNS);

                    Field numRowsField = gridOptionClass.getDeclaredField("numRows");
                    numRowsField.setAccessible(true);
                    numRowsField.set(thisObject, CUSTOM_ROWS);

                    logger.debug("GridOption config modded to " + CUSTOM_COLUMNS + "x" + CUSTOM_ROWS);
                } catch (Exception e) {
                    logger.error("No such method! Probably you are using a newer ZUXOS version!", e);
                }
                return null;
            });
        } catch (Exception e) {
            logger.error("Failed to hook GridOption!", e);
        }
    }

    private void getCustomGridSize() {
        SharedPreferences prefs = this.xposed.getRemotePreferences("xposed_module_config");
        CUSTOM_ROWS = prefs.getInt("CustomLauncherRow", 4);
        CUSTOM_COLUMNS = prefs.getInt("CustomLauncherColumn", 6);
    }
}
