package com.qimian233.ztool.hook.modules.mobiledesktop;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassShareWarningHook extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    private static final String TARGET_CLASS = "com.motorola.readyfor.tile.BaseFileUnionTile";
    private static final String TARGET_MANAGER_CLASS = "com.motorola.mobiledesktop.manager.c0";
    private static final String PREFS_NAME = "moto_ble_preference";
    private static final String PREF_KEY = "file_union_transfer_switch";

    @Override
    public String getModuleName() {
        return "bypass_share_warning";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            XposedHelpers.findAndHookMethod(
                    TARGET_CLASS,
                    lpparam.classLoader,
                    "onClick",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object tile = param.thisObject;
                            Context context = getContext(tile);
                            if (context == null) {
                                return;
                            }

                            boolean enabled = isNearbyShareEnabled(context);
                            log("IsNearbyShareEnabled: " + enabled);
                            if (enabled) {
                                log("Nearby share already enabled, keep original disable flow.");
                                return;
                            }

                            setNearbyShareEnabled(tile, context);
                            log("Bypassed warning and enabled nearby share directly.");
                            param.setResult(null);
                        }
                    }
            );
            log("Installed hook for BaseFileUnionTile.onClick");
        } catch (Throwable t) {
            logError("Failed to hook BaseFileUnionTile.onClick", t);
        }
    }

    private Context getContext(Object tile) {
        try {
            Object context = XposedHelpers.callMethod(tile, "getApplicationContext");
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isNearbyShareEnabled(Context context) {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(PREF_KEY, false);
        } catch (Throwable t) {
            logError("Failed to read nearby share state", t);
            return false;
        }
    }

    private void setNearbyShareEnabled(Object tile, Context context) {
        try {
            Object manager = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass(TARGET_MANAGER_CLASS, tile.getClass().getClassLoader()),
                    "l",
                    context
            );
            XposedHelpers.callMethod(manager, "z", true);
            XposedHelpers.callMethod(tile, "b");
            log("successfully set share to enabled");
        } catch (Exception e) {
            logError("Failed to set nearby share to enable: ", e);
        }
    }
}
