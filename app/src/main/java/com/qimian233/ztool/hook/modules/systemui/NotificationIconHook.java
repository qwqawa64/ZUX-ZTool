package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUI通知图标限制Hook模块
 * 功能：修改状态栏通知图标的最大显示数量限制
 * 支持Android 12+的SystemUI架构
 */
public class NotificationIconHook extends BaseHookModule {

    private int NEW_MAX_ICONS;
    private static final String PREFS_NAME = "StatusBar_notifyNumSize";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            NEW_MAX_ICONS = prefs.getInt("notify_num_size", 4);
            hookSystemUIIconLimit(lpparam);
        }
    }

    private void hookSystemUIIconLimit(XC_LoadPackage.LoadPackageParam lpparam) {
        log("开始 Hook SystemUI 通知图标限制，设置最大图标数: " + NEW_MAX_ICONS);

        try {
            // Hook 1: 修改资源获取的最大图标数量
            hookResourceInteger(lpparam);

            // Hook 2: 修改 NotificationIconContainerStatusBarViewModel 的 maxIcons 字段
            hookViewModelConstructor(lpparam);

            // Hook 3: 修改 NotificationIconsViewData 构造函数，应用数量限制
            hookViewDataConstructor(lpparam);

            log("SystemUI 通知图标限制Hook设置完成");
        } catch (Throwable e) {
            logError("SystemUI Hook过程中发生错误", e);
        }
    }

    private void hookResourceInteger(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.content.res.Resources",
                    lpparam.classLoader,
                    "getInteger",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                // 获取资源ID名称
                                String resName = getResourceName((Integer) param.args[0], lpparam.classLoader);
                                if (resName != null && resName.contains("max_notif_static_icons")) {
                                    int originalValue = (Integer) param.getResult();
                                    log("拦截到 max_notif_static_icons 资源获取，原值: " + originalValue + "，修改为: " + NEW_MAX_ICONS);
                                    param.setResult(NEW_MAX_ICONS);
                                }
                            } catch (Exception e) {
                                logError("资源Hook过程中发生错误", e);
                            }
                        }
                    }
            );
            log("资源整数Hook设置成功");
        } catch (Throwable e) {
            logError("设置资源整数Hook失败", e);
        }
    }

    private void hookViewModelConstructor(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> viewModelClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookConstructor(
                    viewModelClass,
                    "kotlin.coroutines.CoroutineContext",
                    "com.android.systemui.statusbar.phone.domain.interactor.DarkIconInteractor",
                    "com.android.systemui.statusbar.notification.icon.domain.interactor.StatusBarNotificationIconsInteractor",
                    "com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationIconInteractor",
                    "com.android.systemui.keyguard.domain.interactor.KeyguardInteractor",
                    "android.content.res.Resources",
                    "com.android.systemui.shade.domain.interactor.ShadeInteractor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedHelpers.setIntField(param.thisObject, "maxIcons", NEW_MAX_ICONS);
                                log("成功修改 ViewModel maxIcons 为 " + NEW_MAX_ICONS);
                            } catch (Exception e) {
                                logError("修改 ViewModel maxIcons 字段失败", e);
                            }
                        }
                    }
            );
            log("ViewModel构造函数Hook设置成功");
        } catch (Throwable e) {
            log("找不到 ViewModel 类，可能系统版本不兼容: " + e.getMessage());
        }
    }

    private void hookViewDataConstructor(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> viewDataClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookConstructor(
                    viewDataClass,
                    "java.util.List",
                    int.class,
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconsViewData$LimitType",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                // 获取图标列表
                                Object iconList = param.args[0];
                                int listSize = getListSize(iconList);

                                // 使用NEW_MAX_ICONS作为限制，但不超过实际图标数量
                                int effectiveLimit = Math.min(NEW_MAX_ICONS, listSize);
                                int currentLimit = (int) param.args[1];

                                // 只有当当前限制不等于我们设置的有效限制时才修改
                                if (currentLimit != effectiveLimit) {
                                    param.args[1] = effectiveLimit;
                                    log("修改图标限制 " + currentLimit + " -> " + effectiveLimit + " (图标总数: " + listSize + ")");
                                }
                            } catch (Exception e) {
                                logError("ViewData Hook过程中发生错误", e);
                            }
                        }
                    }
            );
            log("ViewData构造函数Hook设置成功");
        } catch (Throwable e) {
            log("找不到 ViewData 类，可能系统版本不兼容: " + e.getMessage());
        }
    }

    // 辅助方法：获取资源名称
    private String getResourceName(int resId, ClassLoader classLoader) {
        try {
            Class<?> resClass = XposedHelpers.findClass("android.content.res.Resources", classLoader);
            Object resources = XposedHelpers.callStaticMethod(resClass, "getSystem");
            return (String) XposedHelpers.callMethod(resources, "getResourceName", resId);
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取列表大小
    private int getListSize(Object list) {
        try {
            return (int) XposedHelpers.callMethod(list, "size");
        } catch (Exception e) {
            return 0;
        }
    }
}
