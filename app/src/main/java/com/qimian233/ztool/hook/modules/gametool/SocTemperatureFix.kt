package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.data.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * SOC温度修复Hook模块
 * 功能：拦截游戏服务的温度读取方法，从thermal_zone9文件获取真实温度值
 */
public class SocTemperatureFix extends AppHookModule {

    private static final String THERMAL_FILE_PATH = "/sys/class/thermal/thermal_zone9/temp";

    public SocTemperatureFix() {}

    @Override
    public String getModuleName() {
        return "Fix_SocTemp";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.GAME_SERVICE.packageName,
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        logger.debug("SocTemperatureFix: 开始处理包 " + packageName);
        hookZuiGameService(classLoader);
    }

    private void hookZuiGameService(ClassLoader classLoader) {
        try {
            Class<?> hwDataInterfaceClass = classLoader.loadClass("com.zui.game.service.util.HWDataInterface");

            // Hook getTemp 方法
            Method getTempMethod = hwDataInterfaceClass.getDeclaredMethod("getTemp");
            hookWithId(getTempMethod, "get_temp", chain -> {
                logger.info("Block call to getTemp()");
                int originalResult = (int) chain.proceed();
                int newTemperature = readTemperatureFromFile();

                if (newTemperature > 0) {
                    logger.trace("getTemp - Original temperature: " + originalResult + ", new temperature: " + newTemperature);
                    return newTemperature;
                } else {
                    logger.warn("Failed to read temperature file, use original value: " + originalResult);
                    return originalResult;
                }
            });

            // Hook getThermalTemp 方法
            Method getThermalTempMethod = hwDataInterfaceClass.getDeclaredMethod("getThermalTemp", int.class);
            hookWithId(getThermalTempMethod, "get_thermal_temp", chain -> {
                int type = (int) chain.getArg(0);
                logger.debug("Blocked getThermalTemp(), type: " + type);
                int originalResult = (int) chain.proceed();
                int newTemperature = readTemperatureFromFile();

                if (newTemperature > 0) {
                    logger.trace("getThermalTemp - Original: " + originalResult + ", new: " + newTemperature);
                    return newTemperature;
                }
                return originalResult;
            });

            logger.info("Hook executed successfully.");

        } catch (Throwable t) {
            logger.error("Failed to hook ZUI game service!", t);
        }
    }

    /**
     * 从 thermal_zone9 文件读取温度
     * @return 温度值（毫摄氏度），读取失败返回 -1
     */
    private int readTemperatureFromFile() {
        File thermalFile = new File(THERMAL_FILE_PATH);

        if (!thermalFile.exists()) {
            logger.warn("Temperature file does not exist: " + THERMAL_FILE_PATH);
            // 尝试其他可能的thermal文件路径
            return tryAlternativeThermalFiles();
        }

        if (!thermalFile.canRead()) {
            logger.warn("Failed to read file: permission denied " + THERMAL_FILE_PATH);
            return -1;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(thermalFile))) {
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                int temperature = Integer.parseInt(line.trim());
                logger.debug("Read temperature data from file: " + temperature);
                return temperature;
            }

        } catch (IOException e) {
            logger.error("IO exception happened when reading temperature file", e);
        } catch (NumberFormatException e) {
            logger.error("Invalid temperature file format", e);
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
                logger.debug("Alternate temperature file found: " + path);
                return readFromSpecificFile(path);
            }
        }

        logger.warn("Unable to find a valid temperature file.");
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
            logger.error("Failed to read temperature file: " + filePath, e);
        }
        // 忽略关闭异常
        return -1;
    }
}
