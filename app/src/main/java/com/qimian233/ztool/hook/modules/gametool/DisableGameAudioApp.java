package com.qimian233.ztool.hook.modules.gametool;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用游戏音频优化Hook模块（App层）
 * 在应用进程中拦截游戏音频属性设置，防止游戏模式干扰音频体验
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public class DisableGameAudioApp extends BaseHookModule {

    private static final String TARGET_PROPERTY = "sys.audio.game_name";

    public DisableGameAudioApp() {}

    @Override
    public String getModuleName() {
        return "disable_GameAudio_app";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "android"  // 系统进程
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookGameApp(classLoader, packageName);
    }

    /**
     * 针对特定游戏的Hook
     */
    private void hookGameApp(ClassLoader classLoader, String packageName) {
        try {
            log("Hooking game app: " + packageName);

            // 在游戏启动时主动清除游戏音频属性
            Class<?> activityClass = classLoader.loadClass("android.app.Activity");
            Method onCreateMethod = activityClass.getDeclaredMethod("onCreate", android.os.Bundle.class);
            this.xposed.hook(onCreateMethod).intercept(chain -> {
                chain.proceed();
                // 清除游戏音频属性
                clearGameAudioProperties();
                if (DEBUG) log("Cleared game audio properties in " + packageName);
                return null;
            });

        } catch (Throwable t) {
            logError("Failed to hook game app", t);
        }
    }

    /**
     * 主动清除游戏音频属性
     */
    private void clearGameAudioProperties() {
        try {
            // 使用反射调用 SystemProperties.set 来清除属性
            @SuppressLint("PrivateApi") Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method setMethod = systemPropertiesClass.getMethod("set", String.class, String.class);
            setMethod.invoke(null, TARGET_PROPERTY, "");

            log("Manually cleared " + TARGET_PROPERTY);

        } catch (Exception e) {
            logError("Failed to clear properties", e);
        }
    }
}
