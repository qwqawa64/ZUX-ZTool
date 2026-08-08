package com.qimian233.ztool.hook.modules.gametool;

import android.annotation.SuppressLint;
import android.content.Context;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用游戏音频优化Hook模块（App层）
 * 在应用进程中拦截游戏音频属性设置，防止游戏模式干扰音频体验
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public class DisableGameAudioApp extends AppHookModule {

    private static final String TARGET_PROPERTY = "sys.audio.game_name";

    public DisableGameAudioApp() {}

    @Override
    public String getModuleName() {
        return "disable_GameAudio_app";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.GAME_SERVICE.packageName  // 系统进程
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookGameApp(classLoader, packageName);
        hookGameServicePackage(classLoader);
    }

    /**
     * 针对特定游戏的Hook
     */
    private void hookGameApp(ClassLoader classLoader, String packageName) {
        try {
            logger.info("Hooking game app: " + packageName);

            // 在游戏启动时主动清除游戏音频属性
            Class<?> activityClass = classLoader.loadClass("android.app.Activity");
            Method onCreateMethod = activityClass.getDeclaredMethod("onCreate", android.os.Bundle.class);
            hookWithId(onCreateMethod, "on_create", chain -> {
                chain.proceed();
                // 清除游戏音频属性
                clearGameAudioProperties();
                logger.debug("Cleared game audio properties in " + packageName);
                return null;
            });

        } catch (Throwable t) {
            logger.error("Failed to hook game app", t);
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

            logger.info("Manually cleared " + TARGET_PROPERTY);

        } catch (Exception e) {
            logger.error("Failed to clear properties", e);
        }
    }

    private void hookGameServicePackage(ClassLoader classLoader) {
        try {
            logger.info("Start processing DolbyUtils.");
            Method m = classLoader
                    .loadClass("com.zui.game.service.util.DolbyUtils")
                    .getDeclaredMethod("handleDolbyGameSound", Context.class, Integer.TYPE);
            hookWithId(m, "hook_89", chain -> null);
            logger.info("Successfully hooked DolbyUtils.handleDolbyGameSound - disabled game sound processing");
        } catch (Throwable t) {
            logger.error("Failed to hook GameService package", t);
        }
    }
}
