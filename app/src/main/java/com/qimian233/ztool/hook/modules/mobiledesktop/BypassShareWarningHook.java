package com.qimian233.ztool.hook.modules.mobiledesktop;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class BypassShareWarningHook extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    private static final String TARGET_CLASS = "com.motorola.readyfor.tile.BaseFileUnionTile";
    private static final String TARGET_MANAGER_CLASS = "com.motorola.mobiledesktop.manager.c0";
    private static final String PREFS_NAME = "moto_ble_preference";
    private static final String PREF_KEY = "file_union_transfer_switch";

    public BypassShareWarningHook() {}

    @Override
    public String getModuleName() {
        return "bypass_share_warning";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        try {
            // 磁贴 Hook, 直接拦截监听器就可以干掉弹窗
            Class<?> baseFileUnionTileClass = classLoader.loadClass(TARGET_CLASS);
            Method onClickMethod = baseFileUnionTileClass.getDeclaredMethod("onClick");
            this.xposed.hook(onClickMethod).intercept(chain -> {
                Object tile = chain.getThisObject();
                Context context = getContext(tile);
                if (context == null) {
                    return chain.proceed();
                }

                boolean enabled = isNearbyShareEnabled(context);
                log("IsNearbyShareEnabled: " + enabled);
                if (enabled) {
                    log("Nearby share already enabled, keep original disable flow.");
                    return chain.proceed();
                }

                setNearbyShareEnabled(tile, context, classLoader);
                log("Bypassed warning and enabled nearby share directly.");
                return null;
            });
            log("Installed hook for BaseFileUnionTile.onClick");
        } catch (Throwable t) {
            logError("Failed to hook BaseFileUnionTile.onClick", t);
        }
        try {
            // 处理其它弹窗场景，例如在超级互联活动内部打开互传开关
            // 这里用了另外一个方法，直接启动一个 Intent 而且初始化逻辑非常复杂（大量的 if 还有无法修改的 final 局部变量），直接替换创建和启动 Intent 的方法反而会比较经济
            Class<?> actionNoticeClass = classLoader.loadClass(
                    "com.motorola.readyfor.common.dialog.ActionNoticeCommonDialogActivity");
            Method pMethod = actionNoticeClass.getDeclaredMethod("p");
            this.xposed.hook(pMethod).intercept(chain -> {
                Object myObject = chain.getThisObject();
                Context context = getContext(myObject);

                Class<?> managerClass = classLoader.loadClass(TARGET_MANAGER_CLASS);
                Method lMethod = managerClass.getDeclaredMethod("l", Context.class);
                Object manager = lMethod.invoke(null, context);

                Method zMethod = manager.getClass().getDeclaredMethod("z", boolean.class);
                zMethod.setAccessible(true);
                zMethod.invoke(manager, true);
                return null;
            });
        } catch (Exception e) {
            logError("Failed to hook createAndStartExposureWarnIntent: ", e);
        }
    }

    private Context getContext(Object tile) {
        try {
            Method getApplicationContextMethod = tile.getClass().getMethod("getApplicationContext");
            Object context = getApplicationContextMethod.invoke(tile);
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

    private void setNearbyShareEnabled(Object tile, Context context, ClassLoader classLoader) {
        try {
            Class<?> managerClass = classLoader.loadClass(TARGET_MANAGER_CLASS);
            Method lMethod = managerClass.getDeclaredMethod("l", Context.class);
            Object manager = lMethod.invoke(null, context);

            Method zMethod = manager.getClass().getDeclaredMethod("z", boolean.class);
            zMethod.setAccessible(true);
            zMethod.invoke(manager, true);

            Method bMethod = tile.getClass().getDeclaredMethod("b");
            bMethod.setAccessible(true);
            bMethod.invoke(tile);

            log("successfully set share to enabled");
        } catch (Exception e) {
            logError("Failed to set nearby share to enable: ", e);
        }
    }
}
