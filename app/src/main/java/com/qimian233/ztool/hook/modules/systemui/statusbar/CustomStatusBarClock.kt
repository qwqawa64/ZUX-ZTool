package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.modules.systemui.misc.CustomDateFormatter
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Date

/**
 * 自定义状态栏时钟Hook模块
 * 修改SystemUI状态栏时钟显示格式和样式，支持自定义时间格式、字体大小、字间距、颜色和粗体
 */
@SuppressLint("PrivateApi")
class CustomStatusBarClock : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.CUSTOM_STATUSBAR_CLOCK.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (SYSTEMUI_PACKAGE == packageName) {
            hookSystemUIClock(classLoader)
        }
    }

    private fun hookSystemUIClock(classLoader: ClassLoader) {
        try {
            // Hook Clock 类的 getSmallTime 方法
            val getSmallTimeMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("getSmallTime")
            hookWithId(getSmallTimeMethod, "get_small_time") { chain ->
                try {
                    // 检查模块是否启用
                    if (!isEnabled()) {
                        return@hookWithId chain.proceed()
                    }

                    // 获取自定义格式的时间
                    val customTime = getCustomTimeFormat()

                    // 应用所有样式到文本
                    val styledText = applyAllStyles(customTime)

                    // 返回新的值
                    logger.debug("Successfully customized status bar clock: $customTime")
                    return@hookWithId styledText
                } catch (e: Exception) {
                    logger.error("Failed to customize getSmallTime", e)
                    chain.proceed()
                }
            }

            // Hook updateClock 方法，确保内容描述和样式正确应用
            val updateClockMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateClock")
            hookWithId(updateClockMethod, "update_clock") { chain ->
                val result = chain.proceed()
                try {
                    // 检查模块是否启用
                    if (!isEnabled()) {
                        return@hookWithId result
                    }

                    val clockInstance = chain.thisObject

                    // 获取自定义时间
                    val customTime = getCustomTimeFormat()

                    // 设置内容描述（无障碍功能使用）
                    // 使用 getMethod 而非 getDeclaredMethod，因为 setContentDescription 继承自 View
                    clockInstance.javaClass.getMethod("setContentDescription", CharSequence::class.java)
                        .invoke(clockInstance, customTime)

                    // 应用直接样式（备用方案）
                    applyDirectStyles(clockInstance)

                    logger.debug("Updated clock content description: $customTime")
                } catch (e: Exception) {
                    logger.error("Failed to update clock content description", e)
                }
                result
            }

            // 额外 Hook：在视图初始化时应用样式
            val onFinishInflateMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("onFinishInflate")
            hookWithId(onFinishInflateMethod, "on_finish_inflate") { chain ->
                val result = chain.proceed()
                try {
                    if (!isEnabled()) {
                        return@hookWithId result
                    }

                    val clockInstance = chain.thisObject
                    applyDirectStyles(clockInstance)
                } catch (e: Exception) {
                    logger.error("Failed to apply styles in onFinishInflate", e)
                }
                result
            }

            logger.info("Successfully hooked SystemUI Clock methods")
        } catch (t: Throwable) {
            logger.error("Failed to hook SystemUI Clock class", t)
        }
    }

    /**
     * 自定义时间格式方法
     * 使用新的格式化工具支持农历、节气等
     */
    private fun getCustomTimeFormat(): String {
        return try {
            val format = getCustomClock(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_FORMAT.name)
            CustomDateFormatter.format(format, Date())
        } catch (e: Exception) {
            logger.error("Error in custom time formatting", e)
            // 出错时返回默认时间格式
            CustomDateFormatter.format("HH:mm", Date())
        }
    }

    /**
     * 应用所有样式到文本（主要方法）
     */
    private fun applyAllStyles(text: String): CharSequence {
        val styledText = SpannableString(text)

        // 1. 应用字体大小（仅在开关开启时应用）
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text)
        }

        // 2. 应用字间距（仅在开关开启时应用）
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text)
        }

        // 3. 应用字体颜色（仅在开关开启时应用）
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text)
        }

        // 4. 应用字体样式（粗体等，仅在开关开启时应用）
        if (isTextBoldEnabled()) {
            applyTextStyle(styledText, text)
        }

        return styledText
    }

    /**
     * 应用字体大小
     */
    private fun applyTextSize(styledText: SpannableString, text: String) {
        try {
            val textSizeSp = getTextSize()
            val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, textSizeSp,
                Resources.getSystem().displayMetrics
            ).toInt()
            styledText.setSpan(
                AbsoluteSizeSpan(textSizePx),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } catch (e: Exception) {
            logger.error("Failed to apply text size", e)
        }
    }

    /**
     * 应用字间距（兼容性方案）
     */
    private fun applyLetterSpacing(styledText: SpannableString, text: String) {
        try {
            val letterSpacing = getLetterSpacing()
            // 使用 ScaleXSpan 模拟字间距
            if (letterSpacing > 0) {
                styledText.setSpan(
                    ScaleXSpan(1.0f + letterSpacing * 0.1f),
                    0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to apply letter spacing", e)
        }
    }

    /**
     * 应用字体颜色
     */
    private fun applyTextColor(styledText: SpannableString, text: String) {
        try {
            val textColor = getTextColor()
            styledText.setSpan(
                ForegroundColorSpan(textColor),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } catch (e: Exception) {
            logger.error("Failed to apply text color", e)
        }
    }

    /**
     * 应用字体样式
     */
    private fun applyTextStyle(styledText: SpannableString, text: String) {
        try {
            val isBold = isTextBold()
            if (isBold) {
                styledText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to apply text style", e)
        }
    }

    /**
     * 直接设置样式（备用方案）
     */
    private fun applyDirectStyles(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass

            // 尝试设置字间距（仅在开关开启时应用）
            if (isLetterSpacingEnabled()) {
                val letterSpacing = getLetterSpacing()
                try {
                    cl.getDeclaredMethod("setLetterSpacing", Float::class.javaPrimitiveType)
                        .invoke(clockInstance, letterSpacing)
                } catch (_: NoSuchMethodError) {
                    // 如果 setLetterSpacing 不存在，使用备选方案
                    applyAlternativeLetterSpacing(clockInstance)
                }
            }

            // 设置文本颜色（仅在开关开启时应用）
            if (isTextColorEnabled()) {
                val textColor = getTextColor()
                cl.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType)
                    .invoke(clockInstance, textColor)
            }

            // 设置字体样式（仅在开关开启时应用）
            if (isTextBoldEnabled()) {
                val isBold = isTextBold()
                if (isBold) {
                    cl.getDeclaredMethod("setTypeface", Typeface::class.java)
                        .invoke(
                            clockInstance,
                            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to apply direct styles", e)
        }
    }

    /**
     * 备选字间距方案
     */
    private fun applyAlternativeLetterSpacing(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass
            val letterSpacing = getLetterSpacing()
            // 方法1：通过设置文本缩放来模拟字间距
            val scaleX = 1.0f + letterSpacing * 0.1f
            cl.getDeclaredMethod("setScaleX", Float::class.javaPrimitiveType)
                .invoke(clockInstance, scaleX)

            // 方法2：通过设置左右边距来增加间距
            val paddingLeft = (letterSpacing * 10).toInt()
            val paddingRight = (letterSpacing * 10).toInt()
            cl.getDeclaredMethod("setPadding", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(clockInstance, paddingLeft, 0, paddingRight, 0)
        } catch (e: Exception) {
            logger.error("Failed to apply alternative letter spacing", e)
        }
    }

    /**
     * 从SharedPreferences获取配置值的方法
     */
    private val prefs: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    fun getCustomClock(key: String): String {
        return prefs.getString(key, "HH:mm")!!
    }

    /**
     * 获取字体大小配置
     */
    private fun getTextSize(): Float {
        return getCustomClockFloat(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE.name, 16.0f) // 默认16sp
    }

    /**
     * 获取字间距配置
     */
    private fun getLetterSpacing(): Float {
        return getCustomClockFloat(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING.name, 0.1f) // 默认0.1
    }

    /**
     * 获取字体颜色配置
     */
    private fun getTextColor(): Int {
        return getCustomClockInt() // 默认白色
    }

    /**
     * 获取粗体配置
     */
    private fun isTextBold(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD.name) // 默认非粗体
    }

    /**
     * 检查字体大小是否启用
     */
    private fun isTextSizeEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE_ENABLED.name)
    }

    /**
     * 检查字间距是否启用
     */
    private fun isLetterSpacingEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING_ENABLED.name)
    }

    /**
     * 检查字体颜色是否启用
     */
    private fun isTextColorEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR_ENABLED.name)
    }

    /**
     * 检查粗体是否启用
     */
    private fun isTextBoldEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD.name)
    }

    /**
     * 辅助方法：读取整型配置
     */
    private fun getCustomClockInt(): Int {
        return prefs.getInt(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR.name, -1)
    }

    /**
     * 辅助方法：读取浮点型配置
     */
    private fun getCustomClockFloat(key: String, defaultValue: Float): Float {
        return prefs.getFloat(key, defaultValue)
    }

    /**
     * 辅助方法：读取布尔型配置
     */
    private fun getCustomClockBoolean(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock"
    }
}
