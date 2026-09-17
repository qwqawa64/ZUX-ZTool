package com.qimian233.ztool.hook.modules.systemui.misc

import android.util.Log
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Custom date formatter utility.
 * Supports special formats such as lunar calendar, solar terms, Chinese hours, and time periods.
 */
object CustomDateFormatter {

    private const val TAG = "CustomDateFormatter"

    // Chinese traditional hour (shichen) lookup table
    private val CHINESE_HOURS = arrayOf(
        "子时", "丑时", "寅时", "卯时", "辰时", "巳时",
        "午时", "未时", "申时", "酉时", "戌时", "亥时"
    )

    // Custom format pattern mapping
    private val CUSTOM_PATTERNS = mapOf(
        "N" to "lunar",           // lunar date
        "J" to "solarTerm",       // solar term
        "T" to "chineseHour",     // Chinese hour (shichen)
        "C" to "constellation",   // constellation
        "A" to "animal",          // zodiac animal
        "W" to "week",            // week
        "a" to "timePeriod"       // time period
    )

    /**
     * Format the date, supporting custom lunar calendar, solar term, etc. formats.
     *
     * @param pattern format pattern; supports the following custom placeholders:
     *                N - lunar date (e.g.: 腊月廿三)
     *                J - solar term (e.g.: 立春)
     *                T - Chinese hour (e.g.: 子时)
     *                C - constellation (e.g.: 水瓶座)
     *                A - zodiac animal (e.g.: 龙)
     *                W - week (e.g.: 星期一)
     *                a - time period (e.g.: 凌晨、上午、中午、下午、晚上)
     *                Standard SimpleDateFormat patterns are also supported.
     *
     * @param date the date to format
     * @return the formatted string
     */
    fun format(pattern: String?, date: Date?): String {
        if (pattern == null || date == null) {
            return ""
        }

        return try {
            // Process custom patterns
            var result = processCustomPatterns(pattern, date)

            // Process standard SimpleDateFormat patterns
            result = processStandardPatterns(result, date)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date: " + e.message, e)
            // Return the default format on error
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
    }

    /**
     * Process custom patterns (lunar calendar, solar terms, Chinese hours, etc.)
     */
    private fun processCustomPatterns(pattern: String, date: Date): String {
        var result = pattern
        val lunar = Lunar.fromDate(date)

        // Process custom placeholders one by one
        for ((placeholder, type) in CUSTOM_PATTERNS) {
            if (result.contains(placeholder)) {
                val replacement = getCustomReplacement(type, lunar, date)
                result = result.replace(placeholder, replacement)
            }
        }

        return result
    }

    /**
     * Process standard SimpleDateFormat patterns
     */
    private fun processStandardPatterns(pattern: String, date: Date): String {
        // If no custom placeholders remain, format directly
        if (!containsCustomPatterns(pattern)) {
            return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        }

        // Match and replace standard format segments using regex
        val result = StringBuilder()
        val stdPattern = Pattern.compile("([^a-zA-Z]|^)([yMdHhmsSEDFwWkKzZ]+)([^a-zA-Z]|$)")
        val matcher = stdPattern.matcher(pattern)

        var lastEnd = 0
        while (matcher.find()) {
            // Append non-format segments
            result.append(pattern, lastEnd, matcher.start(2))

            // Format the standard segment
            val stdFormat = matcher.group(2)
            val formatted = SimpleDateFormat(stdFormat, Locale.getDefault()).format(date)
            result.append(formatted)

            lastEnd = matcher.end(2)
        }

        // Append the remaining part
        result.append(pattern.substring(lastEnd))

        return result.toString()
    }

    /**
     * Get the replacement content for a custom placeholder
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
     * Get the lunar date
     */
    private fun getLunarDate(lunar: Lunar): String {
        return try {
            // Format: 腊月廿三
            lunar.monthInChinese + "月" + lunar.dayInChinese
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lunar date", e)
            ""
        }
    }

    /**
     * Get the solar term
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
     * Get the Chinese hour (shichen)
     */
    private fun getChineseHour(date: Date): String {
        return try {
            val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
            val hour = hourFormat.format(date).toInt()

            // Compute the shichen (one shichen per 2 hours)
            val hourIndex = (hour + 1) / 2 % 12
            CHINESE_HOURS[hourIndex]
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Chinese hour", e)
            ""
        }
    }

    /**
     * Get the constellation
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
     * Get the constellation from a Gregorian date
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
     * Get the Chinese week name
     */
    private fun getChineseWeek(date: Date): String {
        return try {
            val weekFormat = SimpleDateFormat("E", Locale.CHINA)
            val week = weekFormat.format(date)
            week.replace("星期", "周") // Normalize to "周一" style
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Chinese week", e)
            ""
        }
    }

    /**
     * Get the current time period (early morning, morning, afternoon, etc.)
     */
    private fun getTimePeriod(date: Date): String {
        return try {
            val hourFormat = SimpleDateFormat("H", Locale.getDefault())
            val hour = hourFormat.format(date).toInt()

            when (hour) {
                in 0..<6 -> "凌晨"
                in 6..<9 -> "早上"
                in 9..<12 -> "上午"
                in 12..<14 -> "中午"
                in 14..<18 -> "下午"
                in 18..<24 -> "晚上"
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting time period", e)
            ""
        }
    }

    /**
     * Check whether the pattern contains custom patterns
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
