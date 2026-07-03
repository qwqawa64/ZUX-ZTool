package com.qimian233.ztool.hook.modules.systemui;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * SystemUI充电瓦数显示Hook模块
 * 在锁屏充电提示中添加实时充电功率显示
 */
@SuppressLint("PrivateApi")
public class SystemUIChargeWattsHook extends BaseHookModule {

    private static final String TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController";

    public SystemUIChargeWattsHook() {}

    @Override
    public String getModuleName() {
        return "systemui_charge_watts";
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
            hookKeyguardIndicationController(classLoader);
        }
    }

    private void hookKeyguardIndicationController(ClassLoader classLoader) {
        try {
            // Hook computePowerIndication方法来添加充电瓦数显示
            Method computeMethod = classLoader.loadClass(TARGET_CLASS)
                    .getDeclaredMethod("computePowerIndication");
            this.xposed.hook(computeMethod).intercept(chain -> {
                try {
                    // 获取原始返回的充电提示文本
                    Object result = chain.proceed();
                    String originalText = (String) result;
                    if (originalText == null) return null;

                    // 获取KeyguardIndicationController实例
                    Object controller = chain.getThisObject();
                    Class<?> cl = controller.getClass();

                    // 获取充电状态相关字段
                    boolean isPluggedIn = cl.getDeclaredField("mPowerPluggedIn").getBoolean(controller);
                    int chargingWattage = cl.getDeclaredField("mChargingWattage").getInt(controller);

                    // 只在充电状态下显示瓦数，且瓦数大于0
                    if (isPluggedIn && chargingWattage > 0) {
                        // 尝试多种单位转换
                        int watts = calculateActualWatts(chargingWattage);

                        if (watts > 0) {
                            // 使用换行符 \n 追加功率信息
                            String newText = originalText + "\n" + formatWattage(watts);
                            log("成功添加充电瓦数显示: " + watts + "W");
                            return newText;
                        }
                    }
                    return result;
                } catch (Throwable t) {
                    logError("computePowerIndication hook回调异常", t);
                    return chain.proceed();
                }
            });

            // 额外Hook电池状态更新方法，确保能获取到最新的充电数据
            Method refreshMethod = classLoader.loadClass(TARGET_CLASS)
                    .getDeclaredMethod("onRefreshBatteryInfo",
                            classLoader.loadClass("com.android.settingslib.fuelgauge.BatteryStatus"));
            this.xposed.hook(refreshMethod).intercept(chain -> {
                try {
                    Object result = chain.proceed();
                    // 这个方法会在电池状态更新时调用，我们可以在这里获取最新的充电数据
                    Object batteryStatus = chain.getArg(0);
                    if (batteryStatus != null) {
                        try {
                            // 尝试从BatteryStatus对象获取充电功率
                            int maxChargingWattage = batteryStatus.getClass()
                                    .getDeclaredField("maxChargingWattage").getInt(batteryStatus);
                            Object controller = chain.getThisObject();
                            Class<?> cl = controller.getClass();

                            // 记录调试信息
                            log("BatteryStatus更新 - maxChargingWattage: " + maxChargingWattage +
                                    ", mChargingWattage: " + cl.getDeclaredField("mChargingWattage").getInt(controller));

                        } catch (Throwable t) {
                            logError("读取BatteryStatus失败", t);
                        }
                    }
                    return result;
                } catch (Throwable t) {
                    logError("onRefreshBatteryInfo hook回调异常", t);
                    return chain.proceed();
                }
            });

            log("成功Hook KeyguardIndicationController");

        } catch (Throwable t) {
            logError("Hook KeyguardIndicationController失败", t);
        }
    }

    /**
     * 尝试多种方式计算实际瓦数
     */
    private int calculateActualWatts(int rawWattage) {
        // 情况1：如果值在合理范围内（1-150W），直接使用
        if (rawWattage > 0 && rawWattage <= 150000) {
            // 可能是毫瓦单位，转换为瓦
            return rawWattage / 1000;
        }

        // 情况2：如果值很大，可能是微瓦单位
        if (rawWattage > 150000 && rawWattage <= 150000000) {
            return rawWattage / 1000000;
        }

        // 情况3：如果值很小，可能是直接就是瓦数
        if (rawWattage > 0 && rawWattage <= 150) {
            return rawWattage;
        }

        // 情况4：如果值异常大，尝试除以10000（某些设备的特殊单位）
        if (rawWattage > 1000000) {
            return rawWattage / 10000;
        }

        // 无法确定单位，返回0表示不显示
        log("无法识别的瓦数单位: " + rawWattage);
        return 0;
    }

    /**
     * 格式化充电瓦数显示：显示"[功率]W [闪电符号]"
     */
    private String formatWattage(int watts) {
        if (watts <= 0) return "";

        // 基础字符串："[功率]W"
        String base = watts + "W";

        // 根据功率范围附加闪电符号
        if (watts < 10) {
            return base;  // 无闪电符号
        } else if (watts < 18) {
            return base;  // 无闪电符号
        } else if (watts < 30) {
            return base + "⚡";  // 一个闪电符号
        } else if (watts < 65) {
            return base + "⚡⚡";  // 两个闪电符号
        } else {
            return base + "⚡⚡⚡";  // 三个闪电符号
        }
    }

}
