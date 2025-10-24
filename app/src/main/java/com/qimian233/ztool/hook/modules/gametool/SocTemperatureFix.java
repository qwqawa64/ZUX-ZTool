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

        log("SocTemperatureFix: 开始处理包 " + packageName);

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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            log("拦截 getTemp() 调用");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            int originalResult = (int) param.getResult();
                            int newTemperature = readTemperatureFromFile();

                            if (newTemperature > 0) {
                                log("getTemp - 原始温度: " + originalResult + ", 新温度: " + newTemperature);
                                param.setResult(newTemperature);
                            } else {
                                log("读取温度文件失败，使用原始值: " + originalResult);
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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int type = (int) param.args[0];
                            log("拦截 getThermalTemp() 类型: " + type);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            int originalResult = (int) param.getResult();
                            int newTemperature = readTemperatureFromFile();

                            if (newTemperature > 0) {
                                log("getThermalTemp - 原始: " + originalResult + ", 新值: " + newTemperature);
                                param.setResult(newTemperature);
                            }
                        }
                    }
            );

            log("ZUI游戏服务温度修复Hook安装成功");

        } catch (Throwable t) {
            logError("ZUI游戏服务Hook安装失败", t);
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
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                int originalResult = (int) param.getResult();
                                int newTemperature = readTemperatureFromFile();

                                if (newTemperature > 0) {
                                    log("联想游戏服务 - 温度修复: " + originalResult + " -> " + newTemperature);
                                    param.setResult(newTemperature);
                                }
                            }
                        }
                );
                log("联想游戏服务温度修复Hook安装成功");
            } else {
                log("未找到联想游戏服务的ThermalManager类");
            }

        } catch (Throwable t) {
            logError("联想游戏服务Hook安装失败", t);
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
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    int newTemperature = readTemperatureFromFile();
                                    if (newTemperature > 0) {
                                        log("通用温度方法 " + methodName + " 修复为: " + newTemperature);
                                        param.setResult(newTemperature);
                                    }
                                }
                            }
                    );
                } catch (Throwable t) {
                    // 方法不存在是正常的，继续尝试下一个
                }
            }

            log("通用游戏服务温度修复Hook安装成功");

        } catch (Throwable t) {
            logError("通用游戏服务Hook安装失败", t);
        }
    }

    /**
     * 从 thermal_zone9 文件读取温度
     * @return 温度值（毫摄氏度），读取失败返回 -1
     */
    private int readTemperatureFromFile() {
        File thermalFile = new File(THERMAL_FILE_PATH);

        if (!thermalFile.exists()) {
            log("温度文件不存在: " + THERMAL_FILE_PATH);
            // 尝试其他可能的thermal文件路径
            return tryAlternativeThermalFiles();
        }

        if (!thermalFile.canRead()) {
            log("无权限读取温度文件: " + THERMAL_FILE_PATH);
            return -1;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(thermalFile));
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                int temperature = Integer.parseInt(line.trim());
                log("从文件读取温度: " + temperature);
                return temperature;
            }

        } catch (IOException e) {
            logError("读取温度文件IO异常", e);
        } catch (NumberFormatException e) {
            logError("温度数据格式异常", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logError("关闭文件读取器异常", e);
                }
            }
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
                log("找到替代温度文件: " + path);
                // 更新默认路径以便后续使用
                // THERMAL_FILE_PATH = path; // 注意：这里不能修改final变量
                return readFromSpecificFile(path);
            }
        }

        log("未找到任何可用的温度文件");
        return -1;
    }

    private int readFromSpecificFile(String filePath) {
        File thermalFile = new File(filePath);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(thermalFile));
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                return Integer.parseInt(line.trim());
            }
        } catch (Exception e) {
            logError("读取替代温度文件失败: " + filePath, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // 忽略关闭异常
                }
            }
        }
        return -1;
    }
}
