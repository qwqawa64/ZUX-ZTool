package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUI充电瓦数显示Hook模块
 * 在锁屏充电提示中添加实时充电功率显示
 */
public class SystemUIChargeWattsHook extends BaseHookModule {

    private static final String TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController";

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            hookKeyguardIndicationController(lpparam);
        }
    }

    private void hookKeyguardIndicationController(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader classLoader = lpparam.classLoader;

            // Hook computePowerIndication方法来添加充电瓦数显示
            XposedHelpers.findAndHookMethod(TARGET_CLASS, classLoader,
                    "computePowerIndication", new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 获取原始返回的充电提示文本
                            String originalText = (String) param.getResult();
                            if (originalText == null) return;

                            // 获取KeyguardIndicationController实例
                            Object controller = param.thisObject;

                            // 获取充电状态相关字段
                            boolean isPluggedIn = XposedHelpers.getBooleanField(controller, "mPowerPluggedIn");
                            int chargingWattage = XposedHelpers.getIntField(controller, "mChargingWattage");

                            // 只在充电状态下显示瓦数，且瓦数大于0
                            if (isPluggedIn && chargingWattage > 0) {
                                // 尝试多种单位转换
                                int watts = calculateActualWatts(chargingWattage);

                                if (watts > 0) {
                                    // 使用换行符 \n 追加功率信息
                                    String newText = originalText + "\n" + formatWattage(watts);
                                    param.setResult(newText);
                                    log("成功添加充电瓦数显示: " + watts + "W");
                                }
                            }
                        }
                    });

            // 额外Hook电池状态更新方法，确保能获取到最新的充电数据
            XposedHelpers.findAndHookMethod(TARGET_CLASS, classLoader,
                    "onRefreshBatteryInfo",
                    "com.android.settingslib.fuelgauge.BatteryStatus",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 这个方法会在电池状态更新时调用，我们可以在这里获取最新的充电数据
                            Object batteryStatus = param.args[0];
                            if (batteryStatus != null) {
                                try {
                                    // 尝试从BatteryStatus对象获取充电功率
                                    int maxChargingWattage = XposedHelpers.getIntField(batteryStatus, "maxChargingWattage");
                                    Object controller = param.thisObject;

                                    // 记录调试信息
                                    log("BatteryStatus更新 - maxChargingWattage: " + maxChargingWattage +
                                            ", mChargingWattage: " + XposedHelpers.getIntField(controller, "mChargingWattage"));

                                } catch (Throwable t) {
                                    logError("读取BatteryStatus失败", t);
                                }
                            }
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
