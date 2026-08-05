package com.qimian233.ztool.hook.modules.systemui.keyguard;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 强制启用原生 AOSP AOD（Always-On Display），忽略电池省电模式限制。
 * <p>
 * 正常情况下 {@code DozeParameters.getAlwaysOn()} 返回
 * {@code mDozeAlwaysOn && !mAodPowerSave}，
 * 即电池省电模式开启时会阻止 AOD 显示。
 * 本 Hook 直接让 {@code getAlwaysOn()} 始终返回 {@code true}，
 * 无需通过 shell 命令 {@code settings put secure doze_always_on}。
 * </p>
 */
@SuppressLint("PrivateApi")
public class ForceNativeAod extends AppHookModule {

    private static final String TAG = "ForceNativeAOD";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String DOZE_PARAMETERS_CLASS =
            "com.android.systemui.statusbar.phone.DozeParameters";

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
    }

    private void hookGetAlwaysOn(ClassLoader classLoader) {
        try {
            Class<?> dozeParamsClass = classLoader.loadClass(DOZE_PARAMETERS_CLASS);
            Method getAlwaysOnMethod = dozeParamsClass.getDeclaredMethod("getAlwaysOn");
            hookWithId(getAlwaysOnMethod, "get_always_on", chain -> {
                logger.debug("ForceNativeAOD: getAlwaysOn() intercepted, forcing true");
                return Boolean.TRUE;
            });
            logger.info("Hooked DozeParameters.getAlwaysOn() [OK]");
        } catch (Throwable t) {
            logger.error("Failed to hook DozeParameters.getAlwaysOn()", t);
        }
    }
}
