package com.qimian233.ztool.hook.modules.systemui;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 诊断 Hook — 在 computePowerIndication 执行后记录 KeyguardIndicationController 的关键字段值。
 * <p>
 * 始终启用（module name = "test_hook"），用于排查锁屏充电指示相关字段的实际运行时状态。
 * </p>
 */
@SuppressLint("PrivateApi")
public class KeyguardIndicationDiagnosticHook extends BaseHookModule {

    private static final String TARGET_CLASS =
            "com.android.systemui.statusbar.KeyguardIndicationController";

    public KeyguardIndicationDiagnosticHook() {}

    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        if (!"com.android.systemui".equals(param.getPackageName())) return;

        ClassLoader classLoader = param.getDefaultClassLoader();
        try {
            Method computeMethod = classLoader.loadClass(TARGET_CLASS)
                    .getDeclaredMethod("computePowerIndication");
            this.xposed.hook(computeMethod).intercept(chain -> {
                // 先执行原方法
                Object result = chain.proceed();

                try {
                    Object controller = chain.getThisObject();
                    Class<?> cl = controller.getClass();

                    boolean batteryDefender = cl.getDeclaredField("mBatteryDefender")
                            .getBoolean(controller);
                    int batteryLevel = cl.getDeclaredField("mBatteryLevel")
                            .getInt(controller);
                    boolean batteryLimited = cl.getDeclaredField("mBatteryLimited")
                            .getBoolean(controller);
                    int chargingSpeed = cl.getDeclaredField("mChargingSpeed")
                            .getInt(controller);
                    int chargingWattage = cl.getDeclaredField("mChargingWattage")
                            .getInt(controller);

                    log("computePowerIndication diagnostic — "
                            + "mBatteryDefender=" + batteryDefender
                            + ", mBatteryLevel=" + batteryLevel
                            + ", mBatteryLimited=" + batteryLimited
                            + ", mChargingSpeed=" + chargingSpeed
                            + ", mChargingWattage=" + chargingWattage
                            + " | returned=" + (result != null ? "\"" + result + "\"" : "null"));
                } catch (Throwable t) {
                    logError("Failed to read diagnostic fields", t);
                }

                return result;
            });

            log("Diagnostic hook installed on computePowerIndication");
        } catch (Throwable t) {
            logError("Failed to install diagnostic hook", t);
        }
    }
}
