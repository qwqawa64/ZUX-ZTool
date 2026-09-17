package com.qimian233.ztool.hook.modules.systemui.misc

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
import android.view.View
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Date

/**
 * Control center date precision hook module.
 * Precise hooks based on VariableDateView and VariableDateViewController.
 * Supports custom date formats (including lunar calendar, solar terms, etc.),
 * font style, color, and other full configuration.
 */
@SuppressLint("PrivateApi")
class CustomControlCenterDate : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        if (!isEnabled()) {
            return
        }

        try {
            // Method 1: Hook VariableDateView's setText method (most precise)
            hookVariableDateViewSetText(classLoader)

            // Method 2: Hook VariableDateViewController's updateClock method
            hookVariableDateViewController(classLoader)

            // Method 3: Hook TextView's onAttachedToWindow method (ensure initial style is correct)
            hookTextViewAttach()

            logger.info("Control center date hook module initialized successfully")
        } catch (t: Throwable) {
            logger.error("Failed to initialize control center date hook module", t)
        }
    }

    /**
     * Method 1: Hook VariableDateView's setText method directly.
     * This is the most precise approach; style and custom format are applied
     * on every text update.
     */
    private fun hookVariableDateViewSetText(classLoader: ClassLoader) {
        try {
            val variableDateViewClass = classLoader.loadClass(VARIABLE_DATE_VIEW_CLASS)

            val setTextMethod: Method = findMethod(variableDateViewClass, "setText", CharSequence::class.java)
            hookWithId(setTextMethod, "set_text") { chain ->
                try {
                    if (!isEnabled()) return@hookWithId chain.proceed()
                    if (!isTargetVariableDateView(chain.thisObject)) return@hookWithId chain.proceed()

                    // Generate new date text using the custom formatter
                    val styledText = createStyledCustomDateText()
                    logger.debug("VariableDateView text replaced: $styledText")
                    return@hookWithId chain.proceed(arrayOf(styledText))
                } catch (e: Exception) {
                    logger.error("Failed to replace VariableDateView text", e)
                }
                chain.proceed()
            }

            logger.info("VariableDateView.setText hook applied")
        } catch (_: Throwable) {
            logger.warn("VariableDateView.setText hook failed (class may not exist)")
        }
    }

    /**
     * Method 2: Hook VariableDateViewController's updateClock method.
     * Applies style and custom format on date updates.
     */
    private fun hookVariableDateViewController(classLoader: ClassLoader) {
        try {
            val controllerClass = classLoader.loadClass(VARIABLE_DATE_CONTROLLER_CLASS)

            // Hook the access$updateClock static method
            val accessMethod: Method =
                controllerClass.getDeclaredMethod($$"access$updateClock", controllerClass)
            hookWithId(accessMethod, "access") { chain ->
                val result = chain.proceed()
                try {
                    val dateView = getValidatedVariableDateView(chain.args[0])
                    if (dateView != null && applyCustomDateToValidatedView(dateView)) {
                        // Set the custom-formatted date text directly
                        logger.debug("VariableDateViewController date updated")
                    }
                } catch (e: Exception) {
                    logger.error("VariableDateViewController date update failed", e)
                }
                result
            }

            logger.info("VariableDateViewController hook applied")
        } catch (_: Throwable) {
            logger.warn("VariableDateViewController hook failed (class may not exist)")
        }
    }

    /**
     * Method 3: Hook TextView's onAttachedToWindow method.
     * Applies style when the view attaches to the window (ensure initial style is correct).
     */
    private fun hookTextViewAttach() {
        try {
            val attachMethod: Method =
                TextView::class.java.getDeclaredMethod("onAttachedToWindow")
            hookWithId(attachMethod, "attach") { chain ->
                val result = chain.proceed()
                try {
                    val textView = chain.thisObject
                    val className = textView.javaClass.name

                    // Only handle control center VariableDateView instances
                    if (VARIABLE_DATE_VIEW_CLASS == className
                        && applyCustomDateToValidatedView(textView)
                    ) {
                        logger.debug("VariableDateView initial style applied")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to apply TextView initial style", e)
                }
                result
            }
        } catch (_: Throwable) {
            logger.warn("TextView.onAttachedToWindow hook failed")
        }
    }

    private fun getValidatedVariableDateView(controller: Any?): Any? {
        if (controller == null
            || VARIABLE_DATE_CONTROLLER_CLASS != controller.javaClass.name
        ) {
            return null
        }

        val dateView = findField(controller.javaClass, "mView").get(controller)
        return if (isTargetVariableDateView(dateView)) dateView else null
    }

    private fun applyCustomDateToValidatedView(dateView: Any): Boolean {
        if (!isTargetVariableDateView(dateView)) {
            return false
        }

        val setTextMethod: Method = findMethod(dateView.javaClass, "setText", CharSequence::class.java)
        setTextMethod.invoke(dateView, createStyledCustomDateText())
        return true
    }

    private fun isTargetVariableDateView(view: Any?): Boolean {
        if (view == null || VARIABLE_DATE_VIEW_CLASS != view.javaClass.name) {
            return false
        }

        return try {
            findField(view.javaClass, "longerPattern")
            findField(view.javaClass, "shorterPattern")
            isControlCenterDateResource(view)
        } catch (t: Throwable) {
            logger.error("VariableDateView fingerprint mismatch", t)
            false
        }
    }

    private fun isControlCenterDateResource(view: Any): Boolean {
        if (view !is View) {
            return false
        }

        val id = view.id
        if (id == View.NO_ID) {
            logger.debug("VariableDateView rejected because it has no resource id")
            return false
        }

        return try {
            val entryName = view.resources.getResourceEntryName(id)
            val matched = "date" == entryName
            if (!matched) {
                logger.debug("VariableDateView resource rejected: $entryName")
            }
            matched
        } catch (t: Throwable) {
            logger.error("Failed to read VariableDateView resource name", t)
            false
        }
    }

    private fun createStyledCustomDateText(): CharSequence {
        return applyAllStyles(getCustomFormattedDate())
    }

    /**
     * Get the custom formatted date
     */
    private fun getCustomFormattedDate(): String {
        return try {
            val format = getCustomDateFormat()
            logger.debug("Loaded format config: $format")
            CustomDateFormatter.format(format, Date())
        } catch (e: Exception) {
            logger.error("Custom date formatting failed", e)
            // Return the default format on error
            CustomDateFormatter.format("yyyy年MM月dd日 EEEE", Date())
        }
    }

    /**
     * Apply all styles to the text
     */
    private fun applyAllStyles(text: String): CharSequence {
        val styledText = SpannableString(text)

        // 1. Apply font size
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text)
        }

        // 2. Apply letter spacing
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text)
        }

        // 3. Apply font color
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text)
        }

        // 4. Apply font style
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
            logger.error("Failed to apply font size", e)
        }
    }

    /**
     * Apply letter spacing
     */
    private fun applyLetterSpacing(styledText: SpannableString, text: String) {
        try {
            val letterSpacing = getLetterSpacing()
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
            logger.error("Failed to apply font color", e)
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
            logger.error("Failed to apply font style", e)
        }
    }

    /**
     * Get the custom date format
     */
    private fun getCustomDateFormat(): String {
        return try {
            val format = getCustomDateSetting()
            logger.debug("Initial format config: $format")
            format
        } catch (e: Exception) {
            logger.error("Failed to get date format", e)
            "yyyy年MM月dd日 EEEE"
        }
    }

    /**
     * Get SharedPreferences
     */
    private val prefs: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    /**
     * Read a config value from SharedPreferences
     */
    private fun getCustomDateSetting(): String {
        return prefs.getString(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_FORMAT.name, "yyyy年MM月dd日 EEEE")!!
    }

    /**
     * Get font size config
     */
    private fun getTextSize(): Float {
        return getCustomDateFloat(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE.name, 16.0f)
    }

    /**
     * Get letter spacing config
     */
    private fun getLetterSpacing(): Float {
        return getCustomDateFloat(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING.name, 0.1f)
    }

    /**
     * Get font color config
     */
    private fun getTextColor(): Int {
        return getCustomDateInt()
    }

    /**
     * Get bold config
     */
    private fun isTextBold(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD.name)
    }

    /**
     * Check whether font size is enabled
     */
    private fun isTextSizeEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE_ENABLED.name)
    }

    /**
     * Check whether letter spacing is enabled
     */
    private fun isLetterSpacingEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING_ENABLED.name)
    }

    /**
     * Check whether font color is enabled
     */
    private fun isTextColorEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR_ENABLED.name)
    }

    /**
     * Check whether bold is enabled
     */
    private fun isTextBoldEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD.name)
    }

    /**
     * Helper: read an integer config
     */
    private fun getCustomDateInt(): Int {
        return prefs.getInt(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR.name, -1)
    }

    /**
     * Helper: read a float config
     */
    private fun getCustomDateFloat(key: String, defaultValue: Float): Float {
        return prefs.getFloat(key, defaultValue)
    }

    /**
     * Helper: read a boolean config
     */
    private fun getCustomDateBoolean(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val VARIABLE_DATE_VIEW_CLASS = "com.android.systemui.statusbar.policy.VariableDateView"
        private const val VARIABLE_DATE_CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.VariableDateViewController"
    }
}
