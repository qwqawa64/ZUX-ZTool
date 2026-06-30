package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ForceScreenOnOffAnimation extends BaseHookModule {
    private static final String DISPLAY_POWER_CONTROLLER =
            "com.android.server.display.DisplayPowerController";
    private static final String DISPLAY_POWER_CONTROLLER_INJECTOR =
            DISPLAY_POWER_CONTROLLER + "$Injector";
    private static long SCREEN_ON_ANIMATION_DURATION_MS = 400L;
    private static long SCREEN_OFF_ANIMATION_DURATION_MS = 250L;

    public ForceScreenOnOffAnimation() {}

    @Override
    public String getModuleName() {
        return "force_screen_on_off_animation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        this.updateAnimationDurationFromPrefs();
        log("Screen on animation duration (ms): " + SCREEN_ON_ANIMATION_DURATION_MS);
        log("Screen off animation duration (ms): " + SCREEN_OFF_ANIMATION_DURATION_MS);
        try {
            log("Executing hook for DisplayPowerController screen on/off animation...");
            Method isColorFadeEnabledMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER_INJECTOR)
                    .getDeclaredMethod("isColorFadeEnabled");
            this.xposed.hook(isColorFadeEnabledMethod).intercept(chain -> true);
            hookDisplayPowerControllerConstructor(classLoader);
            hookDisplayPowerControllerInitialize(classLoader);
            hookDisplayPowerControllerScreenOnAnimation(classLoader);
        } catch (Exception e) {
            logError("Failed to hook DisplayPowerController: ", e);
        }
    }

    private void hookDisplayPowerControllerConstructor(ClassLoader classLoader) {
        try {
            Constructor<?> constructor = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                    .getDeclaredConstructor(
                            classLoader.loadClass("android.content.Context"),
                            classLoader.loadClass(DISPLAY_POWER_CONTROLLER_INJECTOR),
                            classLoader.loadClass(
                                    "android.hardware.display.DisplayManagerInternal$DisplayPowerCallbacks"),
                            classLoader.loadClass("android.os.Handler"),
                            classLoader.loadClass("android.hardware.SensorManager"),
                            classLoader.loadClass("com.android.server.display.DisplayBlanker"),
                            classLoader.loadClass("com.android.server.display.LogicalDisplay"),
                            classLoader.loadClass("com.android.server.display.BrightnessTracker"),
                            classLoader.loadClass("com.android.server.display.BrightnessSetting"),
                            classLoader.loadClass("java.lang.Runnable"),
                            classLoader.loadClass(
                                    "com.android.server.display.HighBrightnessModeMetadata"),
                            boolean.class,
                            classLoader.loadClass(
                                    "com.android.server.display.feature.DisplayManagerFlags")
                    );
            this.xposed.hook(constructor).intercept(chain -> {
                Object result = chain.proceed();
                Object thisObject = chain.getThisObject();
                safeSetBooleanField(thisObject, "mColorFadeEnabled", true);
                safeSetBooleanField(thisObject, "mColorFadeFadesConfig", true);
                log("Forced DisplayPowerController color fade animation enabled.");
                return result;
            });
        } catch (Exception e) {
            logError("Failed to hook DisplayPowerController constructor", e);
        }
    }

    private void hookDisplayPowerControllerInitialize(ClassLoader classLoader) {
        try {
            Method initializeMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                    .getDeclaredMethod("initialize", int.class);
            this.xposed.hook(initializeMethod).intercept(chain -> {
                Object result = chain.proceed();
                configureColorFadeAnimators(chain.getThisObject());
                return result;
            });
        } catch (Exception e) {
            logError("Failed to hook DisplayPowerController.initialize", e);
        }
    }

    private void configureColorFadeAnimators(Object controller) {
        Object onAnimator = safeGetObjectField(controller, "mColorFadeOnAnimator");
        Object offAnimator = safeGetObjectField(controller, "mColorFadeOffAnimator");
        try {
            if (onAnimator != null) {
                safeCallMethod(onAnimator, "setDuration",
                        SCREEN_ON_ANIMATION_DURATION_MS);
            }
            if (offAnimator != null) {
                safeCallMethod(offAnimator, "setDuration",
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
            Object result = safeCallMethod(animator, "getDuration");
            return result instanceof Long ? (Long) result : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private Object safeGetObjectField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            Field field = safeFindField(object.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(object);
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void safeSetBooleanField(Object object, String fieldName, boolean value) {
        if (object == null) return;
        try {
            Field field = safeFindField(object.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.setBoolean(object, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookDisplayPowerControllerScreenOnAnimation(ClassLoader classLoader) {
        try {
            Method animateMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                    .getDeclaredMethod("animateScreenStateChange",
                            int.class, int.class, boolean.class, boolean.class, boolean.class);
            this.xposed.hook(animateMethod).intercept(chain -> {
                if (tryStartPreparedScreenOnAnimation(chain.getThisObject(),
                        (Integer) chain.getArg(0))) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Exception e) {
            logError("Failed to hook animateScreenStateChange", e);
        }
    }

    private boolean tryStartPreparedScreenOnAnimation(Object controller, int targetState) {
        if (targetState != 2) {
            return false;
        }
        Object powerState = safeGetObjectField(controller, "mPowerState");
        if (powerState == null
                || !safeGetBooleanField(powerState, "mColorFadePrepared")
                || getFloatByMethod(powerState, "getColorFadeLevel", 1.0f) >= 1.0f) {
            return false;
        }
        Object onAnimator = safeGetObjectField(controller, "mColorFadeOnAnimator");
        if (onAnimator == null) {
            return false;
        }

        try {
            safeCallMethod(onAnimator, "cancel");
            safeCallMethod(onAnimator, "setDuration", SCREEN_ON_ANIMATION_DURATION_MS);
            safeCallMethod(onAnimator, "setFloatValues",
                    new float[] {getFloatByMethod(powerState, "getColorFadeLevel", 0.0f), 1.0f});
            safeCallMethod(onAnimator, "start");
            log("Started prepared screen-on color fade animation.");
            return true;
        } catch (Throwable t) {
            logError("Failed to start prepared screen-on color fade animation: ", t);
            return false;
        }
    }

    private void updateAnimationDurationFromPrefs() {
        try {
            SCREEN_OFF_ANIMATION_DURATION_MS = this.xposed.getRemotePreferences("xposed_module_config").getInt("screen_on_off_animation_ms", 400);
        } catch (Throwable t) {
            SCREEN_OFF_ANIMATION_DURATION_MS = 400;
        }
        try {
            SCREEN_ON_ANIMATION_DURATION_MS = this.xposed.getRemotePreferences("xposed_module_config").getInt("screen_on_off_animation_ms", 400);
        } catch (Throwable t) {
            SCREEN_ON_ANIMATION_DURATION_MS = 400;
        }
    }

    private boolean safeGetBooleanField(Object object, String fieldName) {
        if (object == null) {
            return false;
        }
        try {
            Field field = safeFindField(object.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.getBoolean(object);
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float getFloatByMethod(Object object, String methodName, float defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        try {
            Object result = safeCallMethod(object, methodName);
            return result instanceof Float ? (Float) result : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    // ── reflection helpers ──

    private static Field safeFindField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object safeCallMethod(Object obj, String methodName, Object... args) {
        Class<?> clazz = obj.getClass();
        Method method = safeFindMethod(clazz, methodName, args);
        if (method != null) {
            method.setAccessible(true);
            try {
                return method.invoke(obj, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "." + methodName);
    }

    private static Method safeFindMethod(Class<?> clazz, String name, Object... args) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name) && parameterTypesMatch(m.getParameterTypes(), args)) {
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean parameterTypesMatch(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) return false;
        for (int i = 0; i < paramTypes.length; i++) {
            if (args[i] == null) continue;
            Class<?> paramType = paramTypes[i];
            if (paramType.isPrimitive()) {
                paramType = boxed(paramType);
            }
            if (!paramType.isAssignableFrom(args[i].getClass())) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        if (primitive == short.class) return Short.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        return primitive;
    }
}
