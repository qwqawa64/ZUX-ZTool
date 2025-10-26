package com.qimian233.ztool.hook.modules.systemui;

import android.util.Log;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义日期格式化工具类
 * 支持农历、节气、时辰等特殊格式
 */
public class CustomDateFormatter {

    private static final String TAG = "CustomDateFormatter";

    // 时辰对照表
    private static final String[] CHINESE_HOURS = {
            "子时", "丑时", "寅时", "卯时", "辰时", "巳时",
            "午时", "未时", "申时", "酉时", "戌时", "亥时"
    };

    // 地支对照表
    private static final String[] EARTHLY_BRANCHES = {
            "子", "丑", "寅", "卯", "辰", "巳",
            "午", "未", "申", "酉", "戌", "亥"
    };

    // 自定义格式模式映射
    private static final Map<String, String> CUSTOM_PATTERNS = new HashMap<>();

    static {
        // 初始化自定义模式映射
        CUSTOM_PATTERNS.put("N", "lunar");           // 农历日期
        CUSTOM_PATTERNS.put("J", "solarTerm");       // 节气
        CUSTOM_PATTERNS.put("T", "chineseHour");     // 时辰
        CUSTOM_PATTERNS.put("C", "constellation");   // 星座
        CUSTOM_PATTERNS.put("A", "animal");          // 生肖
        CUSTOM_PATTERNS.put("W", "week");            // 星期
    }

