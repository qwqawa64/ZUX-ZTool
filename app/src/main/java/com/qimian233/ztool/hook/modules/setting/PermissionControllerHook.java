package com.qimian233.ztool.hook.modules.setting;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class PermissionControllerHook extends AppHookModule {

    public PermissionControllerHook() {}

    @Override
    public String getModuleName() {
        return "PermissionControllerHook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.permissioncontroller", "com.android.settings", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!isEnabled()) return;
        logger.info("Loading module PermissionControllerHook.");
        try {
            if ("com.android.permissioncontroller".equals(packageName)) {
                logger.debug("com.android.permissioncontroller detected. Hooking...");
                handleLoadPermissionController(classLoader);
            } else if ("com.android.settings".equals(packageName)) {
                logger.debug("com.android.settings detected. Hooking...");
                new SettingsHook().handleLoadSettings(classLoader, this.xposed);
            } else if ("com.zui.safecenter".equals(packageName)) {
                logger.debug("com.zui.safecenter detected. Hooking...");
                handleLoadSafeCenter(classLoader);
            }
            logger.info("Hook is successful.");
        } catch (Exception e) {
            logger.error("Error hooking", e);
        }
    }

    private void handleLoadSafeCenter(ClassLoader classLoader) throws Throwable {
        Class<?> cls = classLoader.loadClass("com.lenovo.xuipermissionmanager.XuiPermissionManager");
        Class<?> superclass = cls.getSuperclass();
        Method onCreate = cls.getDeclaredMethod("onCreate", Bundle.class);
        Method super_onCreate = superclass.getDeclaredMethod("onCreate", Bundle.class);
        final MethodHandle super_onCreate_invokespecial = MethodHandles.lookup().unreflectSpecial(super_onCreate, cls);
        hookWithId(onCreate, "on_create_1", chain -> {
            // redirect to AOSP permission manager
            super_onCreate_invokespecial.invoke(chain.getThisObject(), chain.getArg(0));
            Activity activity = (Activity) chain.getThisObject();
            activity.startActivity(new Intent("android.intent.action.MANAGE_PERMISSIONS"));
            activity.finish();
            return null;
        });

        Method onDestroy = cls.getDeclaredMethod("onDestroy");
        hookWithId(onDestroy, "on_destroy", chain -> null);
    }

    static class SettingsHook {
        private final ThreadLocal<Boolean> isRowVersionTls = new ThreadLocal<>();

        public void handleLoadSettings(ClassLoader classLoader, XposedInterface xposed) {
            try {
                // Hook LenovoUtils.isRowVersion with ThreadLocal check
                Method isRowVersionMethod = classLoader
                        .loadClass("com.lenovo.common.utils.LenovoUtils")
                        .getDeclaredMethod("isRowVersion");
                xposed.hook(isRowVersionMethod).setId("is_row_version").intercept(chain -> {
                    Boolean value = isRowVersionTls.get();
                    if (value != null) {
                        return value;
                    }
                    return chain.proceed();
                });
            } catch (Throwable ignored) {}

            // Helper: wraps a method with isRowVersionTls set/remove
            try {
                Method startMethod = classLoader
                        .loadClass("com.android.settings.applications.appinfo.AppPermissionPreferenceController")
                        .getDeclaredMethod("startManagePermissionsActivity");
                xposed.hook(startMethod).setId("start").intercept(chain -> {
                    isRowVersionTls.set(true);
                    try {
                        return chain.proceed();
                    } finally {
                        isRowVersionTls.remove();
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> prefClass = classLoader.loadClass("androidx.preference.Preference");
                Method clickMethod = classLoader
                        .loadClass("com.lenovo.settings.privacy.PrivacyManagerPreferenceController")
                        .getDeclaredMethod("handlePreferenceTreeClick", prefClass);
                xposed.hook(clickMethod).setId("click").intercept(chain -> {
                    isRowVersionTls.set(true);
                    try {
                        return chain.proceed();
                    } finally {
                        isRowVersionTls.remove();
                    }
                });
            } catch (Throwable ignored) {}

            try {
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    Method permClickMethod = classLoader
                            .loadClass("com.lenovo.settings.applications.LenovoAppHeaderPreferenceController")
                            .getDeclaredMethod("handlePermissionClick");
                    xposed.hook(permClickMethod).setId("perm_click").intercept(chain -> {
                        isRowVersionTls.set(true);
                        try {
                            return chain.proceed();
                        } finally {
                            isRowVersionTls.remove();
                        }
                    });
                } else {
                    Class<?> viewClass = classLoader.loadClass("android.view.View");
                    Method lambdaMethod = classLoader
                            .loadClass("com.lenovo.settings.applications.LenovoAppHeaderPreferenceController")
                            .getDeclaredMethod("lambda$initAppEntryList$0$com-lenovo-settings-applications-LenovoAppHeaderPreferenceController", viewClass);
                    xposed.hook(lambdaMethod).setId("lambda").intercept(chain -> {
                        isRowVersionTls.set(true);
                        try {
                            return chain.proceed();
                        } finally {
                            isRowVersionTls.remove();
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    private void handleLoadPermissionController(ClassLoader classLoader) {
        Class<?> zuiUtilsCls = null;
        try {
            zuiUtilsCls = classLoader.loadClass("com.android.permissioncontroller.extra.ZuiUtils");
        } catch (ClassNotFoundException e1) {
            try {
                zuiUtilsCls = classLoader.loadClass("com.android.permissioncontroller.permission.utils.ZuiUtils");
            } catch (ClassNotFoundException ignored) {}
        }
        if (zuiUtilsCls != null) {
            try {
                Method m = zuiUtilsCls.getDeclaredMethod("isCTSandGTS", String.class);
                hookWithId(m, "hook_165", chain -> Boolean.TRUE);
            } catch (Throwable ignored) {}
        } else {
            logger.warn("[PermissionControllerHook] ZuiUtils not found");
        }

        if (Build.VERSION.SDK_INT <= 34) {
            try {
                Method onCreateMethod = classLoader
                        .loadClass("com.android.permissioncontroller.permission.ui.GrantPermissionsActivity")
                        .getDeclaredMethod("onCreate", Bundle.class);
                hookWithId(onCreateMethod, "on_create_2", chain -> {
                    Activity activity = (Activity) chain.getThisObject();
                    activity.setTheme(android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
                    activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    android.view.View rootView = activity.getWindow().getDecorView();
                    rootView.setFilterTouchesWhenObscured(true);
                    rootView.setPadding(0, 0, 0, 0);
                    return chain.proceed();
                });
            } catch (Throwable ignored) {}
        }
    }
}
