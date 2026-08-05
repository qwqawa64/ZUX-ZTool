package com.qimian233.ztool.hook.modules.systemui.keyguard;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

/**
 * SystemUI充电瓦数显示Hook模块
 * 在锁屏充电提示中添加实时充电功率显示
 * <p>
 * 读取策略：优先使用 Java IO 直接读取 sysfs（SystemUI 以 system uid 运行，
 * 通常有权限读取 /sys/class/power_supply/battery/*），
 * 失败时自动 fallback 到 su 命令。
 */
@SuppressLint("PrivateApi")
public class SystemUIRealWatts extends AppHookModule {

    private static final String TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController";

    // 系统文件路径
    private static final String CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now";
    private static final String VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now";
    private static final String STATUS_PATH = "/sys/class/power_supply/battery/status";

    // 用于格式化功率显示，保留两位小数
    private static final DecimalFormat POWER_FORMAT = new DecimalFormat("0.00");

    private static long lastUpdate = 0;

    // su 可用性缓存，null = 尚未检测
    private static Boolean suAvailable = null;

    public SystemUIRealWatts() {}

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
            hookWithId(computeMethod, "compute", chain -> {
                try {
                    // 获取原始返回的充电提示文本
                    Object result = chain.proceed();
                    String originalText = (String) result;
                    if (originalText == null) return null;

                    // 获取KeyguardIndicationController实例
                    Object controller = chain.getThisObject();
                    Class<?> cl = controller.getClass();

                    // 获取充电状态
                    boolean isPluggedIn = cl.getDeclaredField("mPowerPluggedIn").getBoolean(controller);

                    // 只在充电状态下显示瓦数
                    if (isPluggedIn) {
                        if (System.currentTimeMillis() - lastUpdate < 100) {
                            logger.debug("Debounce triggered. Charging power will not be updated this time.");
                            return result;
                        }
                        // 读取实时充电功率（Java IO 优先，失败时 fallback 到 su）
                        ChargingData chargingData = readChargingData();

                        if (chargingData != null && chargingData.isCharging && chargingData.power > 0) {
                            // 使用换行符 \n 追加功率信息
                            String newText = originalText + "\n" + formatWattage(chargingData.power);
                            lastUpdate = System.currentTimeMillis();
                            logger.debug("成功添加充电瓦数显示: " + POWER_FORMAT.format(chargingData.power) + "W");
                            return newText;
                        } else {
                            logger.warn("未能检测到充电功率");
                            return originalText + "\n --W";
                        }
                    }
                    return result;
                } catch (Throwable t) {
                    logger.error("computePowerIndication hook回调异常", t);
                    return chain.proceed();
                }
            });

            logger.info("成功Hook KeyguardIndicationController");

        } catch (Throwable t) {
            logger.error("Hook KeyguardIndicationController失败", t);
        }
    }

    /**
     * 读取充电数据：优先使用 Java IO 直接读取 sysfs，失败时 fallback 到 su。
     */
    private ChargingData readChargingData() {
        // 第一步：尝试 Java IO 直接读取（SystemUI 以 system uid 运行，通常有权限）
        ChargingData data = readChargingDataViaFileIO();
        if (data != null) {
            return data;
        }

        // 第二步：Java IO 失败，fallback 到 su
        logger.warn("Java IO 读取 sysfs 失败，尝试 fallback 到 su 模式");

        if (!isSuAvailable()) {
            logger.warn("su 不可用，无法 fallback");
            return null;
        }

        return readChargingDataViaSu();
    }

    /**
     * 通过 Java IO 直接读取 sysfs 获取充电数据。
     * SystemUI 以 system uid 运行，通常有权限读取这些内核接口文件。
     */
    private ChargingData readChargingDataViaFileIO() {
        try {
            String status = readSysfs(STATUS_PATH);
            String currentStr = readSysfs(CURRENT_NOW_PATH);
            String voltageStr = readSysfs(VOLTAGE_NOW_PATH);

            if (currentStr == null || voltageStr == null || currentStr.isEmpty() || voltageStr.isEmpty()) {
                logger.warn("Java IO 读取 sysfs 无有效数据 - 电流: " + currentStr + ", 电压: " + voltageStr);
                return null;
            }

            return buildChargingData(status, currentStr, voltageStr, "Java IO");

        } catch (Exception e) {
            logger.error("Java IO 读取充电数据异常", e);
            return null;
        }
    }

    /**
     * 通过 su 命令读取充电数据（fallback 方案）。
     */
    private ChargingData readChargingDataViaSu() {
        try {
            String status = executeRootCommand("cat " + STATUS_PATH);
            String currentStr = executeRootCommand("cat " + CURRENT_NOW_PATH);
            String voltageStr = executeRootCommand("cat " + VOLTAGE_NOW_PATH);

            if (currentStr == null || voltageStr == null || currentStr.isEmpty() || voltageStr.isEmpty()) {
                logger.warn("su 读取失败 - 电流: " + currentStr + ", 电压: " + voltageStr);
                return null;
            }

            return buildChargingData(status, currentStr, voltageStr, "su");

        } catch (Exception e) {
            logger.error("su 读取充电数据异常", e);
            return null;
        }
    }

    /**
     * 将原始 sysfs 字符串解析为 ChargingData。
     */
    private ChargingData buildChargingData(String status, String currentStr, String voltageStr, String source) {
        boolean isCharging = "Charging".equalsIgnoreCase(status) || "Full".equalsIgnoreCase(status);

        long currentMicroA = Long.parseLong(currentStr.trim());
        long voltageMicroV = Long.parseLong(voltageStr.trim());

        double currentA = currentMicroA / 1000000.0;
        double voltageV = voltageMicroV / 1000000.0;
        double power = Math.abs(currentA * voltageV);

        ChargingData data = new ChargingData();
        data.isCharging = isCharging;
        data.current = (int) (currentA * 1000);
        data.voltage = (float) voltageV;
        data.power = power;

        logger.debug(source + " 读取实时充电数据 - 状态: " + status +
                ", 电流: " + currentA + "A (" + currentMicroA + "μA)" +
                ", 电压: " + voltageV + "V (" + voltageMicroV + "μV)" +
                ", 功率: " + POWER_FORMAT.format(power) + "W");

        return data;
    }

    /**
     * 使用 Java IO 读取单个 sysfs 文件的一行内容。
     */
    private String readSysfs(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        } catch (IOException e) {
            logger.warn("Java IO 读取 " + path + " 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 检测 su 是否可用（带缓存）。
     */
    private boolean isSuAvailable() {
        if (suAvailable != null) {
            return suAvailable;
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "command -v su"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                suAvailable = (line != null && !line.isEmpty());
            }
            process.waitFor();
            logger.debug("su 可用性检测: " + suAvailable);
            return suAvailable;
        } catch (Exception e) {
            logger.warn("su 可用性检测异常: " + e.getMessage());
            suAvailable = false;
            return false;
        }
    }

    /**
     * 使用 su 执行 Shell 命令（仅作为 fallback 使用）。
     */
    private String executeRootCommand(String command) {
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec("su -c " + command);

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("su 命令执行失败，退出码: " + exitCode + ", 命令: " + command);
                return null;
            }

            String result = output.toString().trim();
            logger.debug("su 命令执行成功: " + command + " -> " + result);
            return result;

        } catch (Exception e) {
            logger.error("执行 su 命令失败: " + command, e);
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
