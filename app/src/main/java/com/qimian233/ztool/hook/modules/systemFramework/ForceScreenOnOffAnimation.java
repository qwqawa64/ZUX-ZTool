package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceScreenOnOffAnimation extends BaseHookModule {
    private static final String DISPLAY_POWER_CONTROLLER =
            "com.android.server.display.DisplayPowerController";
    private static final String DISPLAY_POWER_CONTROLLER_INJECTOR =
            DISPLAY_POWER_CONTROLLER + "$Injector";
    private static long SCREEN_ON_ANIMATION_DURATION_MS = 400L;
    private static long SCREEN_OFF_ANIMATION_DURATION_MS = 250L;

    @Override
    public String getModuleName() {
        return "force_screen_on_off_animation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"android"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        this.updateAnimationDurationFromPrefs();
        log("Screen on animation duration (ms): " + SCREEN_ON_ANIMATION_DURATION_MS);
        log("Screen off animation duration (ms): " + SCREEN_OFF_ANIMATION_DURATION_MS);
        try {
            log("Executing hook for DisplayPowerController screen on/off animation...");
            XposedHelpers.findAndHookMethod(
                    DISPLAY_POWER_CONTROLLER_INJECTOR,
                    lpparam.classLoader,
                    "isColorFadeEnabled",
                    XC_MethodReplacement.returnConstant(true)
            );
            hookDisplayPowerControllerConstructor(lpparam.classLoader);
            hookDisplayPowerControllerInitialize(lpparam.classLoader);
            hookDisplayPowerControllerScreenOnAnimation(lpparam.classLoader);
        } catch (Exception e) {
            logError("Failed to hook DisplayPowerController: ", e);
        }
    }

    private void hookDisplayPowerControllerConstructor(ClassLoader classLoader) {
        XposedHelpers.findAndHookConstructor(
                DISPLAY_POWER_CONTROLLER,
                classLoader,
                XposedHelpers.findClass("android.content.Context", classLoader),
                XposedHelpers.findClass(DISPLAY_POWER_CONTROLLER_INJECTOR, classLoader),
                XposedHelpers.findClass(
                        "android.hardware.display.DisplayManagerInternal$DisplayPowerCallbacks",
                        classLoader),
                XposedHelpers.findClass("android.os.Handler", classLoader),
                XposedHelpers.findClass("android.hardware.SensorManager", classLoader),
                XposedHelpers.findClass("com.android.server.display.DisplayBlanker", classLoader),
                XposedHelpers.findClass("com.android.server.display.LogicalDisplay", classLoader),
                XposedHelpers.findClass("com.android.server.display.BrightnessTracker", classLoader),
                XposedHelpers.findClass("com.android.server.display.BrightnessSetting", classLoader),
                XposedHelpers.findClass("java.lang.Runnable", classLoader),
                XposedHelpers.findClass(
                        "com.android.server.display.HighBrightnessModeMetadata",
                        classLoader),
                boolean.class,
                XposedHelpers.findClass(
                        "com.android.server.display.feature.DisplayManagerFlags",
                        classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedHelpers.setBooleanField(param.thisObject, "mColorFadeEnabled", true);
                        XposedHelpers.setBooleanField(param.thisObject, "mColorFadeFadesConfig", true);
                        log("Forced DisplayPowerController color fade animation enabled.");
                    }
                }
        );
    }

    private void hookDisplayPowerControllerInitialize(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                DISPLAY_POWER_CONTROLLER,
                classLoader,
                "initialize",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        configureColorFadeAnimators(param.thisObject);
                    }
                }
        );
    }

    private void configureColorFadeAnimators(Object controller) {
        Object onAnimator = getObjectField(controller, "mColorFadeOnAnimator");
        Object offAnimator = getObjectField(controller, "mColorFadeOffAnimator");
        try {
            if (onAnimator != null) {
                XposedHelpers.callMethod(onAnimator, "setDuration",
                        SCREEN_ON_ANIMATION_DURATION_MS);
            }
            if (offAnimator != null) {
                XposedHelpers.callMethod(offAnimator, "setDuration",
                        SCREEN_OFF_ANIMATION_DURATION_MS);
            }
            //noinspection ConstantValue
            log("Configured color fade animator durations: on="
                    + getAnimatorDuration(onAnimator)
                    + ", off=" + getAnimatorDuration(offAnimator));
        } catch (Throwable t) {
            logError("Failed to configure color fade animator durations: ", t);
        }
    }

    private long getAnimatorDuration(Object animator) {
        if (animator == null) {
            return -1L;
        }
        try {
            Object result = XposedHelpers.callMethod(animator, "getDuration");
            return result instanceof Long ? (Long) result : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private Object getObjectField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hookDisplayPowerControllerScreenOnAnimation(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                DISPLAY_POWER_CONTROLLER,
                classLoader,
                "animateScreenStateChange",
                int.class,
                int.class,
                boolean.class,
                boolean.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (tryStartPreparedScreenOnAnimation(param.thisObject,
                                (Integer) param.args[0])) {
                            param.setResult(null);
                        }
                    }
                }
        );
    }

    private boolean tryStartPreparedScreenOnAnimation(Object controller, int targetState) {
        if (targetState != 2) {
            return false;
        }
        Object powerState = getObjectField(controller, "mPowerState");
        if (powerState == null
                || !getBooleanField(powerState, "mColorFadePrepared")
                || getFloatByMethod(powerState, "getColorFadeLevel", 1.0f) >= 1.0f) {
            return false;
        }
        Object onAnimator = getObjectField(controller, "mColorFadeOnAnimator");
        if (onAnimator == null) {
            return false;
        }

        try {
            XposedHelpers.callMethod(onAnimator, "cancel");
            XposedHelpers.callMethod(onAnimator, "setDuration", SCREEN_ON_ANIMATION_DURATION_MS);
            XposedHelpers.callMethod(onAnimator, "setFloatValues",
                    new float[] {getFloatByMethod(powerState, "getColorFadeLevel", 0.0f), 1.0f});
            XposedHelpers.callMethod(onAnimator, "start");
            log("Started prepared screen-on color fade animation.");
            return true;
        } catch (Throwable t) {
            logError("Failed to start prepared screen-on color fade animation: ", t);
            return false;
        }
    }

    private void updateAnimationDurationFromPrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        SCREEN_OFF_ANIMATION_DURATION_MS = prefs.getInt("screen_on_off_animation_ms", 400);
        SCREEN_ON_ANIMATION_DURATION_MS = prefs.getInt("screen_on_off_animation_ms", 400);
    }

    private boolean getBooleanField(Object object, String fieldName) {
        if (object == null) {
            return false;
        }
        try {
            return XposedHelpers.getBooleanField(object, fieldName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float getFloatByMethod(Object object, String methodName, float defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            return result instanceof Float ? (Float) result : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
