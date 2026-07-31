package com.qimian233.ztool.hook.modules.gametool;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用游戏音频优化Hook模块
 * 拦截系统游戏音频属性设置，防止游戏模式干扰音频体验
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public class DisableGameAudio extends BaseHookModule {

    private static final String TARGET_PROPERTY = "sys.audio.game_name";

    public DisableGameAudio() {}

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
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        // App-layer hooks moved to DisableGameAudioApp
    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        hookSystemProperties(classLoader);
        hookPhoneWindowManager(classLoader);
        hookAudioManager(classLoader);
    }


    /**
     * 方法1：直接 Hook SystemProperties.set 方法
     * 拦截所有对 sys.audio.game_name 的设置
     */
    private void hookSystemProperties(ClassLoader classLoader) {
        try {
            logger.info("Attempting to hook SystemProperties.set");

            Class<?> sysPropsClass = classLoader.loadClass("android.os.SystemProperties");
            Method setMethod = sysPropsClass.getDeclaredMethod("set", String.class, String.class);
            hookWithId(setMethod, "set", chain -> {
                String key = (String) chain.getArg(0);
                String value = (String) chain.getArg(1);

                if (TARGET_PROPERTY.equals(key)) {
                    logger.debug("Blocked SystemProperties.set for " + key + " = " + value);

                    // 打印调用栈以调试
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    StringBuilder stackTraceStr = new StringBuilder();
                    for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                        stackTraceStr.append(stackTrace[i].toString()).append("\n");
                    }
                    logger.trace("Call stack:\n" + stackTraceStr);

                    // 阻止设置该属性
                    return null;
                }
                return chain.proceed();
            });

            logger.info("Successfully hooked SystemProperties.set");

        } catch (Throwable t) {
            logger.error("Failed to hook SystemProperties.set", t);
        }
    }

    /**
     * 方法2：Hook PhoneWindowManager 中的 ZuiGameAppStateListener
     * 拦截游戏模式相关的设置
     */
    private void hookPhoneWindowManager(ClassLoader classLoader) {
        try {
            Class<?> targetClass;
            try {
                targetClass = classLoader.loadClass("com.android.server.policy.PhoneWindowManager$ZuiGameAppStateListener");
            } catch (ClassNotFoundException e) {
                targetClass = null;
            }
            if (targetClass == null) {
                try {
                    targetClass = classLoader.loadClass("com.android.server.policy.PhoneWindowManager$2");
                } catch (ClassNotFoundException e) {
                    logger.error("Unable to find PhoneWindowManager internal class");
                }
                if (targetClass == null) {
                    logger.error("Failed to find target class for PhoneWindowManager");
                    return;
                } else {
                    logger.info("Found alternative class for PhoneWindowManager");
                }
            } else {
                logger.info("Found target class for PhoneWindowManager");
            }
            // Hook ZuiGameAppStateListener 的 onGameAppStart 方法
            Method onGameAppStartMethod = targetClass.getDeclaredMethod("onGameAppStart", String.class, String.class);
            hookWithId(onGameAppStartMethod, "on_game_app_start", chain -> {
                String pkgName = (String) chain.getArg(0);
                logger.debug("ZuiGameAppStateListener.onGameAppStart for: " + pkgName);

                // 不阻止方法执行，但会在 SystemProperties.set 层拦截
                return chain.proceed();
            });

            // Hook ZuiGameAppStateListener 的 onGameAppExit 方法
            Method onGameAppExitMethod = targetClass.getDeclaredMethod("onGameAppExit", String.class, String.class);
            hookWithId(onGameAppExitMethod, "on_game_app_exit", chain -> {
                String pkgName = (String) chain.getArg(0);
                logger.debug("ZuiGameAppStateListener.onGameAppExit for: " + pkgName);
                return chain.proceed();
            });

            logger.info("Successfully hooked PhoneWindowManager");
        } catch (Exception e) {
            logger.error("Failed to hook PhoneWindowManager due to unknown reason: ", e);
        }
    }

    /**
     * 方法3：Hook AudioManager.setParameters 方法
     * 拦截 game_voip=true 的设置
     */
    private void hookAudioManager(ClassLoader classLoader) {
        try {
            logger.info("Attempting to hook AudioManager.setParameters");

            Class<?> audioManagerClass = classLoader.loadClass("android.media.AudioManager");
            Method setParametersMethod = audioManagerClass.getDeclaredMethod("setParameters", String.class);
            hookWithId(setParametersMethod, "set_parameters", chain -> {
                String keyValuePairs = (String) chain.getArg(0);

                if (keyValuePairs != null && keyValuePairs.contains("game_voip=true")) {
                    logger.debug("Blocked AudioManager.setParameters: " + keyValuePairs);

                    // 阻止设置游戏VOIP参数
                    return null;
                }
                return chain.proceed();
            });

            logger.info("Successfully hooked AudioManager.setParameters");

        } catch (Throwable t) {
            logger.error("Failed to hook AudioManager.setParameters", t);
        }
    }

}
