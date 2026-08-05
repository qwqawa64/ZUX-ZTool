package com.qimian233.ztool.hook.modules.systemui.keyguard;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

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
    private static final String TEMP_PATH = "/sys/class/power_supply/battery/temp";

    // PreferenceKey 常量（与 PreferenceKeys.kt 中的键名一致）
    private static final String PREFS_NAME = "xposed_module_config";
    private static final String KEY_SHOW_VOLTAGE = "systemui_realwatts_show_voltage";
    private static final String KEY_SHOW_CURRENT = "systemui_realwatts_show_current";
    private static final String KEY_SHOW_POWER = "systemui_realwatts_show_power";
    private static final String KEY_SHOW_TEMPERATURE = "systemui_realwatts_show_temperature";
    private static final String KEY_SHOW_INDICATOR = "systemui_realwatts_show_indicator";
    private static final String KEY_CUSTOM_FORMAT_ENABLED = "systemui_realwatts_custom_format_enabled";
    private static final String KEY_CUSTOM_FORMAT = "systemui_realwatts_custom_format";

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
                            SharedPreferences prefs = this.xposed.getRemotePreferences(PREFS_NAME);
                            String displayText = buildDisplayText(chargingData, prefs);
                            if (displayText.isEmpty()) {
                                logger.warn("未能检测到充电功率");
                                return originalText + "\n --W";
                            }
                            String newText = originalText + "\n" + displayText;
                            lastUpdate = System.currentTimeMillis();
                            logger.debug("成功添加充电显示: " + displayText);
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
            String tempStr = readSysfs(TEMP_PATH);

            if (currentStr == null || voltageStr == null || currentStr.isEmpty() || voltageStr.isEmpty()) {
                logger.warn("Java IO 读取 sysfs 无有效数据 - 电流: " + currentStr + ", 电压: " + voltageStr);
                return null;
            }

            return buildChargingData(status, currentStr, voltageStr, tempStr, "Java IO");

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
            String tempStr = executeRootCommand("cat " + TEMP_PATH);

            if (currentStr == null || voltageStr == null || currentStr.isEmpty() || voltageStr.isEmpty()) {
                logger.warn("su 读取失败 - 电流: " + currentStr + ", 电压: " + voltageStr);
                return null;
            }

            return buildChargingData(status, currentStr, voltageStr, tempStr, "su");

        } catch (Exception e) {
            logger.error("su 读取充电数据异常", e);
            return null;
        }
    }

    /**
     * 将原始 sysfs 字符串解析为 ChargingData。
     */
    private ChargingData buildChargingData(String status, String currentStr, String voltageStr, String tempStr, String source) {
        boolean isCharging = "Charging".equalsIgnoreCase(status) || "Full".equalsIgnoreCase(status);

        long currentMicroA = Long.parseLong(currentStr.trim());
        long voltageMicroV = Long.parseLong(voltageStr.trim());

        double currentA = currentMicroA / 1000000.0;
        double voltageV = voltageMicroV / 1000000.0;
        double power = Math.abs(currentA * voltageV);

        // 温度：sysfs 单位为 0.1°C，除以 10 得 °C
        double temperature = -273; // 默认无效值
        if (tempStr != null && !tempStr.isEmpty()) {
            try {
                temperature = Long.parseLong(tempStr.trim()) / 10.0;
            } catch (NumberFormatException e) {
                logger.warn("温度值解析失败: " + tempStr);
            }
        }

        ChargingData data = new ChargingData();
        data.isCharging = isCharging;
        data.current = (int) (currentA * 1000);
        data.voltage = (float) voltageV;
        data.power = power;
        data.temperature = temperature;

        logger.debug(source + " 读取实时充电数据 - 状态: " + status +
                ", 电流: " + currentA + "A (" + currentMicroA + "μA)" +
                ", 电压: " + voltageV + "V (" + voltageMicroV + "μV)" +
                ", 温度: " + (temperature > -200 ? (int)temperature + "°C" : "N/A") +
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

    // ═══════════════════════════════════════════════════════════
    // 显示文本构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据用户偏好构建显示文本。
     * <p>
     * 如果启用了高级自定义格式，使用占位符替换；
     * 否则按子开关组合默认格式。
     */
    private String buildDisplayText(ChargingData data, SharedPreferences prefs) {
        if (data == null || data.power <= 0) return "";

        boolean customEnabled = prefs.getBoolean(KEY_CUSTOM_FORMAT_ENABLED, false);

        if (customEnabled) {
            return buildCustomFormat(data, prefs);
        } else {
            return buildDefaultFormat(data, prefs);
        }
    }

    /**
     * 默认模式：按子开关拼接各数据项。
     */
    private String buildDefaultFormat(ChargingData data, SharedPreferences prefs) {
        StringBuilder sb = new StringBuilder();

        boolean showPower = prefs.getBoolean(KEY_SHOW_POWER, true);
        boolean showVoltage = prefs.getBoolean(KEY_SHOW_VOLTAGE, false);
        boolean showCurrent = prefs.getBoolean(KEY_SHOW_CURRENT, false);
        boolean showTemp = prefs.getBoolean(KEY_SHOW_TEMPERATURE, false);
        boolean showIndicator = prefs.getBoolean(KEY_SHOW_INDICATOR, true);

        if (showVoltage) {
            sb.append(POWER_FORMAT.format(data.voltage)).append("V");
        }
        if (showCurrent) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(POWER_FORMAT.format(data.current / 1000.0)).append("A");
        }
        if (showPower) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(POWER_FORMAT.format(data.power)).append("W");
        }
        if (showTemp && data.temperature > -200) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append((int) data.temperature).append("°C");
        }
        if (showIndicator) {
            sb.append(getIndicator(data.power));
        }

        // 如果什么都没选，默认只显示功率
        if (sb.length() == 0) {
            sb.append(POWER_FORMAT.format(data.power)).append("W");
        }

        return sb.toString();
    }

    /**
     * 高级自定义格式：在 Java 侧做纯字符串替换，不经过 shell，完全安全。
     */
    private String buildCustomFormat(ChargingData data, SharedPreferences prefs) {
        String format = prefs.getString(KEY_CUSTOM_FORMAT, "");
        if (format == null || format.trim().isEmpty()) {
            // 空格式回退到默认
            logger.warn("自定义格式为空，回退到默认格式");
            return buildDefaultFormat(data, prefs);
        }

        String result = format;
        // Java 侧字符串替换，零 shell 风险
        result = result.replace("${voltage}", POWER_FORMAT.format(data.voltage));
        result = result.replace("${current}", POWER_FORMAT.format(data.current / 1000.0));
        result = result.replace("${power}", POWER_FORMAT.format(data.power));
        result = result.replace("${temperature}",
                data.temperature > -200 ? String.valueOf((int) data.temperature) : "N/A");
        result = result.replace("${ind}", getIndicator(data.power));

        return result;
    }

    /**
     * 根据功率范围返回闪电记号字符串。
     */
    private String getIndicator(double watts) {
        if (watts < 30) {
            return "";
        } else if (watts < 65) {
            return " ⚡";
        } else {
            return " ⚡⚡";
        }
    }

    /**
     * 充电数据容器类
     */
    private static class ChargingData {
        boolean isCharging;
        int current;       // 毫安
        float voltage;     // 伏特
        double power;      // 瓦特
        double temperature; // 摄氏度，-273 表示无效
    }

}
