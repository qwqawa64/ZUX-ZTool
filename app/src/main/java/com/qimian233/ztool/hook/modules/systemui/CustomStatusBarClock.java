package com.qimian233.ztool.hook.modules.systemui;

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

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * 自定义状态栏时钟Hook模块
 * 修改SystemUI状态栏时钟显示格式和样式，支持自定义时间格式、字体大小、字间距、颜色和粗体
 */
public class CustomStatusBarClock extends BaseHookModule {

    private static final String PREFS_NAME = "xposed_module_config";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock";

    public CustomStatusBarClock() {}

    @Override
    public String getModuleName() {
        return "Custom_StatusBarClock";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            hookSystemUIClock(classLoader);
        }
    }

    private void hookSystemUIClock(ClassLoader classLoader) {
        try {
            // Hook Clock 类的 getSmallTime 方法
            Method getSmallTimeMethod = classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("getSmallTime");
            this.xposed.hook(getSmallTimeMethod).intercept(chain -> {
                try {
                    // 检查模块是否启用
                    if (!isEnabled()) {
                        return chain.proceed();
                    }

                    // 获取自定义格式的时间
                    String customTime = getCustomTimeFormat();

                    // 应用所有样式到文本
                    CharSequence styledText = applyAllStyles(customTime);

                    // 返回新的值
                    log("Successfully customized status bar clock: " + customTime);
                    return styledText;

                } catch (Exception e) {
                    logError("Failed to customize getSmallTime", e);
                    return chain.proceed();
                }
            });

            // Hook updateClock 方法，确保内容描述和样式正确应用
            Method updateClockMethod = classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("updateClock");
            this.xposed.hook(updateClockMethod).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    // 检查模块是否启用
                    if (!isEnabled()) {
                        return result;
                    }

                    Object clockInstance = chain.getThisObject();

                    // 获取自定义时间
                    String customTime = getCustomTimeFormat();

                    // 设置内容描述（无障碍功能使用）
                    clockInstance.getClass().getDeclaredMethod("setContentDescription", CharSequence.class)
                            .invoke(clockInstance, customTime);

                    // 应用直接样式（备用方案）
                    applyDirectStyles(clockInstance);

                    log("Updated clock content description: " + customTime);

                } catch (Exception e) {
                    logError("Failed to update clock content description", e);
                }
                return result;
            });

            // 额外 Hook：在视图初始化时应用样式
            Method onFinishInflateMethod = classLoader.loadClass(CLOCK_CLASS).getDeclaredMethod("onFinishInflate");
            this.xposed.hook(onFinishInflateMethod).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (!isEnabled()) {
                        return result;
                    }

                    Object clockInstance = chain.getThisObject();
                    applyDirectStyles(clockInstance);
                } catch (Exception e) {
                    logError("Failed to apply styles in onFinishInflate", e);
                }
                return result;
            });

            log("Successfully hooked SystemUI Clock methods");

        } catch (Throwable t) {
            logError("Failed to hook SystemUI Clock class", t);
        }
    }

    /**
     * 自定义时间格式方法
     * 使用新的格式化工具支持农历、节气等
     */
    private String getCustomTimeFormat() {
        try {
            String format = getCustomClock("Custom_StatusBarClockFormat");
            return CustomDateFormatter.format(format, new Date());
        } catch (Exception e) {
            logError("Error in custom time formatting", e);
            // 出错时返回默认时间格式
            return CustomDateFormatter.format("HH:mm", new Date());
        }
    }

    /**
     * 应用所有样式到文本（主要方法）
     */
    private CharSequence applyAllStyles(String text) {
        SpannableString styledText = new SpannableString(text);

        // 1. 应用字体大小（仅在开关开启时应用）
        if (isTextSizeEnabled()) {
            applyTextSize(styledText, text);
        }

        // 2. 应用字间距（仅在开关开启时应用）
        if (isLetterSpacingEnabled()) {
            applyLetterSpacing(styledText, text);
        }

        // 3. 应用字体颜色（仅在开关开启时应用）
        if (isTextColorEnabled()) {
            applyTextColor(styledText, text);
        }

        // 4. 应用字体样式（粗体等，仅在开关开启时应用）
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
            logError("Failed to apply text size", e);
        }
    }

    /**
     * 应用字间距（兼容性方案）
     */
    private void applyLetterSpacing(SpannableString styledText, String text) {
        try {
            float letterSpacing = getLetterSpacing();
            // 使用 ScaleXSpan 模拟字间距
            if (letterSpacing > 0) {
                styledText.setSpan(new ScaleXSpan(1.0f + letterSpacing * 0.1f),
                        0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            logError("Failed to apply letter spacing", e);
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
            logError("Failed to apply text color", e);
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
            logError("Failed to apply text style", e);
        }
    }

    /**
     * 直接设置样式（备用方案）
     */
    private void applyDirectStyles(Object clockInstance) {
        try {
            Class<?> cl = clockInstance.getClass();
            // 设置文本大小（仅在开关开启时应用）
            if (isTextSizeEnabled()) {
                float textSizeSp = getTextSize();
                cl.getDeclaredMethod("setTextSize", int.class, float.class)
                        .invoke(clockInstance, TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            }

            // 尝试设置字间距（仅在开关开启时应用）
            if (isLetterSpacingEnabled()) {
                float letterSpacing = getLetterSpacing();
                try {
                    cl.getDeclaredMethod("setLetterSpacing", float.class)
                            .invoke(clockInstance, letterSpacing);
                } catch (NoSuchMethodError e) {
                    // 如果 setLetterSpacing 不存在，使用备选方案
                    applyAlternativeLetterSpacing(clockInstance);
                }
            }

            // 设置文本颜色（仅在开关开启时应用）
            if (isTextColorEnabled()) {
                int textColor = getTextColor();
                cl.getDeclaredMethod("setTextColor", int.class).invoke(clockInstance, textColor);
            }

            // 设置字体样式（仅在开关开启时应用）
            if (isTextBoldEnabled()) {
                boolean isBold = isTextBold();
                if (isBold) {
                    cl.getDeclaredMethod("setTypeface", Typeface.class)
                            .invoke(clockInstance,
                                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                }
            }

        } catch (Exception e) {
            logError("Failed to apply direct styles", e);
        }
    }

    /**
     * 备选字间距方案
     */
    private void applyAlternativeLetterSpacing(Object clockInstance) {
        try {
            Class<?> cl = clockInstance.getClass();
            float letterSpacing = getLetterSpacing();
            // 方法1：通过设置文本缩放来模拟字间距
            float scaleX = 1.0f + letterSpacing * 0.1f;
            cl.getDeclaredMethod("setScaleX", float.class).invoke(clockInstance, scaleX);

            // 方法2：通过设置左右边距来增加间距
            int paddingLeft = (int) (letterSpacing * 10);
            int paddingRight = (int) (letterSpacing * 10);
            cl.getDeclaredMethod("setPadding", int.class, int.class, int.class, int.class)
                    .invoke(clockInstance, paddingLeft, 0, paddingRight, 0);

        } catch (Exception e) {
            logError("Failed to apply alternative letter spacing", e);
        }
    }

    /**
     * 从SharedPreferences获取配置值的方法
     */
    private SharedPreferences getPrefs() {
        return this.xposed.getRemotePreferences(PREFS_NAME);
    }

    public String getCustomClock(String key) {
        SharedPreferences prefs = getPrefs();
        return prefs.getString(key, "HH:mm");
    }

    /**
     * 获取字体大小配置
     */
    private float getTextSize() {
        return getCustomClockFloat("Custom_StatusBarClockTextSize", 16.0f); // 默认16sp
    }

    /**
     * 获取字间距配置
     */
    private float getLetterSpacing() {
        return getCustomClockFloat("Custom_StatusBarClockLetterSpacing", 0.1f); // 默认0.1
    }

    /**
     * 获取字体颜色配置
     */
    private int getTextColor() {
        return getCustomClockInt(); // 默认白色
    }

    /**
     * 获取粗体配置
     */
    private boolean isTextBold() {
        return getCustomClockBoolean("Custom_StatusBarClockTextBold"); // 默认非粗体
    }

    /**
     * 检查字体大小是否启用
     */
    private boolean isTextSizeEnabled() {
        return getCustomClockBoolean("Custom_StatusBarClockTextSizeEnabled");
    }

    /**
     * 检查字间距是否启用
     */
    private boolean isLetterSpacingEnabled() {
        return getCustomClockBoolean("Custom_StatusBarClockLetterSpacingEnabled");
    }

    /**
     * 检查字体颜色是否启用
     */
    private boolean isTextColorEnabled() {
        return getCustomClockBoolean("Custom_StatusBarClockTextColorEnabled");
    }

    /**
     * 检查粗体是否启用
     */
    private boolean isTextBoldEnabled() {
        return getCustomClockBoolean("Custom_StatusBarClockTextBold");
    }

    /**
     * 辅助方法：读取整型配置
     */
    private int getCustomClockInt() {
        SharedPreferences prefs = getPrefs();
        return prefs.getInt("Custom_StatusBarClockTextColor", -1);
    }

    /**
     * 辅助方法：读取浮点型配置
     */
    private float getCustomClockFloat(String key, float defaultValue) {
        SharedPreferences prefs = getPrefs();
        return prefs.getFloat(key, defaultValue);
    }

    /**
     * 辅助方法：读取布尔型配置
     */
    private boolean getCustomClockBoolean(String key) {
        SharedPreferences prefs = getPrefs();
        return prefs.getBoolean(key, false);
    }
}
