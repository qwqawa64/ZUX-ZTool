package com.qimian233.ztool.hook.modules.systemui.misc

import android.util.Log
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 自定义日期格式化工具类
 * 支持农历、节气、时辰、时间段等特殊格式
 */
object CustomDateFormatter {

    private const val TAG = "CustomDateFormatter"

    // 时辰对照表
    private val CHINESE_HOURS = arrayOf(
        "子时", "丑时", "寅时", "卯时", "辰时", "巳时",
        "午时", "未时", "申时", "酉时", "戌时", "亥时"
    )

    // 自定义格式模式映射
    private val CUSTOM_PATTERNS = mapOf(
        "N" to "lunar",           // 农历日期
        "J" to "solarTerm",       // 节气
        "T" to "chineseHour",     // 时辰
        "C" to "constellation",   // 星座
        "A" to "animal",          // 生肖
        "W" to "week",            // 星期
        "a" to "timePeriod"       // 时间段
    )

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
     *                a - 时间段（如：凌晨、上午、中午、下午、晚上）
     *                同时支持标准的SimpleDateFormat格式
     *
     * @param date 要格式化的日期
     * @return 格式化后的字符串
     */
    @JvmStatic
    fun format(pattern: String?, date: Date?): String {
        if (pattern == null || date == null) {
            return ""
        }

        return try {
            // 处理自定义格式
            var result = processCustomPatterns(pattern, date)

            // 处理标准的SimpleDateFormat格式
            result = processStandardPatterns(result, date)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date: " + e.message, e)
            // 出错时返回默认格式
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
    }

    /**
     * 处理自定义模式（农历、节气、时辰等）
     */
    private fun processCustomPatterns(pattern: String, date: Date): String {
        var result = pattern
        val lunar = Lunar.fromDate(date)

        // 逐个处理自定义占位符
        for ((placeholder, type) in CUSTOM_PATTERNS) {
            if (result.contains(placeholder)) {
                val replacement = getCustomReplacement(type, lunar, date)
                result = result.replace(placeholder, replacement)
            }
        }

        return result
    }

    /**
     * 处理标准SimpleDateFormat格式
     */
    private fun processStandardPatterns(pattern: String, date: Date): String {
        // 如果已经没有自定义占位符，直接格式化
        if (!containsCustomPatterns(pattern)) {
            return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        }

        // 使用正则表达式匹配并替换标准格式部分
        val result = StringBuilder()
        val stdPattern = Pattern.compile("([^a-zA-Z]|^)([yMdHhmsSEDFwWkKzZ]+)([^a-zA-Z]|\$)")
        val matcher = stdPattern.matcher(pattern)

        var lastEnd = 0
        while (matcher.find()) {
            // 添加非格式部分
            result.append(pattern, lastEnd, matcher.start(2))

            // 格式化标准部分
            val stdFormat = matcher.group(2)
            val formatted = SimpleDateFormat(stdFormat, Locale.getDefault()).format(date)
            result.append(formatted)

            lastEnd = matcher.end(2)
        }

        // 添加剩余部分
        result.append(pattern.substring(lastEnd))

        return result.toString()
    }

    /**
     * 获取自定义占位符的替换内容
     */
    private fun getCustomReplacement(type: String, lunar: Lunar, date: Date): String {
        return when (type) {
            "lunar" -> getLunarDate(lunar)
            "solarTerm" -> getSolarTerm(lunar)
            "chineseHour" -> getChineseHour(date)
            "constellation" -> getConstellation(lunar)
            "animal" -> lunar.yearShengXiao
            "week" -> getChineseWeek(date)
            "timePeriod" -> getTimePeriod(date)
            else -> ""
        }
    }

    /**
     * 获取农历日期
     */
    private fun getLunarDate(lunar: Lunar): String {
        return try {
            // 格式：腊月廿三
            lunar.monthInChinese + "月" + lunar.dayInChinese
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lunar date", e)
            ""
        }
    }

    /**
     * 获取节气
     */
    private fun getSolarTerm(lunar: Lunar): String {
        return try {
            val solarTerm = lunar.jieQi
            solarTerm ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting solar term", e)
            ""
        }
    }

    /**
     * 获取时辰
     */
    private fun getChineseHour(date: Date): String {
        return try {
            val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
            val hour = hourFormat.format(date).toInt()

            // 计算时辰（每2小时一个时辰）
            val hourIndex = (hour + 1) / 2 % 12
            CHINESE_HOURS[hourIndex]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Chinese hour", e)
            ""
        }
    }

    /**
     * 获取星座
     */
    private fun getConstellation(lunar: Lunar): String {
        return try {
            val solar: Solar = lunar.solar
            val month = solar.month
            val day = solar.day

            getConstellationBySolarDate(month, day)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting constellation", e)
            ""
        }
    }

    /**
     * 根据公历日期获取星座
     */
    private fun getConstellationBySolarDate(month: Int, day: Int): String {
        if (month == 1 && day >= 20 || month == 2 && day <= 18) return "水瓶座"
        if (month == 2 || month == 3 && day <= 20) return "双鱼座"
        if (month == 3 || month == 4 && day <= 19) return "白羊座"
        if (month == 4 || month == 5 && day <= 20) return "金牛座"
        if (month == 5 || month == 6 && day <= 21) return "双子座"
        if (month == 6 || month == 7 && day <= 22) return "巨蟹座"
        if (month == 7 || month == 8 && day <= 22) return "狮子座"
        if (month == 8 || month == 9 && day <= 22) return "处女座"
        if (month == 9 || month == 10 && day <= 23) return "天秤座"
        if (month == 10 || month == 11 && day <= 22) return "天蝎座"
        if (month == 11 || month == 12 && day <= 21) return "射手座"
        if (month == 12 || month == 1) return "摩羯座"
        return ""
    }

    /**
     * 获取中文星期
     */
    private fun getChineseWeek(date: Date): String {
        return try {
            val weekFormat = SimpleDateFormat("E", Locale.CHINA)
            val week = weekFormat.format(date)
            week.replace("星期", "周") // 统一格式为"周一"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Chinese week", e)
            ""
        }
    }

    /**
     * 获取当前时间段（凌晨、上午、下午等）
     */
    private fun getTimePeriod(date: Date): String {
        return try {
            val hourFormat = SimpleDateFormat("H", Locale.getDefault())
            val hour = hourFormat.format(date).toInt()

            when {
                hour >= 0 && hour < 6 -> "凌晨"
                hour >= 6 && hour < 9 -> "早上"
                hour >= 9 && hour < 12 -> "上午"
                hour >= 12 && hour < 14 -> "中午"
                hour >= 14 && hour < 18 -> "下午"
                hour >= 18 && hour < 24 -> "晚上"
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting time period", e)
            ""
        }
    }

    /**
     * 检查是否包含自定义模式
     */
    private fun containsCustomPatterns(pattern: String): Boolean {
        for (placeholder in CUSTOM_PATTERNS.keys) {
            if (pattern.contains(placeholder)) {
                return true
            }
        }
        return false
    }
}
