package com.qimian233.ztool.hook.modules.gametool;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * CPU频率Hook模块 - 修复游戏服务中的CPU时钟读取
 * 功能：Hook com.zui.game.service.util.HWDataInterface 的CPU频率获取方法
 * 使其始终读取最后一个CPU核心的频率数据
 */
public class CpuFrequencyFix extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "Fix_CpuClock";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.game.service"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("CpuFrequencyFix: Targeting " + lpparam.packageName);

        try {
            // Hook HWDataInterface 的 getCpuCurFreq() 方法（无参数）
            XposedHelpers.findAndHookMethod("com.zui.game.service.util.HWDataInterface",
                    lpparam.classLoader, "getCpuCurFreq", new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return getLastCpuCoreCurrentFreq();
                        }
                    });

            // Hook HWDataInterface 的 getCpuCurFreq(int coreIndex) 方法
            XposedHelpers.findAndHookMethod("com.zui.game.service.util.HWDataInterface",
                    lpparam.classLoader, "getCpuCurFreq", int.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            // 忽略传入的coreIndex，始终读取最后一个核心
                            return getLastCpuCoreCurrentFreq();
                        }
                    });

            // Hook HWDataInterface 的 getCpuMaxFreq() 方法
            XposedHelpers.findAndHookMethod("com.zui.game.service.util.HWDataInterface",
                    lpparam.classLoader, "getCpuMaxFreq", new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return getLastCpuCoreMaxFreq();
                        }
                    });

            log("CpuFrequencyFix: Successfully hooked CPU frequency methods");

        } catch (Throwable t) {
            logError("CpuFrequencyFix: Error hooking methods", t);
        }
    }

    /**
     * 获取最后一个CPU核心的当前频率
     */
    private int getLastCpuCoreCurrentFreq() {
        try {
            // 获取最后一个CPU核心的索引
            int lastCoreIndex = getLastCpuCoreIndex();
            if (lastCoreIndex < 0) {
                log("CpuFrequencyFix: No CPU cores found, using fallback");
                return readFallbackCpuFreq();
            }

            // 读取当前频率
            String curFreqPath = "/sys/devices/system/cpu/cpu" + lastCoreIndex + "/cpufreq/scaling_cur_freq";
            String freqStr = readSystemFile(curFreqPath);

            if (freqStr != null && !freqStr.isEmpty()) {
                int freq = Integer.parseInt(freqStr.trim());
                log("CpuFrequencyFix: Current freq from core " + lastCoreIndex + ": " + freq);
                return freq;
            }

            // 如果读取失败，尝试备用方法
            log("CpuFrequencyFix: Failed to read current freq from core " + lastCoreIndex);
            return readFallbackCpuFreq();

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error reading CPU current freq", e);
            return 2000000; // 默认值 2.0GHz
        }
    }

    /**
     * 获取最后一个CPU核心的最大频率
     */
    private int getLastCpuCoreMaxFreq() {
        try {
            // 获取最后一个CPU核心的索引
            int lastCoreIndex = getLastCpuCoreIndex();
            if (lastCoreIndex < 0) {
                log("CpuFrequencyFix: No CPU cores found for max freq, using fallback");
                return readFallbackCpuMaxFreq();
            }

            // 读取最大频率
            String maxFreqPath = "/sys/devices/system/cpu/cpu" + lastCoreIndex + "/cpufreq/scaling_max_freq";
            String freqStr = readSystemFile(maxFreqPath);

            if (freqStr != null && !freqStr.isEmpty()) {
                int freq = Integer.parseInt(freqStr.trim());
                log("CpuFrequencyFix: Max freq from core " + lastCoreIndex + ": " + freq);
                return freq;
            }

            // 如果读取失败，尝试备用方法
            log("CpuFrequencyFix: Failed to read max freq from core " + lastCoreIndex);
            return readFallbackCpuMaxFreq();

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error reading CPU max freq", e);
            return 3000000; // 默认值 3.0GHz
        }
    }

    /**
     * 获取最后一个CPU核心的索引
     */
    private int getLastCpuCoreIndex() {
        try {
            File cpuDir = new File("/sys/devices/system/cpu/");
            File[] cpuFiles = cpuDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.matches("cpu[0-9]+");
                }
            });

            if (cpuFiles == null || cpuFiles.length == 0) {
                log("CpuFrequencyFix: No CPU cores found in /sys/devices/system/cpu/");
                return -1;
            }

            // 按核心编号降序排序，取最大的（最后一个核心）
            Arrays.sort(cpuFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    try {
                        int num1 = Integer.parseInt(f1.getName().substring(3));
                        int num2 = Integer.parseInt(f2.getName().substring(3));
                        return Integer.compare(num2, num1); // 降序
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            });

            // 获取最后一个核心的索引
            String lastName = cpuFiles[0].getName();
            int lastIndex = Integer.parseInt(lastName.substring(3));
            log("CpuFrequencyFix: Last CPU core index: " + lastIndex);
            return lastIndex;

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error getting last CPU core index", e);
            return -1;
        }
    }

    /**
     * 备用方法：读取CPU当前频率
     */
    private int readFallbackCpuFreq() {
        try {
            // 尝试读取cpu0的当前频率
            String curFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            if (curFreqStr != null && !curFreqStr.isEmpty()) {
                int freq = Integer.parseInt(curFreqStr.trim());
                log("CpuFrequencyFix: Fallback current freq: " + freq);
                return freq;
            }

            // 尝试读取cpuinfo_cur_freq
            String infoCurFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq");
            if (infoCurFreqStr != null && !infoCurFreqStr.isEmpty()) {
                int freq = Integer.parseInt(infoCurFreqStr.trim());
                log("CpuFrequencyFix: Fallback cpuinfo current freq: " + freq);
                return freq;
            }

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error in fallback current freq reading", e);
        }

        log("CpuFrequencyFix: Using default current freq: 2000000");
        return 2000000; // 默认2.0GHz
    }

    /**
     * 备用方法：读取CPU最大频率
     */
    private int readFallbackCpuMaxFreq() {
        try {
            // 尝试读取cpu0的最大频率
            String maxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
            if (maxFreqStr != null && !maxFreqStr.isEmpty()) {
                int freq = Integer.parseInt(maxFreqStr.trim());
                log("CpuFrequencyFix: Fallback max freq: " + freq);
                return freq;
            }

            // 尝试读取cpuinfo_max_freq
            String infoMaxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (infoMaxFreqStr != null && !infoMaxFreqStr.isEmpty()) {
                int freq = Integer.parseInt(infoMaxFreqStr.trim());
                log("CpuFrequencyFix: Fallback cpuinfo max freq: " + freq);
                return freq;
            }

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error in fallback max freq reading", e);
        }

        log("CpuFrequencyFix: Using default max freq: 3000000");
        return 3000000; // 默认3.0GHz
    }

    /**
     * 读取系统文件内容
     */
    private String readSystemFile(String filePath) {
        BufferedReader reader = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log("CpuFrequencyFix: File does not exist: " + filePath);
                return null;
            }

            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line;

        } catch (Exception e) {
            logError("CpuFrequencyFix: Error reading file " + filePath, e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
