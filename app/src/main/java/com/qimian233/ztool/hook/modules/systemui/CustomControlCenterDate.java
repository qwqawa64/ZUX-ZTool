package com.qimian233.ztool.hook.modules.systemui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

/**
 * 精确控制中心日期Hook模块
 * 基于VariableDateView和VariableDateViewController的精确Hook
 * 支持自定义日期格式（包括农历、节气等）、字体样式、颜色等完整配置
 */
@SuppressLint("PrivateApi")
public class CustomControlCenterDate extends BaseHookModule {

    private static final String PREFS_NAME = "xposed_module_config";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String VARIABLE_DATE_VIEW_CLASS = "com.android.systemui.statusbar.policy.VariableDateView";
    private static final String VARIABLE_DATE_CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.VariableDateViewController";

    public CustomControlCenterDate() {}

    @Override
    public String getModuleName() {
        return "Custom_ControlCenterDate";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        if (!isEnabled()) {
            return;
        }

        try {
            // 方法1: Hook VariableDateView的setText方法（最精确）
            hookVariableDateViewSetText(classLoader);

            // 方法2: Hook VariableDateViewController的updateClock方法
            hookVariableDateViewController(classLoader);

            // 方法3: Hook TextView的onAttachedToWindow方法（确保初始样式正确）
            hookTextViewAttach();

            log("控制中心日期Hook模块初始化成功");

        } catch (Throwable t) {
            logError("控制中心日期Hook模块初始化失败", t);
        }
    }

    /**
     * 方法1: 直接Hook VariableDateView的setText方法
     * 这是最精确的方法，每次文本更新时都会应用样式和自定义格式
     */
    private void hookVariableDateViewSetText(ClassLoader classLoader) {
        try {
            Class<?> variableDateViewClass = classLoader.loadClass(VARIABLE_DATE_VIEW_CLASS);

            Method setTextMethod = findMethod(variableDateViewClass, "setText", CharSequence.class);
            this.xposed.hook(setTextMethod).intercept(chain -> {
                try {
                    if (!isEnabled()) return chain.proceed();
                    if (!isTargetVariableDateView(chain.getThisObject())) return chain.proceed();

                    CharSequence originalText = (CharSequence) chain.getArg(0);
                    if (originalText != null) {
                        // 使用自定义格式化器生成新的日期文本
                        CharSequence styledText = createStyledCustomDateText();
                        log("VariableDateView文本替换成功: " + styledText);
                        return chain.proceed(new Object[]{styledText});
                    }
                } catch (Exception e) {
                    logError("VariableDateView文本替换失败", e);
                }
                return chain.proceed();
            });

            log("VariableDateView.setText Hook成功");

        } catch (Throwable t) {
            log("VariableDateView.setText Hook失败（可能是类不存在）");
        }
    }

