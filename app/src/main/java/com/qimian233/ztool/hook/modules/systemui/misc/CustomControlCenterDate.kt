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
 * 精确控制中心日期Hook模块
 * 基于VariableDateView和VariableDateViewController的精确Hook
 * 支持自定义日期格式（包括农历、节气等）、字体样式、颜色等完整配置
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
            // 方法1: Hook VariableDateView的setText方法（最精确）
            hookVariableDateViewSetText(classLoader)

            // 方法2: Hook VariableDateViewController的updateClock方法
            hookVariableDateViewController(classLoader)

            // 方法3: Hook TextView的onAttachedToWindow方法（确保初始样式正确）
            hookTextViewAttach()

            logger.info("控制中心日期Hook模块初始化成功")
        } catch (t: Throwable) {
            logger.error("控制中心日期Hook模块初始化失败", t)
        }
    }

    /**
     * 方法1: 直接Hook VariableDateView的setText方法
     * 这是最精确的方法，每次文本更新时都会应用样式和自定义格式
     */
    private fun hookVariableDateViewSetText(classLoader: ClassLoader) {
        try {
            val variableDateViewClass = classLoader.loadClass(VARIABLE_DATE_VIEW_CLASS)

            val setTextMethod: Method = findMethod(variableDateViewClass, "setText", CharSequence::class.java)
            hookWithId(setTextMethod, "set_text") { chain ->
                try {
                    if (!isEnabled()) return@hookWithId chain.proceed()
                    if (!isTargetVariableDateView(chain.thisObject)) return@hookWithId chain.proceed()

                    val originalText = chain.args[0] as CharSequence
                    if (originalText != null) {
                        // 使用自定义格式化器生成新的日期文本
                        val styledText = createStyledCustomDateText()
                        logger.debug("VariableDateView文本替换成功: $styledText")
                        return@hookWithId chain.proceed(arrayOf(styledText))
                    }
                } catch (e: Exception) {
                    logger.error("VariableDateView文本替换失败", e)
                }
                chain.proceed()
            }

            logger.info("VariableDateView.setText Hook成功")
        } catch (t: Throwable) {
            logger.warn("VariableDateView.setText Hook失败（可能是类不存在）")
        }
    }

    /**
     * 方法2: Hook VariableDateViewController的updateClock方法
     * 在日期更新时应用样式和自定义格式
     */
    private fun hookVariableDateViewController(classLoader: ClassLoader) {
        try {
            val controllerClass = classLoader.loadClass(VARIABLE_DATE_CONTROLLER_CLASS)

            // Hook access$updateClock静态方法
            val accessMethod: Method =
                controllerClass.getDeclaredMethod("access\$updateClock", controllerClass)
            hookWithId(accessMethod, "access") { chain ->
                val result = chain.proceed()
                try {
                    val dateView = getValidatedVariableDateView(chain.args[0])
                    if (dateView != null && applyCustomDateToValidatedView(dateView)) {
                        // 直接设置自定义格式化的日期文本
                        logger.debug("VariableDateViewController日期更新成功")
                    }
                } catch (e: Exception) {
                    logger.error("VariableDateViewController日期更新失败", e)
                }
                result
            }

            logger.info("VariableDateViewController Hook成功")
        } catch (t: Throwable) {
            logger.warn("VariableDateViewController Hook失败（可能是类不存在）")
        }
    }

    /**
     * 方法3: Hook TextView的onAttachedToWindow方法
     * 在视图附加到窗口时应用样式（确保初始样式正确）
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

                    // 只处理控制中心 VariableDateView 实例
                    if (VARIABLE_DATE_VIEW_CLASS == className
                        && applyCustomDateToValidatedView(textView)
                    ) {
                        logger.debug("VariableDateView初始样式应用成功")
                    }
                } catch (e: Exception) {
                    logger.error("TextView初始样式应用失败", e)
                }
                result
            }
        } catch (t: Throwable) {
            logger.warn("TextView.onAttachedToWindow Hook失败")
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

        val androidView = view
        val id = androidView.id
        if (id == View.NO_ID) {
            logger.debug("VariableDateView rejected because it has no resource id")
            return false
        }

        return try {
            val entryName = androidView.resources.getResourceEntryName(id)
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
     * 获取自定义格式化的日期
     */
    private fun getCustomFormattedDate(): String {
        return try {
            val format = getCustomDateFormat()
            logger.debug("读取到的配置：$format")
            CustomDateFormatter.format(format, Date())
        } catch (e: Exception) {
            logger.error("自定义日期格式化失败", e)
            // 出错时返回默认格式
            CustomDateFormatter.format("yyyy年MM月dd日 EEEE", Date())
        }
    }

    /**
     * 应用所有样式到文本
     */
    private fun applyAllStyles(text: String): CharSequence {
        val styledText = SpannableString(text)

        // 1. 应用字体大小
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text)
        }

        // 2. 应用字间距
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text)
        }

        // 3. 应用字体颜色
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text)
        }

        // 4. 应用字体样式
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
            logger.error("字体大小应用失败", e)
        }
    }

    /**
     * 应用字间距
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
            logger.error("字间距应用失败", e)
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
            logger.error("字体颜色应用失败", e)
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
            logger.error("字体样式应用失败", e)
        }
    }

    /**
     * 获取自定义日期格式
     */
    private fun getCustomDateFormat(): String {
        return try {
            val format = getCustomDateSetting()
            logger.debug("初次读取到的配置：$format")
            format
        } catch (e: Exception) {
            logger.error("日期格式获取失败", e)
            "yyyy年MM月dd日 EEEE"
        }
    }

    /**
     * 获取SharedPreferences
     */
    private val prefs: SharedPreferences
        get() = xposed.getRemotePreferences(PREFS_NAME)

    /**
     * 从SharedPreferences获取配置值的方法
     */
    private fun getCustomDateSetting(): String {
        return prefs.getString(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_FORMAT.name, "yyyy年MM月dd日 EEEE")!!
    }

    /**
     * 获取字体大小配置
     */
    private fun getTextSize(): Float {
        return getCustomDateFloat(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE.name, 16.0f)
    }

    /**
     * 获取字间距配置
     */
    private fun getLetterSpacing(): Float {
        return getCustomDateFloat(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING.name, 0.1f)
    }

    /**
     * 获取字体颜色配置
     */
    private fun getTextColor(): Int {
        return getCustomDateInt()
    }

    /**
     * 获取粗体配置
     */
    private fun isTextBold(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD.name)
    }

    /**
     * 检查字体大小是否启用
     */
    private fun isTextSizeEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE_ENABLED.name)
    }

    /**
     * 检查字间距是否启用
     */
    private fun isLetterSpacingEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING_ENABLED.name)
    }

    /**
     * 检查字体颜色是否启用
     */
    private fun isTextColorEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR_ENABLED.name)
    }

    /**
     * 检查粗体是否启用
     */
    private fun isTextBoldEnabled(): Boolean {
        return getCustomDateBoolean(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD.name)
    }

    /**
     * 辅助方法：读取整型配置
     */
    private fun getCustomDateInt(): Int {
        return prefs.getInt(PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR.name, -1)
    }

    /**
     * 辅助方法：读取浮点型配置
     */
    private fun getCustomDateFloat(key: String, defaultValue: Float): Float {
        return prefs.getFloat(key, defaultValue)
    }

    /**
     * 辅助方法：读取布尔型配置
     */
    private fun getCustomDateBoolean(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    companion object {
        private const val PREFS_NAME = "xposed_module_config"
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val VARIABLE_DATE_VIEW_CLASS = "com.android.systemui.statusbar.policy.VariableDateView"
        private const val VARIABLE_DATE_CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.VariableDateViewController"
    }
}