    /**
     * 格式化日期，支持自定义农历、节气等格式
     *
     * @param pattern 格式模式，支持以下自定义占位符：
     *                N - 农历日期（如：腊月廿三）
     *                J - 节气（如：立春）
     *                T - 时辰（如：子时）
     *                C - 星座（如：水瓶座）
     *                A - 生肖（如：龙）
     *                W - 星期（如：星期一）
     *                同时支持标准的SimpleDateFormat格式
     *
     * @param date 要格式化的日期
     * @return 格式化后的字符串
     */
    public static String format(String pattern, Date date) {
        if (pattern == null || date == null) {
            return "";
        }

        try {
            // 处理自定义格式
            String result = processCustomPatterns(pattern, date);

            // 处理标准的SimpleDateFormat格式
            result = processStandardPatterns(result, date);

            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + e.getMessage(), e);
            // 出错时返回默认格式
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        }
    }

    /**
     * 处理自定义模式（农历、节气、时辰等）
     */
    private static String processCustomPatterns(String pattern, Date date) {
        String result = pattern;
        Lunar lunar = Lunar.fromDate(date);

        // 逐个处理自定义占位符
        for (Map.Entry<String, String> entry : CUSTOM_PATTERNS.entrySet()) {
            String placeholder = entry.getKey();
            String type = entry.getValue();

            if (result.contains(placeholder)) {
                String replacement = getCustomReplacement(type, lunar, date);
                result = result.replace(placeholder, replacement);
            }
        }

        return result;
    }

    /**
     * 处理标准SimpleDateFormat格式
     */
    private static String processStandardPatterns(String pattern, Date date) {
        // 如果已经没有自定义占位符，直接格式化
        if (!containsCustomPatterns(pattern)) {
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
        }

        // 使用正则表达式匹配并替换标准格式部分
        StringBuilder result = new StringBuilder();
        Pattern stdPattern = Pattern.compile("([^a-zA-Z]|^)([yMdHhmsSEDFwWkKzZ]+)([^a-zA-Z]|$)");
        Matcher matcher = stdPattern.matcher(pattern);

        int lastEnd = 0;
        while (matcher.find()) {
            // 添加非格式部分
            result.append(pattern, lastEnd, matcher.start(2));

            // 格式化标准部分
            String stdFormat = matcher.group(2);
            String formatted = new SimpleDateFormat(stdFormat, Locale.getDefault()).format(date);
            result.append(formatted);

            lastEnd = matcher.end(2);
        }

        // 添加剩余部分
        result.append(pattern.substring(lastEnd));

        return result.toString();
    }

    /**
     * 获取自定义占位符的替换内容
     */
    private static String getCustomReplacement(String type, Lunar lunar, Date date) {
        switch (type) {
            case "lunar":
                return getLunarDate(lunar);
            case "solarTerm":
                return getSolarTerm(lunar);
            case "chineseHour":
                return getChineseHour(date);
            case "constellation":
                return getConstellation(lunar);
            case "animal":
                return lunar.getYearShengXiao();
            case "week":
                return getChineseWeek(date);
            default:
                return "";
        }
    }

    /**
     * 获取农历日期
     */
    private static String getLunarDate(Lunar lunar) {
        try {
            // 格式：腊月廿三
            return lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
        } catch (Exception e) {
            Log.e(TAG, "Error getting lunar date", e);
            return "";
        }
    }

    /**
     * 获取节气
     */
    private static String getSolarTerm(Lunar lunar) {
        try {
            String solarTerm = lunar.getJieQi();
            return solarTerm != null ? solarTerm : "";
        } catch (Exception e) {
            Log.e(TAG, "Error getting solar term", e);
            return "";
        }
    }

    /**
     * 获取时辰
     */
    private static String getChineseHour(Date date) {
        try {
            SimpleDateFormat hourFormat = new SimpleDateFormat("HH", Locale.getDefault());
            int hour = Integer.parseInt(hourFormat.format(date));

            // 计算时辰（每2小时一个时辰）
            int hourIndex = (hour + 1) / 2 % 12;
            return CHINESE_HOURS[hourIndex];

        } catch (Exception e) {
            Log.e(TAG, "Error getting Chinese hour", e);
            return "";
        }
    }

    /**
     * 获取星座
     */
    private static String getConstellation(Lunar lunar) {
        try {
            Solar solar = lunar.getSolar();
            int month = solar.getMonth();
            int day = solar.getDay();

            return getConstellationBySolarDate(month, day);

        } catch (Exception e) {
            Log.e(TAG, "Error getting constellation", e);
            return "";
        }
    }

    /**
     * 根据公历日期获取星座
     */
    private static String getConstellationBySolarDate(int month, int day) {
        if ((month == 1 && day >= 20) || (month == 2 && day <= 18)) return "水瓶座";
        if ((month == 2 && day >= 19) || (month == 3 && day <= 20)) return "双鱼座";
        if ((month == 3 && day >= 21) || (month == 4 && day <= 19)) return "白羊座";
        if ((month == 4 && day >= 20) || (month == 5 && day <= 20)) return "金牛座";
        if ((month == 5 && day >= 21) || (month == 6 && day <= 21)) return "双子座";
        if ((month == 6 && day >= 22) || (month == 7 && day <= 22)) return "巨蟹座";
        if ((month == 7 && day >= 23) || (month == 8 && day <= 22)) return "狮子座";
        if ((month == 8 && day >= 23) || (month == 9 && day <= 22)) return "处女座";
        if ((month == 9 && day >= 23) || (month == 10 && day <= 23)) return "天秤座";
        if ((month == 10 && day >= 24) || (month == 11 && day <= 22)) return "天蝎座";
        if ((month == 11 && day >= 23) || (month == 12 && day <= 21)) return "射手座";
        if ((month == 12 && day >= 22) || (month == 1 && day <= 19)) return "摩羯座";
        return "";
    }

    /**
     * 获取中文星期
     */
    private static String getChineseWeek(Date date) {
        try {
            SimpleDateFormat weekFormat = new SimpleDateFormat("E", Locale.CHINA);
            String week = weekFormat.format(date);
            return week.replace("星期", "周"); // 统一格式为"周一"
        } catch (Exception e) {
            Log.e(TAG, "Error getting Chinese week", e);
            return "";
        }
    }

    /**
     * 检查是否包含自定义模式
     */
    private static boolean containsCustomPatterns(String pattern) {
        for (String placeholder : CUSTOM_PATTERNS.keySet()) {
            if (pattern.contains(placeholder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有支持的自定义格式说明
     */
    public static String getSupportedFormats() {
        return "支持的自定义格式:\n" +
                "N - 农历日期 (如: 腊月廿三)\n" +
                "J - 节气 (如: 立春)\n" +
                "T - 时辰 (如: 子时)\n" +
                "C - 星座 (如: 水瓶座)\n" +
                "A - 生肖 (如: 龙)\n" +
                "W - 星期 (如: 周一)\n" +
                "同时支持标准日期格式: yyyy, MM, dd, HH, mm, ss等";
    }
}