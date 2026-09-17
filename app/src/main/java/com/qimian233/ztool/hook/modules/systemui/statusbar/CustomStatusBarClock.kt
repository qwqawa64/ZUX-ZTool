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
 * Custom status bar clock hook module.
 * Modifies SystemUI status bar clock display format and style; supports custom time
 * format, font size, letter spacing, color, and bold.
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
            // Hook the Clock class's getSmallTime method
            val getSmallTimeMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("getSmallTime")
            hookWithId(getSmallTimeMethod, "get_small_time") { chain ->
                try {
                    // Check whether the module is enabled
                    if (!isEnabled()) {
                        return@hookWithId chain.proceed()
                    }

                    // Get the custom formatted time
                    val customTime = getCustomTimeFormat()

                    // Apply all styles to the text
                    val styledText = applyAllStyles(customTime)

                    // Return the new value
                    logger.debug("Successfully customized status bar clock: $customTime")
                    return@hookWithId styledText
                } catch (e: Exception) {
                    logger.error("Failed to customize getSmallTime", e)
                    chain.proceed()
                }
            }

            // Hook the updateClock method to ensure content description and style are applied correctly
            val updateClockMethod: Method =
                classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateClock")
            hookWithId(updateClockMethod, "update_clock") { chain ->
                val result = chain.proceed()
                try {
                    // Check whether the module is enabled
                    if (!isEnabled()) {
                        return@hookWithId result
                    }

                    val clockInstance = chain.thisObject

                    // Get the custom time
                    val customTime = getCustomTimeFormat()

                    // Set the content description (used by accessibility)
                    // Use getMethod instead of getDeclaredMethod because setContentDescription is inherited from View
                    clockInstance.javaClass.getMethod("setContentDescription", CharSequence::class.java)
                        .invoke(clockInstance, customTime)

                    // Apply direct styles (fallback approach)
                    applyDirectStyles(clockInstance)

                    logger.debug("Updated clock content description: $customTime")
                } catch (e: Exception) {
                    logger.error("Failed to update clock content description", e)
                }
                result
            }

            // Extra hook: apply styles when the view is initialized
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
     * Custom time format method.
     * Uses the new formatter utility supporting lunar calendar, solar terms, etc.
     */
    private fun getCustomTimeFormat(): String {
        return try {
            val format = getCustomClock(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_FORMAT.name)
            CustomDateFormatter.format(format, Date())
        } catch (e: Exception) {
            logger.error("Error in custom time formatting", e)
            // Return the default time format on error
            CustomDateFormatter.format("HH:mm", Date())
        }
    }

    /**
     * Apply all styles to the text (main method)
     */
    private fun applyAllStyles(text: String): CharSequence {
        val styledText = SpannableString(text)

        // 1. Apply font size (only when the switch is on)
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text)
        }

        // 2. Apply letter spacing (only when the switch is on)
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text)
        }

        // 3. Apply font color (only when the switch is on)
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text)
        }

        // 4. Apply font style (bold etc., only when the switch is on)
        if (isTextBoldEnabled()) {
            applyTextStyle(styledText, text)
        }

        return styledText
    }

    /**
     * Apply font size
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
     * Apply letter spacing (compatibility approach)
     */
    private fun applyLetterSpacing(styledText: SpannableString, text: String) {
        try {
            val letterSpacing = getLetterSpacing()
            // Use ScaleXSpan to simulate letter spacing
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
     * Apply font color
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
     * Apply font style
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
     * Set styles directly (fallback approach)
     */
    private fun applyDirectStyles(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass

            // Try to set letter spacing (only when the switch is on)
            if (isLetterSpacingEnabled()) {
                val letterSpacing = getLetterSpacing()
                try {
                    cl.getDeclaredMethod("setLetterSpacing", Float::class.javaPrimitiveType)
                        .invoke(clockInstance, letterSpacing)
                } catch (_: NoSuchMethodError) {
                    // If setLetterSpacing does not exist, use the alternative approach
                    applyAlternativeLetterSpacing(clockInstance)
                }
            }

            // Set the text color (only when the switch is on)
            if (isTextColorEnabled()) {
                val textColor = getTextColor()
                cl.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType)
                    .invoke(clockInstance, textColor)
            }

            // Set the font style (only when the switch is on)
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
     * Alternative letter spacing approach
     */
    private fun applyAlternativeLetterSpacing(clockInstance: Any) {
        try {
            val cl = clockInstance.javaClass
            val letterSpacing = getLetterSpacing()
            // Method 1: simulate letter spacing via text scaling
            val scaleX = 1.0f + letterSpacing * 0.1f
            cl.getDeclaredMethod("setScaleX", Float::class.javaPrimitiveType)
                .invoke(clockInstance, scaleX)

            // Method 2: add spacing via left/right padding
            val paddingLeft = (letterSpacing * 10).toInt()
            val paddingRight = (letterSpacing * 10).toInt()
            cl.getDeclaredMethod("setPadding", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(clockInstance, paddingLeft, 0, paddingRight, 0)
        } catch (e: Exception) {
            logger.error("Failed to apply alternative letter spacing", e)
        }
    }

    /**
     * Read a config value from SharedPreferences
     */
    private val prefs: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    fun getCustomClock(key: String): String {
        return prefs.getString(key, "HH:mm")!!
    }

    /**
     * Get font size config
     */
    private fun getTextSize(): Float {
        return getCustomClockFloat(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE.name, 16.0f) // default 16sp
    }

    /**
     * Get letter spacing config
     */
    private fun getLetterSpacing(): Float {
        return getCustomClockFloat(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING.name, 0.1f) // default 0.1
    }

    /**
     * Get font color config
     */
    private fun getTextColor(): Int {
        return getCustomClockInt() // default white
    }

    /**
     * Get bold config
     */
    private fun isTextBold(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD.name) // default not bold
    }

    /**
     * Check whether font size is enabled
     */
    private fun isTextSizeEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_SIZE_ENABLED.name)
    }

    /**
     * Check whether letter spacing is enabled
     */
    private fun isLetterSpacingEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_LETTER_SPACING_ENABLED.name)
    }

    /**
     * Check whether font color is enabled
     */
    private fun isTextColorEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR_ENABLED.name)
    }

    /**
     * Check whether bold is enabled
     */
    private fun isTextBoldEnabled(): Boolean {
        return getCustomClockBoolean(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_BOLD.name)
    }

    /**
     * Helper: read an integer config
     */
    private fun getCustomClockInt(): Int {
        return prefs.getInt(PreferenceKeys.CUSTOM_STATUSBAR_CLOCK_TEXT_COLOR.name, -1)
    }

    /**
     * Helper: read a float config
     */
    private fun getCustomClockFloat(key: String, defaultValue: Float): Float {
        return prefs.getFloat(key, defaultValue)
    }

    /**
     * Helper: read a boolean config
     */
    private fun getCustomClockBoolean(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock"
    }
}
