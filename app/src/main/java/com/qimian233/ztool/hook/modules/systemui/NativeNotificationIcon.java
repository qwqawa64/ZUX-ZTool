package com.qimian233.ztool.hook.modules.systemui;

import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NativeNotificationIcon extends BaseHookModule {
    public String getModuleName() { return "NativeNotificationIcon"; }
    public String[] getTargetPackages() { return new String[] { "com.android.systemui" }; }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Build.VERSION.SDK_INT != 35) {
            return;
        }
        log("Loading module NativeNotificationIcon.");
        new NativeNotificationIcon().handleLoadSystemUi(lpparam);
    }

    private final ThreadLocal<Boolean> isCtsMode = ThreadLocal.withInitial(() -> null);

    public void handleLoadSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking com.android.systemui.util.XSystemUtil...");
            XposedHelpers.findAndHookMethod("com.android.systemui.util.XSystemUtil", lpparam.classLoader, "isCTSGTSTest", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    var mode = isCtsMode.get();
                    if (mode != null) {
                        param.setResult(mode);
                    }
                }
            });
            log("Successfully hooked com.android.systemui.util.XSystemUtil. [1/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.util.XSystemUtil.",e);
        }

        try{
            // use grayscale icons for notification shelf (collapsed notification icons)
            log("Hooking com.android.systemui.statusbar.NotificationShelf");
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.NotificationShelf", lpparam.classLoader, "updateResources$5", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    isCtsMode.set(true);
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    isCtsMode.remove();
                }
            });
            log("Successfully hooked com.android.systemui.statusbar.NotificationShelf [2/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.NotificationShelf",e);
        }

        try {
            log("Hooking com.android.systemui.statusbar.NotificationListener");
            // don't replace the small icon with app icon
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.NotificationListener", lpparam.classLoader, "replaceTheSmallIcon", StatusBarNotification.class, XC_MethodReplacement.returnConstant(null));
            log("[NativeNotificationIcon] Successfully hooked com.android.systemui.statusbar.NotificationListener [3/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.NotificationListener",e);
        }

        try {
            log("Finding classes...");
            final var notificationHeaderViewWrapper_class = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper", lpparam.classLoader);
            final var notificationHeaderViewWrapper_mIcon = XposedHelpers.findField(notificationHeaderViewWrapper_class, "mIcon");
            final var getIcon = MethodHandles.lookup().unreflectGetter(notificationHeaderViewWrapper_mIcon);
            final var expandableNotificationRow_class = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader);
            log("Classes found. Hooking...");
            XposedHelpers.findAndHookMethod(notificationHeaderViewWrapper_class, "onContentUpdated", expandableNotificationRow_class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var iconview = (ImageView) getIcon.invoke(param.thisObject);
                    final int KEY_SIZE_UNFUCKED = 1145141919;
                    if (Objects.equals(iconview.getTag(KEY_SIZE_UNFUCKED), Boolean.TRUE)) {
                        return;
                    }
                    var lp = iconview.getLayoutParams();
                    if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                        // AOSP notification_icon_circle_size: 24dp
                        var dm = iconview.getContext().getResources().getDisplayMetrics();
                        var diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, dm);
                        lp.width = Math.round(diameter);
                        lp.height = Math.round(diameter);
                        if (lp instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, dm)));
                        }
                        iconview.requestLayout();
                    }
                    iconview.setTag(KEY_SIZE_UNFUCKED, Boolean.TRUE);
                }
            }
            );
            log("Successfully hooked com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper [4/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper",e);
        }

        try {
            log("Hooking com.android.systemui.notificationlist.view.NotificationHeaderView");
            // always true for ROW
            XposedHelpers.findAndHookMethod("com.android.systemui.notificationlist.view.NotificationHeaderView", lpparam.classLoader, "shouldShowIconBackground", XC_MethodReplacement.returnConstant(true));
            log("Successfully hooked com.android.systemui.notificationlist.view.NotificationHeaderView [5/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.notificationlist.view.NotificationHeaderView",e);
        }

        try {
            log("Hooking android.app.Notification$Builder");
            // always use circle template for android.app.Notification$Builder#get*Resource()
            XposedHelpers.findAndHookMethod("android.app.Notification$Builder", lpparam.classLoader, "isCtsGtsTest", XC_MethodReplacement.returnConstant(true));
            log("Successfully hooked android.app.Notification$Builder [6/6]");
        }catch (Exception e) {
            logError("Failed to hook android.app.Notification$Builder",e);
        }
        
        log("Hook is successful. [OK]");
    }
}