    /**
     * 方法2: Hook VariableDateViewController的updateClock方法
     * 在日期更新时应用样式和自定义格式
     */
    private void hookVariableDateViewController(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = classLoader.loadClass(VARIABLE_DATE_CONTROLLER_CLASS);

            // Hook access$updateClock静态方法
            Method accessMethod = controllerClass.getDeclaredMethod("access$updateClock", controllerClass);
            this.xposed.hook(accessMethod).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object dateView = getValidatedVariableDateView(chain.getArg(0));
                    if (dateView != null && applyCustomDateToValidatedView(dateView)) {
                        // 直接设置自定义格式化的日期文本
                        log("VariableDateViewController日期更新成功");
                    }
                } catch (Exception e) {
                    logError("VariableDateViewController日期更新失败", e);
                }
                return result;
            });

            log("VariableDateViewController Hook成功");

        } catch (Throwable t) {
            log("VariableDateViewController Hook失败（可能是类不存在）");
        }
    }

    /**
     * 方法3: Hook TextView的onAttachedToWindow方法
     * 在视图附加到窗口时应用样式（确保初始样式正确）
     */
    private void hookTextViewAttach() {
        try {
            Method attachMethod = android.widget.TextView.class.getDeclaredMethod("onAttachedToWindow");
            this.xposed.hook(attachMethod).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object textView = chain.getThisObject();
                    String className = textView.getClass().getName();

                    // 只处理控制中心 VariableDateView 实例
                    if (VARIABLE_DATE_VIEW_CLASS.equals(className)
                            && applyCustomDateToValidatedView(textView)) {
                        log("VariableDateView初始样式应用成功");
                    }
                } catch (Exception e) {
                    logError("TextView初始样式应用失败", e);
                }
                return result;
            });

        } catch (Throwable t) {
            log("TextView.onAttachedToWindow Hook失败");
        }
    }

    private Object getValidatedVariableDateView(Object controller) throws ReflectiveOperationException {
        if (controller == null
                || !VARIABLE_DATE_CONTROLLER_CLASS.equals(controller.getClass().getName())) {
            return null;
        }

        Object dateView = findField(controller.getClass(), "mView").get(controller);
        return isTargetVariableDateView(dateView) ? dateView : null;
    }

    private boolean applyCustomDateToValidatedView(Object dateView) throws ReflectiveOperationException {
        if (!isTargetVariableDateView(dateView)) {
            return false;
        }

        Method setTextMethod = findMethod(dateView.getClass(), "setText", CharSequence.class);
        setTextMethod.invoke(dateView, createStyledCustomDateText());
        return true;
    }

    private boolean isTargetVariableDateView(Object view) {
        if (view == null || !VARIABLE_DATE_VIEW_CLASS.equals(view.getClass().getName())) {
            return false;
        }

        try {
            findField(view.getClass(), "longerPattern");
            findField(view.getClass(), "shorterPattern");
            return isControlCenterDateResource(view);
        } catch (Throwable t) {
            if (DEBUG) logError("VariableDateView fingerprint mismatch", t);
            return false;
        }
    }

    private boolean isControlCenterDateResource(Object view) {
        if (!(view instanceof android.view.View)) {
            return false;
        }

        android.view.View androidView = (android.view.View) view;
        int id = androidView.getId();
        if (id == android.view.View.NO_ID) {
            if (DEBUG) log("VariableDateView rejected because it has no resource id");
            return false;
        }

        try {
            String entryName = androidView.getResources().getResourceEntryName(id);
            boolean matched = "date".equals(entryName);
            if (DEBUG && !matched) {
                log("VariableDateView resource rejected: " + entryName);
            }
            return matched;
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to read VariableDateView resource name", t);
            return false;
        }
    }

    private CharSequence createStyledCustomDateText() {
        return applyAllStyles(getCustomFormattedDate());
    }

    /**
     * 获取自定义格式化的日期
     */
    private String getCustomFormattedDate() {
        try {
            String format = getCustomDateFormat();
            if (DEBUG) log("读取到的配置：" + format);
            return CustomDateFormatter.format(format, new Date());
        } catch (Exception e) {
            logError("自定义日期格式化失败", e);
            // 出错时返回默认格式
            return CustomDateFormatter.format("yyyy年MM月dd日 EEEE", new Date());
        }
    }

    /**
     * 应用所有样式到文本
     */
    private CharSequence applyAllStyles(String text) {
        SpannableString styledText = new SpannableString(text);

        // 1. 应用字体大小
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text);
        }

        // 2. 应用字间距
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text);
        }

        // 3. 应用字体颜色
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text);
        }

        // 4. 应用字体样式
        if (isTextBoldEnabled()) {
            applyTextStyle(styledText, text);
        }

        return styledText;
    }

    /**
     * 应用字体大小
     */
    private void applyTextSize(SpannableString styledText, String text) {
        try {
            float textSizeSp = getTextSize();
            int textSizePx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, textSizeSp,
                    android.content.res.Resources.getSystem().getDisplayMetrics());
            styledText.setSpan(new AbsoluteSizeSpan(textSizePx),
                    0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception e) {
            logError("字体大小应用失败", e);
        }
    }

    /**
     * 应用字间距
     */
    private void applyLetterSpacing(SpannableString styledText, String text) {
        try {
            float letterSpacing = getLetterSpacing();
            if (letterSpacing > 0) {
                styledText.setSpan(new ScaleXSpan(1.0f + letterSpacing * 0.1f),
                        0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            logError("字间距应用失败", e);
        }
    }

    /**
     * 应用字体颜色
     */
    private void applyTextColor(SpannableString styledText, String text) {
        try {
            int textColor = getTextColor();
            styledText.setSpan(new ForegroundColorSpan(textColor),
                    0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception e) {
            logError("字体颜色应用失败", e);
        }
    }

    /**
     * 应用字体样式
     */
    private void applyTextStyle(SpannableString styledText, String text) {
        try {
            boolean isBold = isTextBold();
            if (isBold) {
                styledText.setSpan(new StyleSpan(Typeface.BOLD),
                        0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            logError("字体样式应用失败", e);
        }
    }

    /**
     * 获取自定义日期格式
     */
    private String getCustomDateFormat() {
        try {
            String format = getCustomDateSetting();
            if (DEBUG) log("初次读取到的配置：" + format);
            if (format == null || format.isEmpty()) {
                log("读取到的配置为空，使用默认格式");
                format = "yyyy年MM月dd日 EEEE"; // 默认格式
            }
            return format;
        } catch (Exception e) {
            logError("日期格式获取失败", e);
            return "yyyy年MM月dd日 EEEE";
        }
    }

    /**
     * 获取SharedPreferences
     */
    private SharedPreferences getPrefs() {
        return this.xposed.getRemotePreferences(PREFS_NAME);
    }

    /**
     * 从SharedPreferences获取配置值的方法
     */
    private String getCustomDateSetting() {
        return getPrefs().getString("Custom_ControlCenterDateFormat", "yyyy年MM月dd日 EEEE");
    }

    /**
     * 获取字体大小配置
     */
    private float getTextSize() {
        return getCustomDateFloat("Custom_ControlCenterDateTextSize", 16.0f);
    }

    /**
     * 获取字间距配置
     */
    private float getLetterSpacing() {
        return getCustomDateFloat("Custom_ControlCenterDateLetterSpacing", 0.1f);
    }

    /**
     * 获取字体颜色配置
     */
    private int getTextColor() {
        return getCustomDateInt();
    }

    /**
     * 获取粗体配置
     */
    private boolean isTextBold() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextBold");
    }

    /**
     * 检查字体大小是否启用
     */
    private boolean isTextSizeEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextSizeEnabled");
    }

    /**
     * 检查字间距是否启用
     */
    private boolean isLetterSpacingEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateLetterSpacingEnabled");
    }

    /**
     * 检查字体颜色是否启用
     */
    private boolean isTextColorEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextColorEnabled");
    }

    /**
     * 检查粗体是否启用
     */
    private boolean isTextBoldEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextBold");
    }

    /**
     * 辅助方法：读取整型配置
     */
    private int getCustomDateInt() {
        return getPrefs().getInt("Custom_ControlCenterDateTextColor", -1);
    }

    /**
     * 辅助方法：读取浮点型配置
     */
    private float getCustomDateFloat(String key, float defaultValue) {
        return getPrefs().getFloat(key, defaultValue);
    }

    /**
     * 辅助方法：读取布尔型配置
     */
    private boolean getCustomDateBoolean(String key) {
        return getPrefs().getBoolean(key, false);
    }
}
