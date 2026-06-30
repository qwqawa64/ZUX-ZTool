package com.qimian233.ztool.hook.modules.systemui;

import android.os.Build;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Objects;

public class NativeNotificationIcon extends BaseHookModule {

    public NativeNotificationIcon() {}

    public String getModuleName() { return "NativeNotificationIcon"; }
    public String[] getTargetPackages() { return new String[] { "com.android.systemui" }; }

    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (Build.VERSION.SDK_INT != 35 && Build.VERSION.SDK_INT != 36) {return;}
        log("Loading module NativeNotificationIcon.");
        handleLoadSystemUi(classLoader);
    }

    private final ThreadLocal<Boolean> isCtsMode = ThreadLocal.withInitial(() -> null);

    public void handleLoadSystemUi(ClassLoader classLoader) {
        hookXSystemUtil(classLoader); // Hook 1
        hookNotificationShelf(classLoader); // Hook 2
        // Hook 3
        if (Build.VERSION.SDK_INT >= 36) {
            hookQSUtil(classLoader); // API 36+
        } else {
            hookNotificationListener(classLoader); // API 35-
        }
        // Hook 4
        if (Build.VERSION.SDK_INT >= 36) {
            hookNewPathClasses(classLoader); // API 36+
        } else {
            hookOldPathClasses(classLoader); // API 35-
        }
        hookNotificationHeaderView(classLoader); // Hook 5
        hookNotificationBuilder(classLoader); // Hook 6
    }

    private void hookXSystemUtil(ClassLoader classLoader) {
        try {
            log("Hooking com.android.systemui.util.XSystemUtil...");
            Method isCTSGTSTestMethod = classLoader
                    .loadClass("com.android.systemui.util.XSystemUtil")
                    .getDeclaredMethod("isCTSGTSTest");
            this.xposed.hook(isCTSGTSTestMethod).intercept(chain -> {
                var mode = isCtsMode.get();
                if (mode != null) {
                    return mode;
                }
                return chain.proceed();
            });
            log("Successfully hooked com.android.systemui.util.XSystemUtil. [1/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.util.XSystemUtil.", e);
        }
    }

    private void hookNotificationShelf(ClassLoader classLoader) {
        try {
            log("Hooking com.android.systemui.statusbar.NotificationShelf");
            Method updateMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationShelf")
                    .getDeclaredMethod("updateResources$5");
            this.xposed.hook(updateMethod).intercept(chain -> {
                isCtsMode.set(true);
                Object result = chain.proceed();
                isCtsMode.remove();
                return result;
            });
            log("Successfully hooked com.android.systemui.statusbar.NotificationShelf [2/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.NotificationShelf", e);
        }
    }

    private void hookQSUtil(ClassLoader classLoader) {
        try {
            log("Hooking com.android.systemui.util.QSUtil");
            Method replaceMethod = classLoader
                    .loadClass("com.android.systemui.util.QSUtil")
                    .getDeclaredMethod("replaceTheSmallIcon", Context.class, StatusBarNotification.class);
            this.xposed.hook(replaceMethod).intercept(chain -> null);
            log("[NativeNotificationIcon] Successfully hooked com.android.systemui.util.QSUtil [3-1/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.util.QSUtil", e);
        }
    }

    private void hookNotificationListener(ClassLoader classLoader) {
        try {
            Method replaceMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationListener")
                    .getDeclaredMethod("replaceTheSmallIcon", StatusBarNotification.class);
            this.xposed.hook(replaceMethod).intercept(chain -> null);
            log("[NativeNotificationIcon] Fallback: hooked NotificationListener.replaceTheSmallIcon [3-2/6]");
        } catch (Exception e) {
            logError("Failed to hook replaceTheSmallIcon", e);
        }
    }

    private void hookNewPathClasses(ClassLoader classLoader) {
        try {
            log("Finding new path classes...");
            final var newWrapperClass = classLoader.loadClass(
                    "com.android.systemui.notificationlist.notification.wrapper.NotificationHeaderViewWrapper");
            final var newMIconField = newWrapperClass.getDeclaredField("mIcon");
            newMIconField.setAccessible(true);
            final MethodHandle newGetIcon = MethodHandles.lookup().unreflectGetter(newMIconField);
            final var newRowClass = classLoader.loadClass(
                    "com.android.systemui.notificationlist.view.NotificationRowView");
            log("New path classes found. Hooking...");

            Method onContentUpdatedMethod = newWrapperClass.getDeclaredMethod("onContentUpdated", newRowClass);
            this.xposed.hook(onContentUpdatedMethod).intercept(chain -> {
                Object result = chain.proceed();
                var iconview = (ImageView) newGetIcon.invoke(chain.getThisObject());
                if (iconview == null) return result;

                final int KEY_SIZE_UNFUCKED = 1145141919;
                if (Objects.equals(iconview.getTag(KEY_SIZE_UNFUCKED), Boolean.TRUE)) {
                    return result;
                }

                var lp = iconview.getLayoutParams();
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    // AOSP notification_icon_circle_size: 24dp
                    var dm = iconview.getContext().getResources().getDisplayMetrics();
                    var diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, dm);
                    lp.width = Math.round(diameter);
                    lp.height = Math.round(diameter);
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.round(
                                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, dm)));
                    }
                    iconview.requestLayout();
                }
                iconview.setTag(KEY_SIZE_UNFUCKED, Boolean.TRUE);
                return result;
            });
            log("Successfully hooked new path NotificationHeaderViewWrapper [4-1/6]");
        } catch (Exception e) {
            logError("Failed to hook new path NotificationHeaderViewWrapper", e);
        }
    }

    private void hookOldPathClasses(ClassLoader classLoader) {
        try {
            log("Finding old path classes...");
            final var notificationHeaderViewWrapper_class = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper");
            final var notificationHeaderViewWrapper_mIcon = notificationHeaderViewWrapper_class.getDeclaredField("mIcon");
            notificationHeaderViewWrapper_mIcon.setAccessible(true);
            final var getIcon = MethodHandles.lookup().unreflectGetter(notificationHeaderViewWrapper_mIcon);
            final var expandableNotificationRow_class = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            log("Old path classes found. Hooking...");

            Method onContentUpdatedMethod = notificationHeaderViewWrapper_class.getDeclaredMethod(
                    "onContentUpdated", expandableNotificationRow_class);
            this.xposed.hook(onContentUpdatedMethod).intercept(chain -> {
                Object result = chain.proceed();
                var iconview = (ImageView) getIcon.invoke(chain.getThisObject());
                final int KEY_SIZE_UNFUCKED = 1145141919;
                if (Objects.equals(iconview.getTag(KEY_SIZE_UNFUCKED), Boolean.TRUE)) {
                    return result;
                }
                var lp = iconview.getLayoutParams();
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    // AOSP notification_icon_circle_size: 24dp
                    var dm = iconview.getContext().getResources().getDisplayMetrics();
                    var diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, dm);
                    lp.width = Math.round(diameter);
                    lp.height = Math.round(diameter);
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.round(
                                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, dm)));
                    }
                    iconview.requestLayout();
                }
                iconview.setTag(KEY_SIZE_UNFUCKED, Boolean.TRUE);
                return result;
            });
            log("Successfully hooked com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper [4-2/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper", e);
        }
    }

    private void hookNotificationHeaderView(ClassLoader classLoader) {
        try {
            log("Hooking com.android.systemui.notificationlist.view.NotificationHeaderView");
            // always true for ROW
            Method shouldShowMethod = classLoader
                    .loadClass("com.android.systemui.notificationlist.view.NotificationHeaderView")
                    .getDeclaredMethod("shouldShowIconBackground");
            this.xposed.hook(shouldShowMethod).intercept(chain -> true);
            log("Successfully hooked com.android.systemui.notificationlist.view.NotificationHeaderView [5/6]");
        } catch (Exception e) {
            logError("Failed to hook com.android.systemui.notificationlist.view.NotificationHeaderView", e);
        }
    }

    private void hookNotificationBuilder(ClassLoader classLoader) {
        try {
            log("Hooking android.app.Notification$Builder");
            // always use circle template for android.app.Notification$Builder#get*Resource()
            Method isCtsMethod = classLoader
                    .loadClass("android.app.Notification$Builder")
                    .getDeclaredMethod("isCtsGtsTest");
            this.xposed.hook(isCtsMethod).intercept(chain -> true);
            log("Successfully hooked android.app.Notification$Builder [6/6]");
        } catch (Exception e) {
            logError("Failed to hook android.app.Notification$Builder", e);
        }
    }
}
