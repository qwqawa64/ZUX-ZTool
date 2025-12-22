package com.qimian233.ztool.hook.modules.systemui;

import android.os.Build;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.lang.invoke.MethodHandle;
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
        if (Build.VERSION.SDK_INT != 35 && Build.VERSION.SDK_INT != 36) {return;}
        log("Loading module NativeNotificationIcon.");
        handleLoadSystemUi(lpparam);
    }

    private final ThreadLocal<Boolean> isCtsMode = ThreadLocal.withInitial(() -> null);

    public void handleLoadSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        hookXSystemUtil(lpparam); // Hook 1
        hookNotificationShelf(lpparam); // Hook 2
        // Hook 3
        if (Build.VERSION.SDK_INT >= 36) {
            hookQSUtil(lpparam); // API 36+
        } else {
            hookNotificationListener(lpparam); // API 35-
        }
        // Hook 4
        if (Build.VERSION.SDK_INT >= 36) {
            hookNewPathClasses(lpparam); // API 36+
        } else {
            hookOldPathClasses(lpparam); // API 35-
        }
        hookNotificationHeaderView(lpparam); // Hook 5
        hookNotificationBuilder(lpparam); // Hook 6
    }

    private void hookXSystemUtil(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking com.android.systemui.util.XSystemUtil...");
            XposedHelpers.findAndHookMethod("com.android.systemui.util.XSystemUtil",
                    lpparam.classLoader,
                    "isCTSGTSTest",
                    new XC_MethodHook() {
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
    }

    private void hookNotificationShelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try{
            log("Hooking com.android.systemui.statusbar.NotificationShelf");
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.NotificationShelf",
                    lpparam.classLoader,
                    "updateResources$5",
                    new XC_MethodHook() {
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
    }

    private void hookQSUtil(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking com.android.systemui.util.QSUtil");
            XposedHelpers.findAndHookMethod("com.android.systemui.util.QSUtil",
                    lpparam.classLoader,
                    "replaceTheSmallIcon",
                    Context.class, StatusBarNotification.class,
                    XC_MethodReplacement.returnConstant(null));
            log("[NativeNotificationIcon] Successfully hooked com.android.systemui.util.QSUtil [3-1/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.util.QSUtil",e);
        }
    }

    private void hookNotificationListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.NotificationListener",
                    lpparam.classLoader,
                    "replaceTheSmallIcon",
                    StatusBarNotification.class,
                    XC_MethodReplacement.returnConstant(null));
            log("[NativeNotificationIcon] Fallback: hooked NotificationListener.replaceTheSmallIcon [3-2/6]");
        } catch (Exception e) {
            logError("Failed to hook replaceTheSmallIcon", e);
        }
    }

    private void hookNewPathClasses (XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Finding new path classes...");
            final var newWrapperClass = XposedHelpers.findClass("com.android.systemui.notificationlist.notification.wrapper.NotificationHeaderViewWrapper", lpparam.classLoader);
            final var newMIconField = XposedHelpers.findField(newWrapperClass, "mIcon");
            newMIconField.setAccessible(true);
            final MethodHandle newGetIcon = MethodHandles.lookup().unreflectGetter(newMIconField);
            final var newRowClass = XposedHelpers.findClass("com.android.systemui.notificationlist.view.NotificationRowView", lpparam.classLoader);
            log("New path classes found. Hooking...");

            XposedHelpers.findAndHookMethod(newWrapperClass, "onContentUpdated", newRowClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var iconview = (ImageView) newGetIcon.invoke(param.thisObject);
                    if (iconview == null) return;

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
            });
            log("Successfully hooked new path NotificationHeaderViewWrapper [4-1/6]");
        } catch (Exception e) {
            logError("Failed to hook new path NotificationHeaderViewWrapper",e);
        }
    }

    private void hookOldPathClasses (XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Finding old path classes...");
            final var notificationHeaderViewWrapper_class = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper", lpparam.classLoader);
            final var notificationHeaderViewWrapper_mIcon = XposedHelpers.findField(notificationHeaderViewWrapper_class, "mIcon");
            notificationHeaderViewWrapper_mIcon.setAccessible(true);
            final var getIcon = MethodHandles.lookup().unreflectGetter(notificationHeaderViewWrapper_mIcon);
            final var expandableNotificationRow_class = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader);
            log("Old path classes found. Hooking...");

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
            });
            log("Successfully hooked com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper [4-2/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper",e);
        }
    }

    private void hookNotificationHeaderView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking com.android.systemui.notificationlist.view.NotificationHeaderView");
            // always true for ROW
            XposedHelpers.findAndHookMethod("com.android.systemui.notificationlist.view.NotificationHeaderView", lpparam.classLoader, "shouldShowIconBackground", XC_MethodReplacement.returnConstant(true));
            log("Successfully hooked com.android.systemui.notificationlist.view.NotificationHeaderView [5/6]");
        }catch (Exception e) {
            logError("Failed to hook com.android.systemui.notificationlist.view.NotificationHeaderView",e);
        }
    }

    private void hookNotificationBuilder(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking android.app.Notification$Builder");
            // always use circle template for android.app.Notification$Builder#get*Resource()
            XposedHelpers.findAndHookMethod("android.app.Notification$Builder", lpparam.classLoader, "isCtsGtsTest", XC_MethodReplacement.returnConstant(true));
            log("Successfully hooked android.app.Notification$Builder [6/6]");
        }catch (Exception e) {
            logError("Failed to hook android.app.Notification$Builder",e);
        }
    }
}