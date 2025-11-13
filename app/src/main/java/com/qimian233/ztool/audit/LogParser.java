// LogParser.java
package com.qimian233.ztool.audit;

import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * 日志解析器 - 增强版支持多行错误堆栈合并和所有文件读取
 */
public class LogParser {
    private static final String TAG = "LogParser";

    // 日志级别
    public enum LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR, UNKNOWN
    }

    // 日志条目数据结构
    public static class LogEntry {
        public String timestamp;
        public String mode;
        public String originalTimestamp;
        public String level;
        public String tag;
        public int pid = -1;
        public String message;
        public String module;
        public String function;
        public Map<String, String> extractedData;
        public LogLevel logLevel;
        public boolean isMultiLine = false; // 标记是否为多行日志
        public List<String> originalLines = new ArrayList<>(); // 保存原始行

        public LogEntry() {
            extractedData = new HashMap<>();
            originalLines = new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.format("[%s] [%s] %s/%s(%d): %s",
                    timestamp, mode, level, tag, pid, message);
        }

        /**
         * 获取完整的消息（包含多行）
         */
        public String getFullMessage() {
            if (originalLines.size() <= 1) {
                return message;
            }
            StringBuilder fullMessage = new StringBuilder();
            for (String line : originalLines) {
                fullMessage.append(line).append("\n");
            }
            return fullMessage.toString().trim();
        }
    }

    // 模块模式定义 - 完整模块列表
    private static final Map<String, Pattern> MODULE_PATTERNS = new HashMap<>();
    private static final Map<String, String> MODULE_NAMES = new HashMap<>();

    static {
        // 初始化所有模块的正则模式
        // 系统UI相关模块
        MODULE_PATTERNS.put("notification_icon_limit",
                Pattern.compile("\\[notification_icon_limit\\]\\s*(.*)"));
        MODULE_PATTERNS.put("systemUI_RealWatts",
                Pattern.compile("\\[systemUI_RealWatts\\]\\s*(.*)"));
        MODULE_PATTERNS.put("systemui_charge_watts",
                Pattern.compile("\\[systemui_charge_watts\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Custom_StatusBarClock",
                Pattern.compile("\\[Custom_StatusBarClock\\]\\s*(.*)"));
        MODULE_PATTERNS.put("StatusBarDisplay_Seconds",
                Pattern.compile("\\[StatusBarDisplay_Seconds\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Custom_ControlCenterDate",
                Pattern.compile("\\[Custom_ControlCenterDate\\]\\s*(.*)"));
        MODULE_PATTERNS.put("NativeNotificationIcon",
                Pattern.compile("\\[NativeNotificationIcon\\]\\s*(.*)"));
        MODULE_PATTERNS.put("No_ChargeAnimation",
                Pattern.compile("\\[No_ChargeAnimation\\]\\s*(.*)"));

        // 游戏工具相关模块
        MODULE_PATTERNS.put("Fix_CpuClock",
                Pattern.compile("\\[Fix_CpuClock\\]\\s*(.*)"));
        MODULE_PATTERNS.put("disguise_TB322FC",
                Pattern.compile("\\[disguise_TB322FC\\]\\s*(.*)"));
        MODULE_PATTERNS.put("disable_GameAudio",
                Pattern.compile("\\[disable_GameAudio\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Fix_SocTemp",
                Pattern.compile("\\[Fix_SocTemp\\]\\s*(.*)"));

        // 包安装器相关模块
        MODULE_PATTERNS.put("disable_OtaCheck",
                Pattern.compile("\\[disable_OtaCheck\\]\\s*(.*)"));
        MODULE_PATTERNS.put("disable_installerAD",
                Pattern.compile("\\[disable_installerAD\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Skip_WarnPage",
                Pattern.compile("\\[Skip_WarnPage\\]\\s*(.*)"));
        MODULE_PATTERNS.put("disable_scanAPK",
                Pattern.compile("\\[disable_scanAPK\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Always_AllowPermission",
                Pattern.compile("\\[Always_AllowPermission\\]\\s*(.*)"));
        MODULE_PATTERNS.put("packageInstallerStyle_hook",
                Pattern.compile("\\[packageInstallerStyle_hook\\]\\s*(.*)"));

        // 设置相关模块
        MODULE_PATTERNS.put("allow_display_dolby",
                Pattern.compile("\\[allow_display_dolby\\]\\s*(.*)"));
        MODULE_PATTERNS.put("auto_owner_info",
                Pattern.compile("\\[auto_owner_info\\]\\s*(.*)"));
        MODULE_PATTERNS.put("PermissionControllerHook",
                Pattern.compile("\\[PermissionControllerHook\\]\\s*(.*)"));
        MODULE_PATTERNS.put("Split_Screen_mandatory",
                Pattern.compile("\\[Split_Screen_mandatory\\]\\s*(.*)"));
        MODULE_PATTERNS.put("remove_blacklist",
                Pattern.compile("\\[remove_blacklist\\]\\s*(.*)"));


        // 模块显示名称映射
        // 系统UI相关
        MODULE_NAMES.put("notification_icon_limit", "通知图标限制");
        MODULE_NAMES.put("systemUI_RealWatts", "实时充电功率");
        MODULE_NAMES.put("systemui_charge_watts", "充电瓦数显示");
        MODULE_NAMES.put("Custom_StatusBarClock", "状态栏时钟");
        MODULE_NAMES.put("StatusBarDisplay_Seconds", "秒数显示");
        MODULE_NAMES.put("Custom_ControlCenterDate", "控制中心日期");
        MODULE_NAMES.put("NativeNotificationIcon", "原生通知图标");
        MODULE_NAMES.put("No_ChargeAnimation", "无充电动画");

        // 游戏工具相关
        MODULE_NAMES.put("Fix_CpuClock", "CPU频率修复");
        MODULE_NAMES.put("disguise_TB322FC", "设备型号伪装");
        MODULE_NAMES.put("disable_GameAudio", "禁用游戏音频");
        MODULE_NAMES.put("Fix_SocTemp", "SOC温度修复");

        // 包安装器相关
        MODULE_NAMES.put("disable_OtaCheck", "禁用OTA检查");
        MODULE_NAMES.put("disable_installerAD", "禁用安装器广告");
        MODULE_NAMES.put("Skip_WarnPage", "跳过警告页面");
        MODULE_NAMES.put("disable_scanAPK", "禁用APK扫描");
        MODULE_NAMES.put("Always_AllowPermission", "始终允许权限");
        MODULE_NAMES.put("packageInstallerStyle_hook", "包安装器样式");

        // 设置相关
        MODULE_NAMES.put("allow_display_dolby", "杜比显示");
        MODULE_NAMES.put("auto_owner_info", "自动Owner信息");
        MODULE_NAMES.put("PermissionControllerHook", "权限控制器");
        MODULE_NAMES.put("Split_Screen_mandatory", "强制分屏");
        MODULE_NAMES.put("remove_blacklist", "移除黑名单");
        MODULE_NAMES.put("keep_rotation", "保持屏幕方向");

        // 启动器相关
        MODULE_NAMES.put("disable_force_stop", "禁止划卡杀后台");

        // 系统框架相关
        MODULE_PATTERNS.put("keep_rotation",
                Pattern.compile("\\[keep_rotation\\]\\s*(.*)"));
        MODULE_NAMES.put("allow_get_packages", "停用系统应用列表管理");
    }

    /**
     * 解析单行日志
     */
    public static LogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        LogEntry entry = new LogEntry();

        try {
            // 解析增强的日志格式: [timestamp] [mode] original_log_line
            Pattern enhancedPattern = Pattern.compile(
                    "^\\[(.+?)\\]\\s+\\[(.+?)\\]\\s+(.+)$");
            Matcher enhancedMatcher = enhancedPattern.matcher(line);

            if (enhancedMatcher.find()) {
                entry.timestamp = enhancedMatcher.group(1).trim();
                entry.mode = enhancedMatcher.group(2).trim();
                String originalLine = enhancedMatcher.group(3).trim();
                entry.originalLines.add(originalLine);

                // 解析原始日志行
                parseOriginalLogLine(originalLine, entry);

                // 提取模块信息
                extractModuleInfo(entry);

                // 提取特定数据
                extractSpecificData(entry);

                // 确定日志级别
                determineLogLevel(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析日志行失败: " + line, e);
        }

        return entry;
    }

    /**
     * 解析原始日志行
     */
    private static void parseOriginalLogLine(String line, LogEntry entry) {
        // Android logcat 格式: MM-DD HH:MM:SS.mmm Level/Tag(PID): Message
        Pattern logPattern = Pattern.compile(
                "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+" +
                        "(V|D|I|W|E|F|S)/(.+?)\\((\\s*\\d+)\\):\\s*(.*)$");

        Matcher matcher = logPattern.matcher(line);

        if (matcher.find()) {
            entry.originalTimestamp = matcher.group(1).trim();
            entry.level = matcher.group(2).trim();
            entry.tag = matcher.group(3).trim();

            try {
                entry.pid = Integer.parseInt(matcher.group(4).trim());
            } catch (NumberFormatException e) {
                entry.pid = -1;
            }

            entry.message = matcher.group(5).trim();
        } else {
            // 如果不匹配标准格式，将整行作为消息
            entry.message = line;
            entry.level = "I";
            entry.tag = "Unknown";
        }
    }

    /**
     * 提取模块信息
     */
    private static void extractModuleInfo(LogEntry entry) {
        if (entry.message == null) return;

        for (Map.Entry<String, Pattern> moduleEntry : MODULE_PATTERNS.entrySet()) {
            String moduleKey = moduleEntry.getKey();
            Pattern pattern = moduleEntry.getValue();
            Matcher matcher = pattern.matcher(entry.message);

            if (matcher.find()) {
                entry.module = moduleKey;
                // 提取函数信息
                extractFunctionInfo(entry, matcher.group(1));
                break;
            }
        }
    }

    /**
     * 提取函数信息
     */
    private static void extractFunctionInfo(LogEntry entry, String content) {
        if (content == null) return;

        // 增强的函数名模式，支持更多操作类型
        Pattern functionPattern = Pattern.compile(
                "(Successfully|Failed to|开始|修改|成功|失败|执行|更新|添加|读取|拦截|Hook|清除|设置|修复|伪装|禁用|跳过|允许|强制)\\s+(hook|Hook|执行|修改|更新|添加|读取|拦截|清除|设置|修复|伪装|禁用|跳过|允许|强制)?\\s*([\\w]+)");
        Matcher matcher = functionPattern.matcher(content);

        if (matcher.find()) {
            String action = matcher.group(1);
            String function = matcher.group(3);
            entry.function = action + " " + function;
        } else {
            // 如果没有明确函数名，使用前几个词
            String[] words = content.split("\\s+");
            if (words.length > 0) {
                entry.function = words[0] + (words.length > 1 ? " " + words[1] : "");
            }
        }
    }

    /**
     * 提取特定数据 - 增强版支持所有模块
     */
    private static void extractSpecificData(LogEntry entry) {
        if (entry.message == null) return;

        // 提取充电数据
        extractChargingData(entry);

        // 提取图标限制数据
        extractIconLimitData(entry);

        // 提取温度数据
        extractTemperatureData(entry);

        // 提取频率数据
        extractFrequencyData(entry);

        // 提取权限数据
        extractPermissionData(entry);

        // 提取设备信息数据
        extractDeviceInfoData(entry);

        // 提取错误信息
        extractErrorData(entry);

        // 提取成功信息
        extractSuccessData(entry);
    }

    private static void extractChargingData(LogEntry entry) {
        if (entry.message.contains("充电") || entry.message.contains("瓦数") ||
                entry.message.contains("功率") || entry.message.contains("电流") ||
                entry.message.contains("电压")) {

            // 提取功率（支持多种格式）
            Pattern powerPattern = Pattern.compile("(\\d+\\.?\\d*)\\s*W");
            Matcher matcher = powerPattern.matcher(entry.message);
            if (matcher.find()) {
                entry.extractedData.put("power", matcher.group(1) + "W");
            }

            // 提取电流
            Pattern currentPattern = Pattern.compile("电流:\\s*([\\d.-]+)(mA|A)");
            Matcher currentMatcher = currentPattern.matcher(entry.message);
            if (currentMatcher.find()) {
                entry.extractedData.put("current", currentMatcher.group(1) + currentMatcher.group(2));
            }

            // 提取电压
            Pattern voltagePattern = Pattern.compile("电压:\\s*([\\d.-]+)V");
            Matcher voltageMatcher = voltagePattern.matcher(entry.message);
            if (voltageMatcher.find()) {
                entry.extractedData.put("voltage", voltageMatcher.group(1) + "V");
            }

            // 提取充电状态
            if (entry.message.contains("充电中") || entry.message.contains("Charging")) {
                entry.extractedData.put("charging_status", "charging");
            } else if (entry.message.contains("充满") || entry.message.contains("Full")) {
                entry.extractedData.put("charging_status", "full");
            }
        }
    }

    private static void extractIconLimitData(LogEntry entry) {
        if (entry.message.contains("图标限制") || entry.message.contains("maxIcons")) {
            Pattern pattern = Pattern.compile("(\\d+)\\s*->\\s*(\\d+).*?\\(图标总数:\\s*(\\d+)\\)");
            Matcher matcher = pattern.matcher(entry.message);
            if (matcher.find()) {
                entry.extractedData.put("from_limit", matcher.group(1));
                entry.extractedData.put("to_limit", matcher.group(2));
                entry.extractedData.put("current_icons", matcher.group(3));
            }
        }
    }

    private static void extractTemperatureData(LogEntry entry) {
        if (entry.message.contains("温度") || entry.message.contains("temp") ||
                entry.message.contains("thermal")) {

            Pattern tempPattern = Pattern.compile("(\\d+)\\s*°?C");
            Matcher matcher = tempPattern.matcher(entry.message);
            if (matcher.find()) {
                entry.extractedData.put("temperature", matcher.group(1) + "°C");
            }

            // 提取温度变化
            if (entry.message.contains("->") || entry.message.contains("原始") || entry.message.contains("新值")) {
                Pattern changePattern = Pattern.compile("(\\d+)\\s*->\\s*(\\d+)");
                Matcher changeMatcher = changePattern.matcher(entry.message);
                if (changeMatcher.find()) {
                    entry.extractedData.put("temp_original", changeMatcher.group(1));
                    entry.extractedData.put("temp_new", changeMatcher.group(2));
                }
            }
        }
    }

    private static void extractFrequencyData(LogEntry entry) {
        if (entry.message.contains("频率") || entry.message.contains("freq") ||
                entry.message.contains("MHz") || entry.message.contains("GHz")) {

            Pattern freqPattern = Pattern.compile("(\\d+)\\s*(MHz|GHz)");
            Matcher matcher = freqPattern.matcher(entry.message);
            if (matcher.find()) {
                entry.extractedData.put("frequency", matcher.group(1) + matcher.group(2));
            }

            // 提取核心信息
            if (entry.message.contains("core") || entry.message.contains("核心")) {
                Pattern corePattern = Pattern.compile("core\\s*(\\d+)");
                Matcher coreMatcher = corePattern.matcher(entry.message);
                if (coreMatcher.find()) {
                    entry.extractedData.put("core_index", coreMatcher.group(1));
                }
            }
        }
    }

    private static void extractPermissionData(LogEntry entry) {
        if (entry.message.contains("权限") || entry.message.contains("permission") ||
                entry.message.contains("允许") || entry.message.contains("拒绝")) {

            if (entry.message.contains("成功") || entry.message.contains("允许") || entry.message.contains("granted")) {
                entry.extractedData.put("permission_result", "granted");
            } else if (entry.message.contains("失败") || entry.message.contains("拒绝") || entry.message.contains("denied")) {
                entry.extractedData.put("permission_result", "denied");
            }

            // 提取权限类型
            Pattern permPattern = Pattern.compile("(READ|WRITE|ACCESS|MANAGE)_([A-Z_]+)");
            Matcher permMatcher = permPattern.matcher(entry.message);
            if (permMatcher.find()) {
                entry.extractedData.put("permission_type", permMatcher.group(0));
            }
        }
    }

    private static void extractDeviceInfoData(LogEntry entry) {
        if (entry.message.contains("设备") || entry.message.contains("device") ||
                entry.message.contains("型号") || entry.message.contains("model")) {

            // 提取设备型号
            Pattern modelPattern = Pattern.compile("型号[:：]\\s*([\\w]+)");
            Matcher modelMatcher = modelPattern.matcher(entry.message);
            if (modelMatcher.find()) {
                entry.extractedData.put("device_model", modelMatcher.group(1));
            }

            // 提取伪装信息
            if (entry.message.contains("伪装") || entry.message.contains("disguise")) {
                entry.extractedData.put("device_disguised", "true");
            }
        }
    }

    private static void extractErrorData(LogEntry entry) {
        if (entry.message.contains("Failed") || entry.message.contains("错误") ||
                entry.message.contains("失败") || entry.message.contains("异常") ||
                entry.level.equals("E")) {
            entry.extractedData.put("is_error", "true");

            // 提取错误类型
            if (entry.message.contains("NullPointer")) {
                entry.extractedData.put("error_type", "NullPointerException");
            } else if (entry.message.contains("ClassNotFound")) {
                entry.extractedData.put("error_type", "ClassNotFoundException");
            } else if (entry.message.contains("IOException")) {
                entry.extractedData.put("error_type", "IOException");
            }
        }
    }

    private static void extractSuccessData(LogEntry entry) {
        if (entry.message.contains("Successfully") || entry.message.contains("成功") ||
                entry.message.contains("成功添加") || entry.message.contains("更新成功") ||
                entry.message.contains("Hook成功") || entry.message.contains("拦截成功")) {
            entry.extractedData.put("is_success", "true");
        }
    }

    /**
     * 确定日志级别
     */
    private static void determineLogLevel(LogEntry entry) {
        if (entry.level != null) {
            switch (entry.level) {
                case "V": entry.logLevel = LogLevel.VERBOSE; break;
                case "D": entry.logLevel = LogLevel.DEBUG; break;
                case "I": entry.logLevel = LogLevel.INFO; break;
                case "W": entry.logLevel = LogLevel.WARN; break;
                case "E": entry.logLevel = LogLevel.ERROR; break;
                default: entry.logLevel = LogLevel.UNKNOWN;
            }
        } else {
            entry.logLevel = LogLevel.UNKNOWN;
        }
    }

    /**
     * 获取模块的显示名称
     */
    public static String getModuleDisplayName(String moduleKey) {
        return MODULE_NAMES.getOrDefault(moduleKey, moduleKey);
    }

    /**
     * 获取所有可用模块
     */
    public static List<String> getAvailableModules() {
        return new ArrayList<>(MODULE_NAMES.keySet());
    }

    /**
     * 按类别获取模块
     */
    public static Map<String, List<String>> getModulesByCategory() {
        Map<String, List<String>> categories = new HashMap<>();

        // 系统UI相关
        categories.put("系统UI", Arrays.asList(
                "notification_icon_limit", "systemUI_RealWatts", "systemui_charge_watts",
                "Custom_StatusBarClock", "StatusBarDisplay_Seconds", "Custom_ControlCenterDate",
                "NativeNotificationIcon", "No_ChargeAnimation"
        ));

        // 游戏工具相关
        categories.put("游戏工具", Arrays.asList(
                "Fix_CpuClock", "disguise_TB322FC", "disable_GameAudio", "Fix_SocTemp"
        ));

        // 包安装器相关
        categories.put("包安装器", Arrays.asList(
                "disable_OtaCheck", "disable_installerAD", "Skip_WarnPage",
                "disable_scanAPK", "Always_AllowPermission", "packageInstallerStyle_hook"
        ));

        // 设置相关
        categories.put("系统设置", Arrays.asList(
                "allow_display_dolby", "auto_owner_info", "PermissionControllerHook",
                "Split_Screen_mandatory", "remove_blacklist"
        ));

        // 启动器相关
        categories.put("系统桌面", Arrays.asList(
                "disable_force_stop"
        ));

        // 系统框架相关
        categories.put("系统框架", Arrays.asList(
                "allow_get_packages", "keep_rotation"
        ));

        return categories;
    }

    /**
     * 解析整个日志文件 - 增强版支持多行错误堆栈合并
     */
    public static List<LogEntry> parseLogFile(File logFile) {
        List<LogEntry> entries = new ArrayList<>();

        if (logFile == null || !logFile.exists()) {
            Log.w(TAG, "日志文件不存在: " + logFile);
            return entries;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            LogEntry currentEntry = null;

            while ((line = reader.readLine()) != null) {
                // 解析增强格式
                Pattern enhancedPattern = Pattern.compile("^\\[(.+?)\\]\\s+\\[(.+?)\\]\\s+(.+)$");
                Matcher enhancedMatcher = enhancedPattern.matcher(line);

                if (enhancedMatcher.find()) {
                    String timestamp = enhancedMatcher.group(1).trim();
                    String mode = enhancedMatcher.group(2).trim();
                    String originalLine = enhancedMatcher.group(3).trim();

                    // 检查是否包含模块标识
                    boolean hasModule = false;
                    String detectedModule = null;

                    for (Map.Entry<String, Pattern> moduleEntry : MODULE_PATTERNS.entrySet()) {
                        Pattern pattern = moduleEntry.getValue();
                        Matcher matcher = pattern.matcher(originalLine);
                        if (matcher.find()) {
                            hasModule = true;
                            detectedModule = moduleEntry.getKey();
                            break;
                        }
                    }

                    if (hasModule) {
                        // 如果当前有正在构建的条目，先保存
                        if (currentEntry != null) {
                            entries.add(currentEntry);
                        }

                        // 创建新的日志条目
                        currentEntry = new LogEntry();
                        currentEntry.timestamp = timestamp;
                        currentEntry.mode = mode;
                        currentEntry.originalLines.add(originalLine);

                        // 解析原始日志行
                        parseOriginalLogLine(originalLine, currentEntry);

                        // 提取模块信息
                        currentEntry.module = detectedModule;
                        extractFunctionInfo(currentEntry, originalLine.replaceFirst("\\[" + detectedModule + "\\]\\s*", ""));

                        // 提取特定数据
                        extractSpecificData(currentEntry);

                        // 确定日志级别
                        determineLogLevel(currentEntry);

                    } else if (currentEntry != null) {
                        // 没有模块标识，但是有当前条目，说明是多行日志的延续
                        currentEntry.originalLines.add(originalLine);
                        currentEntry.isMultiLine = true;

                        // 更新消息为完整的多行消息
                        StringBuilder fullMessage = new StringBuilder();
                        for (String origLine : currentEntry.originalLines) {
                            fullMessage.append(origLine).append("\n");
                        }
                        currentEntry.message = fullMessage.toString().trim();

                        // 重新提取错误信息（因为消息已更新）
                        extractErrorData(currentEntry);
                    } else {
                        // 没有模块标识且没有当前条目，创建新的独立条目
                        LogEntry entry = parseLine(line);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                } else {
                    // 不匹配增强格式的行，尝试作为独立行解析
                    LogEntry entry = parseLine(line);
                    if (entry != null) {
                        if (currentEntry != null) {
                            entries.add(currentEntry);
                            currentEntry = null;
                        }
                        entries.add(entry);
                    }
                }
            }

            // 保存最后一个条目
            if (currentEntry != null) {
                entries.add(currentEntry);
            }

            Log.d(TAG, "成功解析日志文件，共 " + entries.size() + " 条记录");
        } catch (IOException e) {
            Log.e(TAG, "读取日志文件失败: " + logFile, e);
        }

        return entries;
    }

    /**
     * 解析所有日志文件
     */
    public static List<LogEntry> parseAllLogFiles(File logDir) {
        List<LogEntry> allEntries = new ArrayList<>();

        if (logDir == null || !logDir.exists() || !logDir.isDirectory()) {
            Log.w(TAG, "日志目录不存在: " + logDir);
            return allEntries;
        }

        // 获取所有日志文件
        File[] logFiles = logDir.listFiles((dir, name) ->
                name.startsWith("hook_log_") && name.endsWith(".txt"));

        if (logFiles == null || logFiles.length == 0) {
            Log.w(TAG, "未找到日志文件");
            return allEntries;
        }

        // 按文件名排序（时间顺序）
        Arrays.sort(logFiles, (f1, f2) -> {
            try {
                // 从文件名提取时间戳进行比较
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String time1 = extractTimeFromFilename(f1.getName());
                String time2 = extractTimeFromFilename(f2.getName());
                Date date1 = sdf.parse(time1);
                Date date2 = sdf.parse(time2);
                return date1.compareTo(date2);
            } catch (Exception e) {
                return f1.getName().compareTo(f2.getName());
            }
        });

        // 解析所有文件
        for (File logFile : logFiles) {
            Log.d(TAG, "解析日志文件: " + logFile.getName());
            List<LogEntry> fileEntries = parseLogFile(logFile);
            allEntries.addAll(fileEntries);
        }

        Log.d(TAG, "总共解析 " + allEntries.size() + " 条日志记录");
        return allEntries;
    }

    /**
     * 从文件名中提取时间戳
     */
    private static String extractTimeFromFilename(String filename) {
        // 文件名格式: hook_log_root_20251112_144325.txt
        Pattern pattern = Pattern.compile("hook_log_root_(\\d{8}_\\d{6})\\.txt");
        Matcher matcher = pattern.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 过滤日志条目 - 增强版支持模块分类
     */
    public static List<LogEntry> filterEntries(List<LogEntry> entries,
                                               String moduleFilter,
                                               LogLevel levelFilter,
                                               String searchText,
                                               String categoryFilter) {
        List<LogEntry> filtered = new ArrayList<>();

        // 如果指定了类别过滤，先获取该类别下的所有模块
        Set<String> categoryModules = new HashSet<>();
        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            Map<String, List<String>> categories = getModulesByCategory();
            if (categories.containsKey(categoryFilter)) {
                categoryModules.addAll(categories.get(categoryFilter));
            }
        }

        for (LogEntry entry : entries) {
            boolean matches = true;

            // 模块过滤
            if (moduleFilter != null && !moduleFilter.isEmpty()) {
                if (entry.module == null || !entry.module.equals(moduleFilter)) {
                    matches = false;
                }
            }

            // 类别过滤
            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                if (entry.module == null || !categoryModules.contains(entry.module)) {
                    matches = false;
                }
            }

            // 级别过滤
            if (levelFilter != null && levelFilter != LogLevel.UNKNOWN) {
                if (entry.logLevel != levelFilter) {
                    matches = false;
                }
            }

            // 文本搜索 - 搜索完整消息（包含多行）
            if (searchText != null && !searchText.isEmpty()) {
                String searchLower = searchText.toLowerCase();
                String fullMessage = entry.getFullMessage().toLowerCase();
                boolean textMatch = fullMessage.contains(searchLower) ||
                        (entry.tag != null && entry.tag.toLowerCase().contains(searchLower)) ||
                        (entry.module != null && entry.module.toLowerCase().contains(searchLower)) ||
                        (entry.function != null && entry.function.toLowerCase().contains(searchLower));
                if (!textMatch) {
                    matches = false;
                }
            }

            if (matches) {
                filtered.add(entry);
            }
        }

        return filtered;
    }

    /**
     * 统计模块日志数量
     */
    public static Map<String, Integer> getModuleStats(List<LogEntry> entries) {
        Map<String, Integer> stats = new HashMap<>();
        for (LogEntry entry : entries) {
            if (entry.module != null) {
                stats.put(entry.module, stats.getOrDefault(entry.module, 0) + 1);
            }
        }
        return stats;
    }

    /**
     * 获取错误统计
     */
    public static Map<String, Object> getErrorStats(List<LogEntry> entries) {
        Map<String, Object> stats = new HashMap<>();
        int totalErrors = 0;
        Map<String, Integer> errorByModule = new HashMap<>();
        Map<String, Integer> errorByType = new HashMap<>();

        for (LogEntry entry : entries) {
            if ("true".equals(entry.extractedData.get("is_error"))) {
                totalErrors++;

                // 按模块统计
                if (entry.module != null) {
                    errorByModule.put(entry.module, errorByModule.getOrDefault(entry.module, 0) + 1);
                }

                // 按错误类型统计
                String errorType = entry.extractedData.get("error_type");
                if (errorType != null) {
                    errorByType.put(errorType, errorByType.getOrDefault(errorType, 0) + 1);
                }
            }
        }

        stats.put("total_errors", totalErrors);
        stats.put("errors_by_module", errorByModule);
        stats.put("errors_by_type", errorByType);

        return stats;
    }
}
