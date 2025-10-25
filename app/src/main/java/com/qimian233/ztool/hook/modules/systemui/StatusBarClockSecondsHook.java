package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUI状态栏时钟秒显示Hook模块
 * 强制启用系统状态栏时钟的秒显示功能
 */
public class StatusBarClockSecondsHook extends BaseHookModule {

    private static final String CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock";

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            hookSystemUIClock(lpparam);
        }
    }

    private void hookSystemUIClock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 1: 在 Clock 对象创建时强制启用秒显示
            XposedHelpers.findAndHookMethod(CLOCK_CLASS, lpparam.classLoader,
                    "onAttachedToWindow", new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            forceEnableClockSeconds(param.thisObject);
                        }
                    });

            log("Successfully hooked Clock.onAttachedToWindow");
        } catch (Throwable t) {
            logError("Failed to hook Clock.onAttachedToWindow", t);
        }

        try {
            // Hook 2: 防止系统设置覆盖我们的修改
            XposedHelpers.findAndHookMethod(CLOCK_CLASS, lpparam.classLoader,
                    "onTuningChanged", String.class, String.class, new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if ("clock_seconds".equals(key)) {
                                // 强制覆盖设置为开启
                                param.args[1] = "1";
                                XposedHelpers.setBooleanField(param.thisObject, "mShowSeconds", true);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if ("clock_seconds".equals(key)) {
                                // 确保秒显示更新
                                XposedHelpers.callMethod(param.thisObject, "updateShowSeconds");
                            }
                        }
                    });

            log("Successfully hooked Clock.onTuningChanged");
        } catch (Throwable t) {
            logError("Failed to hook Clock.onTuningChanged", t);
        }

        try {
            // Hook 3: 直接修改 updateShowSeconds 方法
            XposedHelpers.findAndHookMethod(CLOCK_CLASS, lpparam.classLoader,
                    "updateShowSeconds", new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 强制启用秒显示
                            XposedHelpers.setBooleanField(param.thisObject, "mShowSeconds", true);
                        }
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
            // 设置秒显示标志
            XposedHelpers.setBooleanField(clockInstance, "mShowSeconds", true);

            // 确保秒更新处理器存在
            Object secondsHandler = XposedHelpers.getObjectField(clockInstance, "mSecondsHandler");
            if (secondsHandler == null) {
                ClassLoader cl = clockInstance.getClass().getClassLoader();
                Class<?> handlerClass = XposedHelpers.findClass("android.os.Handler", cl);
                Object newHandler = XposedHelpers.newInstance(handlerClass);
                XposedHelpers.setObjectField(clockInstance, "mSecondsHandler", newHandler);
            }

            // 触发秒显示更新
            XposedHelpers.callMethod(clockInstance, "updateShowSeconds");

            log("Force enabled clock seconds display");
        } catch (Throwable t) {
            logError("Force enable seconds failed", t);
        }
    }
}
