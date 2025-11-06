package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.widget.TextView;
import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Date;

/**
 * 精确控制中心日期Hook模块
 * 基于VariableDateView和VariableDateViewController的精确Hook
 * 支持自定义日期格式（包括农历、节气等）、字体样式、颜色等完整配置
 */
public class CustomControlCenterDate extends BaseHookModule {

    private static final String PREFS_NAME = "ControlCenter_Date";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String VARIABLE_DATE_VIEW_CLASS = "com.android.systemui.statusbar.policy.VariableDateView";
    private static final String VARIABLE_DATE_CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.VariableDateViewController";

    @Override
    public String getModuleName() {
        return "Custom_ControlCenterDate";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isEnabled()) {
            return;
        }

        try {
            // 方法1: Hook VariableDateView的setText方法（最精确）
            hookVariableDateViewSetText(lpparam);

            // 方法2: Hook VariableDateViewController的updateClock方法
            hookVariableDateViewController(lpparam);

            // 方法3: Hook TextView的onAttachedToWindow方法（确保初始样式正确）
            hookTextViewAttach(lpparam);

            log("控制中心日期Hook模块初始化成功");

        } catch (Throwable t) {
            logError("控制中心日期Hook模块初始化失败", t);
        }
    }

    /**
     * 方法1: 直接Hook VariableDateView的setText方法
     * 这是最精确的方法，每次文本更新时都会应用样式和自定义格式
     */
    private void hookVariableDateViewSetText(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> variableDateViewClass = XposedHelpers.findClass(
                    VARIABLE_DATE_VIEW_CLASS,
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    variableDateViewClass,
                    "setText",
                    CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                if (!isEnabled()) return;

                                CharSequence originalText = (CharSequence) param.args[0];
                                if (originalText != null) {
                                    // 使用自定义格式化器生成新的日期文本
                                    String customDate = getCustomFormattedDate();
                                    CharSequence styledText = applyAllStyles(customDate);
                                    param.args[0] = styledText;
                                    log("VariableDateView文本替换成功: " + customDate);
                                }
                            } catch (Exception e) {
                                logError("VariableDateView文本替换失败", e);
                            }
                        }
                    }
            );

            log("VariableDateView.setText Hook成功");

        } catch (Throwable t) {
            log("VariableDateView.setText Hook失败（可能是类不存在）");
        }
    }

    /**
     * 方法2: Hook VariableDateViewController的updateClock方法
     * 在日期更新时应用样式和自定义格式
     */
    private void hookVariableDateViewController(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> controllerClass = XposedHelpers.findClass(
                    VARIABLE_DATE_CONTROLLER_CLASS,
                    lpparam.classLoader
            );

            // Hook access$updateClock静态方法
            XposedHelpers.findAndHookMethod(
                    controllerClass,
                    "access$updateClock",
                    controllerClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object controller = param.args[0];
                                Object dateView = XposedHelpers.getObjectField(controller, "mView");
                                if (dateView != null) {
                                    // 直接设置自定义格式化的日期文本
                                    String customDate = getCustomFormattedDate();
                                    CharSequence styledText = applyAllStyles(customDate);
                                    XposedHelpers.callMethod(dateView, "setText", styledText);
                                    log("VariableDateViewController日期更新成功: " + customDate);
                                }
                            } catch (Exception e) {
                                logError("VariableDateViewController日期更新失败", e);
                            }
                        }
                    }
            );

            log("VariableDateViewController Hook成功");

        } catch (Throwable t) {
            log("VariableDateViewController Hook失败（可能是类不存在）");
        }
    }

    /**
     * 方法3: Hook TextView的onAttachedToWindow方法
     * 在视图附加到窗口时应用样式（确保初始样式正确）
     */
    private void hookTextViewAttach(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.widget.TextView",
                    lpparam.classLoader,
                    "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object textView = param.thisObject;
                                String className = textView.getClass().getName();

                                // 只处理VariableDateView实例
                                if (VARIABLE_DATE_VIEW_CLASS.equals(className)) {
                                    String customDate = getCustomFormattedDate();
                                    CharSequence styledText = applyAllStyles(customDate);
                                    XposedHelpers.callMethod(textView, "setText", styledText);
                                    log("VariableDateView初始样式应用成功");
                                }
                            } catch (Exception e) {
                                logError("TextView初始样式应用失败", e);
                            }
                        }
                    }
            );

        } catch (Throwable t) {
            log("TextView.onAttachedToWindow Hook失败");
        }
    }

    /**
     * 获取自定义格式化的日期
     */
    private String getCustomFormattedDate() {
        try {
            String format = getCustomDateFormat();
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
            String format = getCustomDateSetting("Custom_ControlCenterDateFormat");
            if (format == null || format.isEmpty()) {
                format = "yyyy年MM月dd日 EEEE"; // 默认格式
            }
            return format;
        } catch (Exception e) {
            logError("日期格式获取失败", e);
            return "yyyy年MM月dd日 EEEE";
        }
    }

    /**
     * 从SharedPreferences获取配置值的方法
     */
    private String getCustomDateSetting(String key) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getString(key, "yyyy年MM月dd日 EEEE");
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
        return getCustomDateInt("Custom_ControlCenterDateTextColor", 0xFFFFFFFF);
    }

    /**
     * 获取粗体配置
     */
    private boolean isTextBold() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextBold", false);
    }

    /**
     * 检查字体大小是否启用
     */
    private boolean isTextSizeEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextSizeEnabled", false);
    }

    /**
     * 检查字间距是否启用
     */
    private boolean isLetterSpacingEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateLetterSpacingEnabled", false);
    }

    /**
     * 检查字体颜色是否启用
     */
    private boolean isTextColorEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextColorEnabled", false);
    }

    /**
     * 检查粗体是否启用
     */
    private boolean isTextBoldEnabled() {
        return getCustomDateBoolean("Custom_ControlCenterDateTextBold", false);
    }

    /**
     * 辅助方法：读取整型配置
     */
    private int getCustomDateInt(String key, int defaultValue) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getInt(key, defaultValue);
    }

    /**
     * 辅助方法：读取浮点型配置
     */
    private float getCustomDateFloat(String key, float defaultValue) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getFloat(key, defaultValue);
    }

    /**
     * 辅助方法：读取布尔型配置
     */
    private boolean getCustomDateBoolean(String key, boolean defaultValue) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getBoolean(key, defaultValue);
    }
}
