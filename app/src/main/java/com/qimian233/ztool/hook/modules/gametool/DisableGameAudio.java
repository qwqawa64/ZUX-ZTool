package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用游戏音频优化Hook模块
 * 拦截系统游戏音频属性设置，防止游戏模式干扰音频体验
 */
public class DisableGameAudio extends BaseHookModule {

    private static final String TARGET_PROPERTY = "sys.audio.game_name";

    @Override
    public String getModuleName() {
        return "disable_GameAudio";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "android"  // 系统进程
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("android".equals(packageName)) {
            hookSystemProperties(lpparam);
            hookPhoneWindowManager(lpparam);
            hookAudioManager(lpparam);
        } else {
            // 针对特定游戏的Hook
            hookGameApp(lpparam);
        }
    }

    /**
     * 方法1：直接 Hook SystemProperties.set 方法
     * 拦截所有对 sys.audio.game_name 的设置
     */
    private void hookSystemProperties(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Attempting to hook SystemProperties.set");

            XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    lpparam.classLoader,
                    "set",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            String value = (String) param.args[1];

                            if (TARGET_PROPERTY.equals(key)) {
                                log("Blocked SystemProperties.set for " + key + " = " + value);

                                // 打印调用栈以调试
                                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                                StringBuilder stackTraceStr = new StringBuilder();
                                for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                                    stackTraceStr.append(stackTrace[i].toString()).append("\n");
                                }
                                log("Call stack:\n" + stackTraceStr);

                                // 阻止设置该属性
                                param.setResult(null);
                            }
                        }
                    });

            log("Successfully hooked SystemProperties.set");

        } catch (Throwable t) {
            logError("Failed to hook SystemProperties.set", t);
        }
    }

    /**
     * 方法2：Hook PhoneWindowManager 中的 ZuiGameAppStateListener
     * 拦截游戏模式相关的设置
     */
    private void hookPhoneWindowManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Attempting to hook PhoneWindowManager");

            // Hook ZuiGameAppStateListener 的 onGameAppStart 方法
            XposedHelpers.findAndHookMethod(
                    "com.android.server.policy.PhoneWindowManager$ZuiGameAppStateListener",
                    lpparam.classLoader,
                    "onGameAppStart",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            log("ZuiGameAppStateListener.onGameAppStart for: " + packageName);

                            if (shouldBlockGameAudio()) {
                                log("Blocking game audio for: " + packageName);
                                // 不阻止方法执行，但会在 SystemProperties.set 层拦截
                            }
                        }
                    });

            // Hook ZuiGameAppStateListener 的 onGameAppExit 方法
            XposedHelpers.findAndHookMethod(
                    "com.android.server.policy.PhoneWindowManager$ZuiGameAppStateListener",
                    lpparam.classLoader,
                    "onGameAppExit",
                    String.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            log("ZuiGameAppStateListener.onGameAppExit for: " + packageName);
                        }
                    });

            log("Successfully hooked PhoneWindowManager");

        } catch (Throwable t) {
            logError("Failed to hook PhoneWindowManager", t);
        }
    }

    /**
     * 方法3：Hook AudioManager.setParameters 方法
     * 拦截 game_voip=true 的设置
     */
    private void hookAudioManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Attempting to hook AudioManager.setParameters");

            XposedHelpers.findAndHookMethod(
                    "android.media.AudioManager",
                    lpparam.classLoader,
                    "setParameters",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String keyValuePairs = (String) param.args[0];

                            if (keyValuePairs != null && keyValuePairs.contains("game_voip=true")) {
                                log("Blocked AudioManager.setParameters: " + keyValuePairs);

                                // 阻止设置游戏VOIP参数
                                param.setResult(null);
                            }
                        }
                    });

            log("Successfully hooked AudioManager.setParameters");

        } catch (Throwable t) {
            logError("Failed to hook AudioManager.setParameters", t);
        }
    }

    /**
     * 针对特定游戏的Hook
     */
    private void hookGameApp(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking game app: " + lpparam.packageName);

            // 在游戏启动时主动清除游戏音频属性
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    lpparam.classLoader,
                    "onCreate",
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 清除游戏音频属性
                            clearGameAudioProperties();
                            log("Cleared game audio properties in " + lpparam.packageName);
                        }
                    });

        } catch (Throwable t) {
            logError("Failed to hook game app", t);
        }
    }

    /**
     * 判断是否应该阻止游戏音频设置
     */
    private boolean shouldBlockGameAudio() {
        // 阻止所有游戏的音频优化设置
        // 可以根据需要修改为特定包名过滤
        return true;
    }

    /**
     * 主动清除游戏音频属性
     */
    private void clearGameAudioProperties() {
        try {
            // 使用反射调用 SystemProperties.set 来清除属性
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method setMethod = systemPropertiesClass.getMethod("set", String.class, String.class);
            setMethod.invoke(null, TARGET_PROPERTY, "");

            log("Manually cleared " + TARGET_PROPERTY);

        } catch (Exception e) {
            logError("Failed to clear properties", e);
        }
    }
}
