package com.qimian233.ztool.hook.modules.systemui.keyguard;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 强制启用原生 AOSP AOD（Always-On Display），忽略电池省电模式限制。
 * <p>
 * Hook 两处关键判断：
 * <ol>
 *   <li>{@code DozeParameters.getAlwaysOn()} —
 *       控制熄屏后是否立即进入深眠 ({@code setDozeAfterScreenOff})</li>
 *   <li>{@code AmbientDisplayConfiguration.alwaysOnEnabled(int)} —
 *       {@code DozeSuppressor} 用此方法决定 Doze 状态机走
 *       {@code DOZE_AOD}（显示 AOD）还是 {@code DOZE}（直接深眠）。
 *       同时 {@code DozeSensors} 也用此方法控制传感器注册。</li>
 * </ol>
 * 无需通过 shell 命令 {@code settings put secure doze_always_on}。
 * </p>
 */
@SuppressLint("PrivateApi")
public class ForceNativeAod extends AppHookModule {

    private static final String TAG = "ForceNativeAOD";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String DOZE_PARAMETERS_CLASS =
            "com.android.systemui.statusbar.phone.DozeParameters";
    private static final String AMBIENT_DISPLAY_CONFIG_CLASS =
            "android.hardware.display.AmbientDisplayConfiguration";

    public ForceNativeAod() {}

    @Override
    public String getModuleName() {
        return TAG;
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!SYSTEMUI_PACKAGE.equals(packageName)) {
            return;
        }
        if (!isEnabled()) return;

        logger.info("Loading module ForceNativeAOD.");
        hookGetAlwaysOn(classLoader);
        hookAlwaysOnEnabled();
    }

    /**
     * Hook {@code DozeParameters.getAlwaysOn()}，始终返回 true，
     * 确保 {@code updateControlScreenOff()} 中正确设置
     * {@code PowerManager.setDozeAfterScreenOff(false)}。
     */
    private void hookGetAlwaysOn(ClassLoader classLoader) {
        try {
            Class<?> dozeParamsClass = classLoader.loadClass(DOZE_PARAMETERS_CLASS);
            Method getAlwaysOnMethod = dozeParamsClass.getDeclaredMethod("getAlwaysOn");
            hookWithId(getAlwaysOnMethod, "get_always_on", chain -> {
                logger.debug("ForceNativeAOD: getAlwaysOn() -> true");
                return Boolean.TRUE;
            });
            logger.info("Hooked DozeParameters.getAlwaysOn() [OK]");
        } catch (Throwable t) {
            logger.error("Failed to hook DozeParameters.getAlwaysOn()", t);
        }
    }

    /**
     * Hook {@code AmbientDisplayConfiguration.alwaysOnEnabled(int)}，始终返回 true。
     * <p>
     * 该方法是 {@code DozeSuppressor} 决定状态机走向（DOZE vs DOZE_AOD）
     * 以及 {@code DozeSensors} 传感器注册策略的核心判断点，
     * 直接读取 {@code Settings.Secure.doze_always_on}。
     * 因为不再通过 shell 写入该值，必须用 Hook 覆盖。
     * </p>
     */
    private void hookAlwaysOnEnabled() {
        try {
            Class<?> configClass = Class.forName(AMBIENT_DISPLAY_CONFIG_CLASS);
            Method alwaysOnEnabledMethod = configClass.getDeclaredMethod(
                    "alwaysOnEnabled", int.class);
            hookWithId(alwaysOnEnabledMethod, "always_on_enabled", chain -> {
                logger.debug("ForceNativeAOD: alwaysOnEnabled() -> true");
                return Boolean.TRUE;
            });
            logger.info("Hooked AmbientDisplayConfiguration.alwaysOnEnabled() [OK]");
        } catch (Throwable t) {
            logger.error("Failed to hook AmbientDisplayConfiguration.alwaysOnEnabled()", t);
        }
    }
}
