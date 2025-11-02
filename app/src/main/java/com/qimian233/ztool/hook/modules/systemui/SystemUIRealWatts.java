package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * SystemUI充电瓦数显示Hook模块
 * 在锁屏充电提示中添加实时充电功率显示（使用Root权限读取系统文件获取实时数据）
 */
public class SystemUIRealWatts extends BaseHookModule {

    private static final String TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController";

    // 系统文件路径
    private static final String CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now";
    private static final String VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now";
    private static final String STATUS_PATH = "/sys/class/power_supply/battery/status";

    // 用于格式化功率显示，保留两位小数
    private static final DecimalFormat POWER_FORMAT = new DecimalFormat("0.00");

    @Override
    public String getModuleName() {
        return "systemUI_RealWatts";
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
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 获取原始返回的充电提示文本
                            String originalText = (String) param.getResult();
                            if (originalText == null) return;

                            // 获取KeyguardIndicationController实例
                            Object controller = param.thisObject;

                            // 获取充电状态
                            boolean isPluggedIn = XposedHelpers.getBooleanField(controller, "mPowerPluggedIn");

                            // 只在充电状态下显示瓦数
                            if (isPluggedIn) {
                                // 使用Root权限读取系统文件获取实时充电功率
                                ChargingData chargingData = readChargingDataWithRoot();

                                if (chargingData != null && chargingData.isCharging && chargingData.power > 0) {
                                    // 使用换行符 \n 追加功率信息
                                    String newText = originalText + "\n" + formatWattage(chargingData.power);
                                    param.setResult(newText);
                                    log("成功添加充电瓦数显示: " + POWER_FORMAT.format(chargingData.power) + "W");
                                } else {
                                    log("未能检测到充电功率");
                                    if (!isBatteryFull()) {
                                        param.setResult(originalText + "\n请授予 \"系统界面\" 应用Root权限");
                                    }
                                }
                            }
                        }
                    });

            log("成功Hook KeyguardIndicationController");

        } catch (Throwable t) {
            logError("Hook KeyguardIndicationController失败", t);
        }
    }

    public static boolean isBatteryFull() {
        try {
            // 以root权限执行shell命令
            Process process = Runtime.getRuntime().exec("su -c \"dumpsys battery | grep level\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // 读取命令输出
            while ((line = reader.readLine()) != null) {
                // 检查输出行是否包含"level"关键字
                if (line.contains("level")) {
                    // 解析电量值：假设输出格式为 "level: 100"
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        String levelStr = parts[1].trim();
                        try {
                            int level = Integer.parseInt(levelStr);
                            return level == 100; // 如果电量为100，返回true
                        } catch (NumberFormatException e) {
                            // 解析数字失败，记录错误并继续（可能有多行输出）
                            e.printStackTrace();
                        }
                    }
                }
            }

            // 关闭资源
            reader.close();
            process.destroy();
        } catch (IOException e) {
            // 命令执行失败（如无root权限），记录错误
            e.printStackTrace();
        }

        // 默认返回false（未找到有效电量或命令失败）
        return false;
    }

    /**
     * 使用Root权限读取充电数据
     */
    private ChargingData readChargingDataWithRoot() {
        try {
            // 使用Root权限读取充电状态
            String status = executeRootCommand("cat " + STATUS_PATH);
            boolean isCharging = "Charging".equalsIgnoreCase(status) || "Full".equalsIgnoreCase(status);

            // 使用Root权限读取电流（单位：微安）
            String currentStr = executeRootCommand("cat " + CURRENT_NOW_PATH);
            // 使用Root权限读取电压（单位：微伏）
            String voltageStr = executeRootCommand("cat " + VOLTAGE_NOW_PATH);

            if (currentStr == null || voltageStr == null || currentStr.isEmpty() || voltageStr.isEmpty()) {
                log("Root读取失败 - 电流: " + currentStr + ", 电压: " + voltageStr);
                return null;
            }

            // 转换为数值
            long currentMicroA = Long.parseLong(currentStr.trim());
            long voltageMicroV = Long.parseLong(voltageStr.trim());

            // 转换为标准单位
            double currentA = currentMicroA / 1000000.0;  // 微安 -> 安培
            double voltageV = voltageMicroV / 1000000.0;  // 微伏 -> 伏特

            // 计算功率（瓦特）
            double power = Math.abs(currentA * voltageV);

            ChargingData data = new ChargingData();
            data.isCharging = isCharging;
            data.current = (int)(currentA * 1000);  // 转换为毫安
            data.voltage = (float) voltageV;
            data.power = power;  // 保留原始double值，不四舍五入

            log("Root读取实时充电数据 - 状态: " + status +
                    ", 电流: " + currentA + "A (" + currentMicroA + "μA)" +
                    ", 电压: " + voltageV + "V (" + voltageMicroV + "μV)" +
                    ", 功率: " + POWER_FORMAT.format(power) + "W");

            return data;

        } catch (Exception e) {
            logError("Root读取充电数据失败", e);
            return null;
        }
    }

    /**
     * 使用Root权限执行Shell命令
     */
    private String executeRootCommand(String command) {
        Process process = null;
        BufferedReader reader = null;
        try {
            // 使用su命令获取Root权限
            process = Runtime.getRuntime().exec("su -c " + command);

            // 读取命令输出
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            // 等待命令执行完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log("Root命令执行失败，退出码: " + exitCode + ", 命令: " + command);
                return null;
            }

            String result = output.toString().trim();
            log("Root命令执行成功: " + command + " -> " + result);
            return result;

        } catch (Exception e) {
            logError("执行Root命令失败: " + command, e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 格式化充电瓦数显示：显示"[功率]W [闪电符号]"，保留两位小数
     */
    private String formatWattage(double watts) {
        if (watts <= 0) return "";

        // 基础字符串："[功率]W"，保留两位小数
        String base = POWER_FORMAT.format(watts) + "W";

        // 根据功率范围附加闪电符号
        if (watts < 10) {
            return base;  // 无闪电符号
        } else if (watts < 18) {
            return base;  // 无闪电符号
        } else if (watts < 30) {
            return base + " ⚡";  // 一个闪电符号
        } else if (watts < 65) {
            return base + " ⚡⚡";  // 两个闪电符号
        } else {
            return base + " ⚡⚡⚡";  // 三个闪电符号
        }
    }

    /**
     * 充电数据容器类
     */
    private static class ChargingData {
        boolean isCharging;
        int current;    // 毫安
        float voltage;  // 伏特
        double power;   // 瓦特（保留小数）
    }
}
