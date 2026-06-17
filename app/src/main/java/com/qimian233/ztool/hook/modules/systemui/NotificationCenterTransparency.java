package com.qimian233.ztool.hook.modules.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationCenterTransparency extends BaseHookModule {
    private static final String KEY_BLUR_PERCENT = "notification_center_blur_percent";
    private static final int DEFAULT_BLUR_PERCENT = 0;
    private static volatile int blurPercent = DEFAULT_BLUR_PERCENT;

    @Override
    public String getModuleName() {
        return "notification_center_blur";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        updatePrefs();
        hookShadeRootViewFactory(lpparam.classLoader);
        hookNotificationShadeBlur(lpparam.classLoader);
        hookQuickSettingsBackdropBlur(lpparam.classLoader);
    }

    private void hookShadeRootViewFactory(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.shade.ShadeViewProviderModule_Companion_ProvidesWindowRootViewFactory",
                classLoader,
                "providesWindowRootView",
                "android.view.LayoutInflater", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object viewObject = param.getResult();
                        if (viewObject instanceof View && isBlurCleared()) {
                            clearBlurFromViewTree((View) viewObject);
                        }
                    }
                });
        } catch (Throwable t) {
            logError("Failed to hook shade root view factory", t);
        }
    }

    private void hookNotificationShadeBlur(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "setNotificationPanelBlurBehind",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isBlurCleared()) {
                            param.setResult(null);
                        }
                    }
                });
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to hook setNotificationPanelBlurBehind()", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "setNotificationPanelBlurBehind",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = scaleBlur((int) param.args[0]);
                    }
                });
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to hook setNotificationPanelBlurBehind(int)", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "computeBlurAndZoomOut",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (result != null) {
                            Object blur = XposedHelpers.callMethod(result, "component1");
                            Object zoomOut = XposedHelpers.callMethod(result, "component2");
                            param.setResult(XposedHelpers.newInstance(
                                XposedHelpers.findClass("kotlin.Pair", classLoader),
                                scaleBlur(blur),
                                zoomOut));
                        }
                    }
                });
        } catch (Throwable t) {
            logError("Failed to hook NotificationShadeDepthController.computeBlurAndZoomOut", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "animateBlur",
                float.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = scaleBlur((float) param.args[0]);
                        if (isBlurCleared()) {
                            param.args[1] = false;
                        }
                    }
                });
        } catch (Throwable t) {
            logError("Failed to hook NotificationShadeDepthController.animateBlur", t);
        }
    }

    private void hookQuickSettingsBackdropBlur(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.shade.QuickSettingsControllerImpl",
                classLoader,
                "updateExpansion",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (isBlurCleared()) {
                            clearBackdropRenderEffect(param.thisObject);
                        }
                    }
                });
        } catch (Throwable t) {
            logError("Failed to hook QuickSettingsControllerImpl.updateExpansion", t);
        }
    }

    private void clearBackdropRenderEffect(Object quickSettingsController) {
        try {
            Object zuiCore = XposedHelpers.getObjectField(quickSettingsController, "mZuiCoreImpl");
            Object backdrop = XposedHelpers.getObjectField(zuiCore, "backDropView");
            if (backdrop instanceof View) {
                ((View) backdrop).setRenderEffect(null);
            }
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to clear QS backdrop render effect", t);
        }
    }

    private void clearBlurFromViewTree(View view) {
        view.setRenderEffect(null);

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                clearBlurFromViewTree(viewGroup.getChildAt(i));
            }
        }
    }

    private static void updatePrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        blurPercent = prefs.getInt(KEY_BLUR_PERCENT, DEFAULT_BLUR_PERCENT);
        if (blurPercent < 0) {
            blurPercent = 0;
        } else if (blurPercent > 100) {
            blurPercent = 100;
        }
    }

    private static boolean isBlurCleared() {
        updatePrefs();
        return blurPercent <= 0;
    }

    private static int scaleBlur(int blur) {
        updatePrefs();
        return Math.round(blur * (blurPercent / 100.0f));
    }

    private static float scaleBlur(float blur) {
        updatePrefs();
        return blur * (blurPercent / 100.0f);
    }

    private static Object scaleBlur(Object blur) {
        if (blur instanceof Integer) {
            return scaleBlur((int) blur);
        }
        if (blur instanceof Float) {
            return scaleBlur((float) blur);
        }
        if (blur instanceof Number) {
            updatePrefs();
            return Math.round(((Number) blur).floatValue() * (blurPercent / 100.0f));
        }
        return blur;
    }
}
