package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceScreenOnOffAnimation extends BaseHookModule {
    private static final String DISPLAY_POWER_CONTROLLER =
            "com.android.server.display.DisplayPowerController";
    private static final String DISPLAY_POWER_CONTROLLER_INJECTOR =
            DISPLAY_POWER_CONTROLLER + "$Injector";
    private static final String DISPLAY_STATE_CONTROLLER =
            "com.android.server.display.state.DisplayStateController";
    private static final String DISPLAY_POWER_STATE =
            "com.android.server.display.DisplayPowerState";
    private static final String COLOR_FADE =
            "com.android.server.display.ColorFade";

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

        try {
            log("Executing hook for DisplayPowerController screen on/off animation...");
            XposedHelpers.findAndHookMethod(
                    DISPLAY_POWER_CONTROLLER_INJECTOR,
                    lpparam.classLoader,
                    "isColorFadeEnabled",
                    XC_MethodReplacement.returnConstant(true)
            );
            hookDisplayPowerControllerConstructor(lpparam.classLoader);
            hookDiagnostics(lpparam.classLoader);
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

    private void hookDiagnostics(ClassLoader classLoader) {
        hookDisplayStateControllerDiagnostics(classLoader);
        hookDisplayPowerControllerDiagnostics(classLoader);
        hookDisplayPowerStateDiagnostics(classLoader);
        hookColorFadeDiagnostics(classLoader);
    }

    private void hookDisplayStateControllerDiagnostics(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                DISPLAY_STATE_CONTROLLER,
                classLoader,
                "shouldPerformScreenOffTransition",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("DisplayStateController.shouldPerformScreenOffTransition result="
                                + param.getResult()
                                + ", perform=" + getBooleanField(param.thisObject,
                                        "mPerformScreenOffTransition")
                                + ", skip=" + getBooleanField(param.thisObject,
                                        "mShouldSkipScreenOffTransition"));
                    }
                }
        );
    }

    private void hookDisplayPowerControllerDiagnostics(ClassLoader classLoader) {
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
                        log("DisplayPowerController.animateScreenStateChange before: targetState="
                                + param.args[0]
                                + ", reason=" + param.args[1]
                                + ", performOffTransition=" + param.args[2]
                                + ", skipBecauseOfProximity=" + param.args[3]
                                + ", specialOnAnimation=" + param.args[4]
                                + ", " + describeDisplayPowerController(param.thisObject));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("DisplayPowerController.animateScreenStateChange after: "
                                + describeDisplayPowerController(param.thisObject));
                    }
                }
        );
    }

    private void hookDisplayPowerStateDiagnostics(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                DISPLAY_POWER_STATE,
                classLoader,
                "prepareColorFade",
                XposedHelpers.findClass("android.content.Context", classLoader),
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("DisplayPowerState.prepareColorFade mode=" + param.args[1]
                                + ", result=" + param.getResult()
                                + ", " + describeDisplayPowerState(param.thisObject));
                    }
                }
        );
        XposedHelpers.findAndHookMethod(
                DISPLAY_POWER_STATE,
                classLoader,
                "setColorFadeLevel",
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("DisplayPowerState.setColorFadeLevel level=" + param.args[0]
                                + ", " + describeDisplayPowerState(param.thisObject));
                    }
                }
        );
    }

    private void hookColorFadeDiagnostics(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                COLOR_FADE,
                classLoader,
                "showSurface",
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("ColorFade.showSurface alpha=" + param.args[0]
                                + ", result=" + param.getResult()
                                + ", mode=" + getIntField(param.thisObject, "mMode")
                                + ", prepared=" + getBooleanField(param.thisObject, "mPrepared")
                                + ", visible=" + getBooleanField(param.thisObject, "mSurfaceVisible")
                                + ", surfaceAlpha=" + getFloatField(param.thisObject,
                                        "mSurfaceAlpha"));
                    }
                }
        );
    }

    private String describeDisplayPowerController(Object controller) {
        Object powerState = getObjectField(controller, "mPowerState");
        Object onAnimator = getObjectField(controller, "mColorFadeOnAnimator");
        Object offAnimator = getObjectField(controller, "mColorFadeOffAnimator");
        return "colorFadeEnabled=" + getBooleanField(controller, "mColorFadeEnabled")
                + ", colorFadeFades=" + getBooleanField(controller, "mColorFadeFadesConfig")
                + ", pendingScreenOff=" + getBooleanField(controller, "mPendingScreenOff")
                + ", powerRequest=" + getObjectField(controller, "mPowerRequest")
                + ", powerState={" + describeDisplayPowerState(powerState) + "}"
                + ", onAnimatorStarted=" + isAnimatorStarted(onAnimator)
                + ", offAnimatorStarted=" + isAnimatorStarted(offAnimator);
    }

    private String describeDisplayPowerState(Object powerState) {
        if (powerState == null) {
            return "null";
        }
        return "screenState=" + callIntMethod(powerState, "getScreenState")
                + ", colorFadeLevel=" + callFloatMethod(powerState, "getColorFadeLevel")
                + ", colorFadePrepared=" + getBooleanField(powerState, "mColorFadePrepared")
                + ", colorFadeReady=" + getBooleanField(powerState, "mColorFadeReady")
                + ", colorFadeDrawPending=" + getBooleanField(powerState,
                        "mColorFadeDrawPending")
                + ", screenReady=" + getBooleanField(powerState, "mScreenReady");
    }

    private boolean isAnimatorStarted(Object animator) {
        if (animator == null) {
            return false;
        }
        try {
            Object result = XposedHelpers.callMethod(animator, "isStarted");
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
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

    private int getIntField(Object object, String fieldName) {
        if (object == null) {
            return 0;
        }
        try {
            return XposedHelpers.getIntField(object, fieldName);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private float getFloatField(Object object, String fieldName) {
        if (object == null) {
            return 0.0f;
        }
        try {
            return XposedHelpers.getFloatField(object, fieldName);
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private int callIntMethod(Object object, String methodName) {
        if (object == null) {
            return 0;
        }
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            return result instanceof Integer ? (Integer) result : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private float callFloatMethod(Object object, String methodName) {
        if (object == null) {
            return 0.0f;
        }
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            return result instanceof Float ? (Float) result : 0.0f;
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }
}
