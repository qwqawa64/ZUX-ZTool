package com.qimian233.ztool.hook.modules.systemui.misc;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class NotificationCenterTransparency extends AppHookModule {
    private static final String KEY_BLUR_PERCENT = "notification_center_blur_percent";
    private static final int DEFAULT_BLUR_PERCENT = 0;
    private volatile int blurPercent = DEFAULT_BLUR_PERCENT;

    public NotificationCenterTransparency() {}

    @Override
    public String getModuleName() {
        return "notification_center_blur";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        updatePrefs();
        hookShadeRootViewFactory(classLoader);
        hookNotificationShadeBlur(classLoader);
        hookQuickSettingsBackdropBlur(classLoader);
    }

    private void hookShadeRootViewFactory(ClassLoader classLoader) {
        try {
            Method providesMethod = classLoader
                    .loadClass("com.android.systemui.shade.ShadeViewProviderModule_Companion_ProvidesWindowRootViewFactory")
                    .getDeclaredMethod("providesWindowRootView",
                            classLoader.loadClass("android.view.LayoutInflater"));
            hookWithId(providesMethod, "provides", chain -> {
                Object result = chain.proceed();
                if (result instanceof View && isBlurCleared()) {
                    clearBlurFromViewTree((View) result);
                }
                return result;
            });
        } catch (Throwable t) {
            logger.error("Failed to hook shade root view factory", t);
        }
    }

    private void hookNotificationShadeBlur(ClassLoader classLoader) {
        try {
            Method setBlurMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                    .getDeclaredMethod("setNotificationPanelBlurBehind");
            hookWithId(setBlurMethod, "set_blur", chain -> {
                if (isBlurCleared()) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            logger.error("Failed to hook setNotificationPanelBlurBehind()", t);
        }

        try {
            Method setBlurIntMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                    .getDeclaredMethod("setNotificationPanelBlurBehind", int.class);
            hookWithId(setBlurIntMethod, "set_blur_int", chain -> chain.proceed(new Object[]{scaleBlur((int) chain.getArg(0))}));
        } catch (Throwable t) {
            logger.error("Failed to hook setNotificationPanelBlurBehind(int)", t);
        }

        try {
            Method computeMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                    .getDeclaredMethod("computeBlurAndZoomOut");
            hookWithId(computeMethod, "compute", chain -> {
                Object result = chain.proceed();
                if (result != null) {
                    Class<?> pairClass = classLoader.loadClass("kotlin.Pair");
                    Object blur = pairClass.getDeclaredMethod("component1").invoke(result);
                    Object zoomOut = pairClass.getDeclaredMethod("component2").invoke(result);
                    return pairClass.getDeclaredConstructor(Object.class, Object.class)
                            .newInstance(scaleBlur(blur), zoomOut);
                }
                return null;
            });
        } catch (Throwable t) {
            logger.error("Failed to hook NotificationShadeDepthController.computeBlurAndZoomOut", t);
        }

        try {
            Method animateBlurMethod = classLoader
                    .loadClass("com.android.systemui.statusbar.NotificationShadeDepthController")
                    .getDeclaredMethod("animateBlur", float.class, boolean.class);
            hookWithId(animateBlurMethod, "animate_blur", chain -> {
                float newBlur = scaleBlur((float) chain.getArg(0));
                boolean newAnimate = (boolean) chain.getArg(1);
                if (isBlurCleared()) {
                    newAnimate = false;
                }
                return chain.proceed(new Object[]{newBlur, newAnimate});
            });
        } catch (Throwable t) {
            logger.error("Failed to hook NotificationShadeDepthController.animateBlur", t);
        }
    }

    private void hookQuickSettingsBackdropBlur(ClassLoader classLoader) {
        try {
            Method updateExpansionMethod = classLoader
                    .loadClass("com.android.systemui.shade.QuickSettingsControllerImpl")
                    .getDeclaredMethod("updateExpansion");
            hookWithId(updateExpansionMethod, "update_expansion", chain -> {
                Object result = chain.proceed();
                if (isBlurCleared()) {
                    clearBackdropRenderEffect(chain.getThisObject());
                }
                return result;
            });
        } catch (Throwable t) {
            logger.error("Failed to hook QuickSettingsControllerImpl.updateExpansion", t);
        }
    }

    private void clearBackdropRenderEffect(Object quickSettingsController) {
        try {
            Class<?> cl = quickSettingsController.getClass();
            Object zuiCore = cl.getDeclaredField("mZuiCoreImpl").get(quickSettingsController);
            Object backdrop = zuiCore.getClass().getDeclaredField("backDropView").get(zuiCore);
            if (backdrop instanceof View) {
                ((View) backdrop).setRenderEffect(null);
            }
        } catch (Throwable t) {
            logger.error("Failed to clear QS backdrop render effect", t);
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

    private void updatePrefs() {
        try {
            blurPercent = this.xposed.getRemotePreferences("xposed_module_config").getInt(KEY_BLUR_PERCENT, DEFAULT_BLUR_PERCENT);
        } catch (Throwable t) {
            blurPercent = DEFAULT_BLUR_PERCENT;
        }
        if (blurPercent < 0) {
            blurPercent = 0;
        } else if (blurPercent > 100) {
            blurPercent = 100;
        }
    }

    private boolean isBlurCleared() {
        updatePrefs();
        return blurPercent <= 0;
    }

    private int scaleBlur(int blur) {
        updatePrefs();
        return Math.round(blur * (blurPercent / 100.0f));
    }

    private float scaleBlur(float blur) {
        updatePrefs();
        return blur * (blurPercent / 100.0f);
    }

    private Object scaleBlur(Object blur) {
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
