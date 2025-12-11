package com.qimian233.ztool.hook.modules.setting;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.lang.invoke.MethodHandles;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PermissionControllerHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "PermissionControllerHook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.permissioncontroller", "com.android.settings", "com.zui.safecenter"};
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isEnabled()) return;
        log("[PermissionControllerHook] Loading module PermissionControllerHook.");
        try {
            if ("com.android.permissioncontroller".equals(lpparam.packageName)) {
                log("[PermissionControllerHook] com.android.permissioncontroller detected. Hooking...");
                handleLoadPermissionController(lpparam);
            } else if ("com.android.settings".equals(lpparam.packageName)) {
                log("[PermissionControllerHook] com.android.settings detected. Hooking...");
                new SettingsHook().handleLoadSettings(lpparam);
            } else if ("com.zui.safecenter".equals(lpparam.packageName)) {
                log("[PermissionControllerHook] com.zui.safecenter detected. Hooking...");
                handleLoadSafeCenter(lpparam);
            }
            log("[PermissionControllerHook] Hook is successful.");
        }catch (Exception e) {
            logError("[PermissionControllerHook] Error hooking", e);
        }
    }

    private static void handleLoadSafeCenter(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        var cls = XposedHelpers.findClass("com.lenovo.xuipermissionmanager.XuiPermissionManager", lpparam.classLoader);
        var superclass = cls.getSuperclass();
        var onCreate = XposedHelpers.findMethodExact(cls, "onCreate", Bundle.class);
        var super_onCreate = XposedHelpers.findMethodExact(superclass, "onCreate", Bundle.class);
        final var super_onCreate_invokespecial = MethodHandles.lookup().unreflectSpecial(super_onCreate, cls);
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // redirect to AOSP permission manager
                super_onCreate_invokespecial.invoke(param.thisObject, param.args[0]);
                var activity = (Activity)param.thisObject;
                activity.startActivity(new Intent("android.intent.action.MANAGE_PERMISSIONS"));
                activity.finish();
                param.setResult(null);
            }
        });
        XposedHelpers.findAndHookMethod(cls, "onDestroy", XC_MethodReplacement.DO_NOTHING);
    }

    static class SettingsHook {
        private final ThreadLocal<Boolean> isRowVersionTls = new ThreadLocal<>();

        private class IsRowVersionHook extends XC_MethodHook {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                var value = isRowVersionTls.get();
                if (value != null) {
                    param.setResult(value);
                }
            }
        }

        private class IsRowVersionTlsHook extends XC_MethodHook {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                isRowVersionTls.set(true);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                isRowVersionTls.remove();
            }
        }

        public void handleLoadSettings(XC_LoadPackage.LoadPackageParam lpparam) {
            XposedHelpers.findAndHookMethod("com.lenovo.common.utils.LenovoUtils", lpparam.classLoader, "isRowVersion", new IsRowVersionHook());

            // make settings invoke AOSP permission manager
            XposedHelpers.findAndHookMethod("com.android.settings.applications.appinfo.AppPermissionPreferenceController", lpparam.classLoader, "startManagePermissionsActivity", new IsRowVersionTlsHook());
            XposedHelpers.findAndHookMethod("com.lenovo.settings.privacy.PrivacyManagerPreferenceController", lpparam.classLoader, "handlePreferenceTreeClick", "androidx.preference.Preference", new IsRowVersionTlsHook());
            XposedHelpers.findAndHookMethod("com.lenovo.settings.applications.LenovoAppHeaderPreferenceController", lpparam.classLoader, "lambda$initAppEntryList$0$com-lenovo-settings-applications-LenovoAppHeaderPreferenceController", "android.view.View", new IsRowVersionTlsHook());
        }
    }

    public static void handleLoadPermissionController(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> zuiUtilsCls = XposedHelpers.findClassIfExists("com.android.permissioncontroller.extra.ZuiUtils", lpparam.classLoader);
        if (zuiUtilsCls == null) {
            zuiUtilsCls = XposedHelpers.findClassIfExists("com.android.permissioncontroller.permission.utils.ZuiUtils", lpparam.classLoader);
        }
        if (zuiUtilsCls != null) {
            XposedHelpers.findAndHookMethod(zuiUtilsCls, "isCTSandGTS", String.class, XC_MethodReplacement.returnConstant(Boolean.TRUE));
        }
        else {
            XposedBridge.log("ZuiUtils not found");
        }

        if (Build.VERSION.SDK_INT <= 34) {
            XposedHelpers.findAndHookMethod("com.android.permissioncontroller.permission.ui.GrantPermissionsActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    var activity = (Activity) param.thisObject;
                    activity.setTheme(android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
                    activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    var rootView = activity.getWindow().getDecorView();
                    rootView.setFilterTouchesWhenObscured(true);
                    rootView.setPadding(0, 0, 0, 0);
                }
            });
        }
    }
}
