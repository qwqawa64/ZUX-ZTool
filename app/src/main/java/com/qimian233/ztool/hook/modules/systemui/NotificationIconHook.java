package com.qimian233.ztool.hook.modules.systemui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * SystemUI通知图标限制Hook模块
 * 功能：修改状态栏通知图标的最大显示数量限制
 * 支持Android 12+的SystemUI架构
 */
@SuppressLint("PrivateApi")
public class NotificationIconHook extends BaseHookModule {

    private int NEW_MAX_ICONS;
    private static final String PREFS_NAME = "StatusBar_notifyNumSize";

    public NotificationIconHook() {}

    @Override
    public String getModuleName() {
        return "notification_icon_limit";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.systemui"
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.systemui".equals(packageName)) {
            SharedPreferences prefs = this.xposed.getRemotePreferences(PREFS_NAME);
            NEW_MAX_ICONS = prefs.getInt("notify_num_size", 4);
            hookSystemUIIconLimit(classLoader);
        }
    }

    private void hookSystemUIIconLimit(ClassLoader classLoader) {
        log("开始 Hook SystemUI 通知图标限制，设置最大图标数: " + NEW_MAX_ICONS);

        try {
            // Hook 1: 修改资源获取的最大图标数量
//            hookResourceInteger(classLoader);

            // Hook 2: 修改 NotificationIconContainerStatusBarViewModel 的 maxIcons 字段
            hookViewModelConstructor(classLoader);

            // Hook 3: 修改 NotificationIconsViewData 构造函数，应用数量限制
            hookViewDataConstructor(classLoader);

            log("SystemUI 通知图标限制Hook设置完成");
        } catch (Throwable e) {
            logError("SystemUI Hook过程中发生错误", e);
        }
    }

    private void hookViewModelConstructor(ClassLoader classLoader) {
        try {
            Class<?> viewModelClass = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel"
            );

            Constructor<?> ctor = viewModelClass.getDeclaredConstructor(
                    classLoader.loadClass("kotlin.coroutines.CoroutineContext"),
                    classLoader.loadClass("com.android.systemui.statusbar.phone.domain.interactor.DarkIconInteractor"),
                    classLoader.loadClass("com.android.systemui.statusbar.notification.icon.domain.interactor.StatusBarNotificationIconsInteractor"),
                    classLoader.loadClass("com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationIconInteractor"),
                    classLoader.loadClass("com.android.systemui.keyguard.domain.interactor.KeyguardInteractor"),
                    android.content.res.Resources.class,
                    classLoader.loadClass("com.android.systemui.shade.domain.interactor.ShadeInteractor")
            );

            hookWithId(ctor, "ctor_1", chain -> {
                // after constructor: chain.proceed() then set field
                chain.proceed();
                try {
                    Field myField = chain.getThisObject().getClass().getDeclaredField("maxIcons");
                    myField.setAccessible(true);
                    myField.setInt(chain.getThisObject(), NEW_MAX_ICONS);
                    log("成功修改 ViewModel maxIcons 为 " + NEW_MAX_ICONS);
                } catch (Exception e) {
                    logError("修改 ViewModel maxIcons 字段失败", e);
                }
                return null;
            });

            log("ViewModel构造函数Hook设置成功");
        } catch (Throwable e) {
            log("找不到 ViewModel 类，可能系统版本不兼容: " + e.getMessage());
        }
    }

    private void hookViewDataConstructor(ClassLoader classLoader) {
        try {
            Class<?> viewDataClass = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData"
            );

            Constructor<?> ctor = viewDataClass.getDeclaredConstructor(
                    java.util.List.class,
                    int.class,
                    classLoader.loadClass("com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData$LimitType")
            );

            hookWithId(ctor, "ctor_2", chain -> {
                try {
                    // 获取图标列表
                    Object iconList = chain.getArg(0);
                    int listSize = getListSize(iconList);

                    // 使用NEW_MAX_ICONS作为限制，但不超过实际图标数量
                    int effectiveLimit = Math.min(NEW_MAX_ICONS, listSize);
                    int currentLimit = (int) chain.getArg(1);

                    // 只有当当前限制不等于我们设置的有效限制时才修改
                    if (currentLimit != effectiveLimit) {
                        log("修改图标限制 " + currentLimit + " -> " + effectiveLimit + " (图标总数: " + listSize + ")");
                        return chain.proceed(new Object[]{iconList, effectiveLimit, chain.getArg(2)});
                    }
                } catch (Exception e) {
                    logError("ViewData Hook过程中发生错误", e);
                }
                return chain.proceed();
            });

            log("ViewData构造函数Hook设置成功");
        } catch (Throwable e) {
            log("找不到 ViewData 类，可能系统版本不兼容: " + e.getMessage());
        }
    }

    // 辅助方法：获取列表大小
    private int getListSize(Object list) {
        try {
            return (int) list.getClass().getDeclaredMethod("size").invoke(list);
        } catch (Exception e) {
            return 0;
        }
    }
}
