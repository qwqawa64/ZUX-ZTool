package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * SOC温度修复Hook模块
 * 功能：拦截游戏服务的温度读取方法，从thermal_zone9文件获取真实温度值
 */
public class SocTemperatureFix extends BaseHookModule {

    private static final String THERMAL_FILE_PATH = "/sys/class/thermal/thermal_zone9/temp";

    @Override
    public String getModuleName() {
        return "Fix_SocTemp";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.zui.game.service",
                "com.lenovo.gamingservice",
                "com.android.gaming"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (DEBUG) log("SocTemperatureFix: 开始处理包 " + packageName);

        if ("com.zui.game.service".equals(packageName)) {
            hookZuiGameService(lpparam);
        } else if ("com.lenovo.gamingservice".equals(packageName)) {
            hookLenovoGamingService(lpparam);
        } else {
            // 通用游戏服务的Hook
            hookGenericGamingService(lpparam);
        }
    }

    private void hookZuiGameService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> hwDataInterfaceClass = XposedHelpers.findClass(
                    "com.zui.game.service.util.HWDataInterface",
                    lpparam.classLoader
            );

            // Hook getTemp 方法
            XposedHelpers.findAndHookMethod(
                    hwDataInterfaceClass,
                    "getTemp",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("Block call to getTemp()");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int originalResult = (int) param.getResult();
                            int newTemperature = readTemperatureFromFile();

                            if (newTemperature > 0) {
                                if (DEBUG) log("getTemp - Original temperature: " + originalResult + ", new temperature: " + newTemperature);
                                param.setResult(newTemperature);
                            } else {
                                log("Failed to read temperature file, use original value: " + originalResult);
                            }
                        }
                    }
            );

            // Hook getThermalTemp 方法
            XposedHelpers.findAndHookMethod(
                    hwDataInterfaceClass,
                    "getThermalTemp",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int type = (int) param.args[0];
                            if (DEBUG) log("Blocked getThermalTemp(), type: " + type);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int originalResult = (int) param.getResult();
                            int newTemperature = readTemperatureFromFile();

                            if (newTemperature > 0) {
                                if (DEBUG) log("getThermalTemp - Original: " + originalResult + ", new: " + newTemperature);
                                param.setResult(newTemperature);
                            }
                        }
                    }
            );

            log("Hook executed successfully.");

        } catch (Throwable t) {
            logError("Failed to hook ZUI game service!", t);
        }
    }

    private void hookLenovoGamingService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 尝试Hook联想游戏服务中的温度相关方法
            Class<?> thermalManagerClass = XposedHelpers.findClassIfExists(
                    "com.lenovo.gamingservice.ThermalManager",
                    lpparam.classLoader
            );

            if (thermalManagerClass != null) {
                XposedHelpers.findAndHookMethod(
                        thermalManagerClass,
                        "getCurrentTemperature",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                int originalResult = (int) param.getResult();
                                int newTemperature = readTemperatureFromFile();

                                if (newTemperature > 0) {
                                    if (DEBUG) log("Lenovo Game Service - temperature fix: "
                                            + originalResult
                                            + " -> "
                                            + newTemperature);
                                    param.setResult(newTemperature);
                                }
                            }
                        }
                );
                log("Hook for gaming service executed successfully.");
            } else {
                log("Unable to find class ThermalManager");
            }

        } catch (Throwable t) {
            logError("Hook failed!", t);
        }
    }

    private void hookGenericGamingService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 尝试Hook通用的温度读取方法
            String[] temperatureMethods = {
                    "getTemperature",
                    "getCPUTemperature",
                    "getGPUTemperature",
                    "getThermalValue"
            };

            for (String methodName : temperatureMethods) {
                try {
                    XposedHelpers.findAndHookMethod(
                            "android.os.SystemProperties",
                            lpparam.classLoader,
                            methodName,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    int newTemperature = readTemperatureFromFile();
                                    if (newTemperature > 0) {
                                        if (DEBUG) log("Generic temperature detection method "
                                                + methodName
                                                + " fixed, new temperature: "
                                                + newTemperature);
                                        param.setResult(newTemperature);
                                    }
                                }
                            }
                    );
                } catch (Throwable t) {
                    // 方法不存在是正常的，继续尝试下一个
                }
            }

            if (DEBUG) log("Generic detection method hook executed successfully.");

        } catch (Throwable t) {
            logError("Failed to hook generic temperature detection method", t);
        }
    }

    /**
     * 从 thermal_zone9 文件读取温度
     * @return 温度值（毫摄氏度），读取失败返回 -1
     */
    private int readTemperatureFromFile() {
        File thermalFile = new File(THERMAL_FILE_PATH);

        if (!thermalFile.exists()) {
            if (DEBUG) log("Temperature file does not exist: " + THERMAL_FILE_PATH);
            // 尝试其他可能的thermal文件路径
            return tryAlternativeThermalFiles();
        }

        if (!thermalFile.canRead()) {
            log("Failed to read file: permission denied " + THERMAL_FILE_PATH);
            return -1;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(thermalFile))) {
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                int temperature = Integer.parseInt(line.trim());
                if (DEBUG) log("Read temperature data from file: " + temperature);
                return temperature;
            }

        } catch (IOException e) {
            logError("IO exception happened when reading temperature file", e);
        } catch (NumberFormatException e) {
            logError("Invalid temperature file format", e);
        }

        return -1;
    }

    /**
     * 尝试其他可能的thermal文件路径
     */
    private int tryAlternativeThermalFiles() {
        String[] alternativePaths = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/class/thermal/thermal_zone2/temp",
                "/sys/devices/virtual/thermal/thermal_zone9/temp",
                "/sys/class/hwmon/hwmon0/temp1_input"
        };

        for (String path : alternativePaths) {
            File thermalFile = new File(path);
            if (thermalFile.exists() && thermalFile.canRead()) {
                if (DEBUG) log("Alternate temperature file found: " + path);
                return readFromSpecificFile(path);
            }
        }

        log("Unable to find a valid temperature file.");
        return -1;
    }

    private int readFromSpecificFile(String filePath) {
        File thermalFile = new File(filePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(thermalFile))) {
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                return Integer.parseInt(line.trim());
            }
        } catch (Exception e) {
            logError("Failed to read temperature file: " + filePath, e);
        }
        // 忽略关闭异常
        return -1;
    }
}
