package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * SystemUI状态栏时钟秒显示Hook模块
 * 强制启用系统状态栏时钟的秒显示功能
 */
public class StatusBarClockSecondsHook extends BaseHookModule {

    private static final String CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock";

    public StatusBarClockSecondsHook() {}

    @Override
    public String getModuleName() {
        return "StatusBarDisplay_Seconds";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.systemui"
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.systemui".equals(packageName)) {
            hookSystemUIClock(classLoader);
        }
    }

    private void hookSystemUIClock(ClassLoader classLoader) {
        try {
            // Hook 1: 在 Clock 对象创建时强制启用秒显示
            Method onAttachedMethod = classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("onAttachedToWindow");
            hookWithId(onAttachedMethod, "on_attached", chain -> {
                Object result = chain.proceed();
                forceEnableClockSeconds(chain.getThisObject());
                return result;
            });

            log("Successfully hooked Clock.onAttachedToWindow");
        } catch (Throwable t) {
            logError("Failed to hook Clock.onAttachedToWindow", t);
        }

        try {
            // Hook 2: 防止系统设置覆盖我们的修改
            Method onTuningMethod = classLoader.loadClass(CLOCK_CLASS)
                    .getDeclaredMethod("onTuningChanged", String.class, String.class);
            hookWithId(onTuningMethod, "on_tuning", chain -> {
                String key = (String) chain.getArg(0);
                if ("clock_seconds".equals(key)) {
                    // 强制覆盖设置为开启
                    Class<?> clockCls = chain.getThisObject().getClass();
                    clockCls.getDeclaredField("mShowSeconds").setBoolean(chain.getThisObject(), true);
                    // 调用原始方法，但修改第二个参数为 "1"
                    Object result = chain.proceed(new Object[]{key, "1"});
                    // 确保秒显示更新
                    clockCls.getDeclaredMethod("updateShowSeconds").invoke(chain.getThisObject());
                    return result;
                }
                return chain.proceed();
            });

            log("Successfully hooked Clock.onTuningChanged");
        } catch (Throwable t) {
            logError("Failed to hook Clock.onTuningChanged", t);
        }

        try {
            // Hook 3: 直接修改 updateShowSeconds 方法
            Method updateMethod = classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateShowSeconds");
            hookWithId(updateMethod, "update", chain -> {
                // 强制启用秒显示
                chain.getThisObject().getClass().getDeclaredField("mShowSeconds")
                        .setBoolean(chain.getThisObject(), true);
                return chain.proceed();
            });

            log("Successfully hooked Clock.updateShowSeconds");
        } catch (Throwable t) {
            logError("Failed to hook Clock.updateShowSeconds", t);
        }
    }

    /**
     * 强制启用时钟秒显示功能
     */
    private void forceEnableClockSeconds(Object clockInstance) {
        try {
            Class<?> cl = clockInstance.getClass();
            // 设置秒显示标志
            cl.getDeclaredField("mShowSeconds").setBoolean(clockInstance, true);

            // 确保秒更新处理器存在
            java.lang.reflect.Field handlerField = cl.getDeclaredField("mSecondsHandler");
            handlerField.setAccessible(true);
            Object secondsHandler = handlerField.get(clockInstance);
            if (secondsHandler == null) {
                ClassLoader clLoader = clockInstance.getClass().getClassLoader();
                Class<?> handlerClass = clLoader.loadClass("android.os.Handler");
                Object newHandler = handlerClass.getDeclaredConstructor().newInstance();
                handlerField.set(clockInstance, newHandler);
            }

            // 触发秒显示更新
            cl.getDeclaredMethod("updateShowSeconds").invoke(clockInstance);

            log("Force enabled clock seconds display");
        } catch (Throwable t) {
            logError("Force enable seconds failed", t);
        }
    }
}
