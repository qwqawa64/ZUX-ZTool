package com.qimian233.ztool.hook.modules.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationCenterTransparency extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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
                        if (viewObject instanceof View) {
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
                XC_MethodReplacement.DO_NOTHING);
        } catch (Throwable t) {
            logError("Failed to hook setNotificationPanelBlurBehind", t);
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
                            Object zoomOut = XposedHelpers.callMethod(result, "component2");
                            param.setResult(XposedHelpers.newInstance(
                                XposedHelpers.findClass("kotlin.Pair", classLoader),
                                0,
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
                        param.args[0] = 0.0f;
                        param.args[1] = false;
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
                        clearBackdropRenderEffect(param.thisObject);
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
}
