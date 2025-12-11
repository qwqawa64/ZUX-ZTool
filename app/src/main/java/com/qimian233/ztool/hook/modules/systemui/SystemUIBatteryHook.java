package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.util.TypedValue;

/**
 * 系统UI电池百分比Hook模块
 * 功能：强制显示电池百分比，调整布局位置和字体大小
 */
public class SystemUIBatteryHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "systemui_battery_percentage";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            hookSystemUIBattery(lpparam);
        }
    }

    private void hookSystemUIBattery(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook BatteryMeterView 类
            Class<?> batteryMeterViewClass = XposedHelpers.findClass(
                    "com.android.systemui.battery.BatteryMeterView",
                    lpparam.classLoader
            );

            // Hook 构造函数，在视图创建时修改布局
            XposedHelpers.findAndHookConstructor(batteryMeterViewClass,
                    android.content.Context.class,
                    android.util.AttributeSet.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            modifyBatteryLayout(param.thisObject);
                        }
                    }
            );

            // Hook updateShowPercent 方法
            XposedHelpers.findAndHookMethod(batteryMeterViewClass,
                    "updateShowPercent",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            forceShowPercentage(param.thisObject);
                        }
                    }
            );

            // Hook updatePercentText 方法
            XposedHelpers.findAndHookMethod(batteryMeterViewClass,
                    "updatePercentText",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            updatePercentageText(param.thisObject);
                        }
                    }
            );

            // Hook scaleBatteryMeterViews 方法，调整字体大小
            XposedHelpers.findAndHookMethod(batteryMeterViewClass,
                    "scaleBatteryMeterViews",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            adjustTextSize(param.thisObject);
                        }
                    }
            );

            log("SystemUI电池百分比Hook模块加载成功");

        } catch (Throwable t) {
            logError("SystemUI电池百分比Hook模块加载失败", t);
        }
    }

    private void modifyBatteryLayout(Object batteryMeterView) {
        try {
            // 获取关键的视图组件
            FrameLayout container = (FrameLayout) XposedHelpers.getObjectField(
                    batteryMeterView, "mBatteryPercentViewContainer");
            TextView percentView = (TextView) XposedHelpers.getObjectField(
                    batteryMeterView, "mBatteryPercentView");

            if (container == null || percentView == null) {
                return;
            }

            // 将百分比文本从 FrameLayout 中移除
            container.removeView(percentView);

            // 获取 LinearLayout 参数
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            // 设置左边距，让百分比显示在电池图标右侧
            int marginStart = getDimenValue(batteryMeterView);
            layoutParams.setMargins(marginStart, 0, 0, 0);

            // 将百分比文本直接添加到 BatteryMeterView (LinearLayout) 中
            LinearLayout batteryView = (LinearLayout) batteryMeterView;
            batteryView.addView(percentView, 1, layoutParams); // 添加到索引1的位置（电池图标后面）

            // 调整字体大小
            adjustTextSize(batteryMeterView);

            log("电池布局修改完成");

        } catch (Throwable t) {
            logError("电池布局修改失败", t);
        }
    }

    private void adjustTextSize(Object batteryMeterView) {
        try {
            TextView percentView = (TextView) XposedHelpers.getObjectField(
                    batteryMeterView, "mBatteryPercentView");

            if (percentView == null) {
                return;
            }

            // 获取原始字体大小
            float originalSize = getOriginalTextSize(batteryMeterView);

            // 设置更大的字体大小（增加3sp）
            float newSize = originalSize + 3;
            percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize);

            // 可选：设置粗体让文字更清晰
            percentView.setTypeface(percentView.getTypeface(), android.graphics.Typeface.BOLD);

            log("电池百分比字体大小调整为 " + newSize + "sp");

        } catch (Throwable t) {
            logError("调整电池百分比字体大小失败", t);
        }
    }

    private float getOriginalTextSize(Object batteryMeterView) {
        try {
            // 获取系统默认的电池文字大小
            android.content.Context context = getContext(batteryMeterView);
            int originalSizeRes = context.getResources().getIdentifier(
                    "status_bar_battery_text_size", "dimen", "com.android.systemui");

            if (originalSizeRes != 0) {
                float sizeInPixels = context.getResources().getDimension(originalSizeRes);
                // 将像素转换为sp
                return sizeInPixels / context.getResources().getDisplayMetrics().scaledDensity;
            }
        } catch (Throwable t) {
            log("获取原始电池字体大小失败，使用默认值");
        }

        // 默认值：12sp
        return 12f;
    }

    private void forceShowPercentage(Object batteryMeterView) {
        try {
            TextView percentView = (TextView) XposedHelpers.getObjectField(
                    batteryMeterView, "mBatteryPercentView");

            if (percentView == null) {
                return;
            }

            // 强制显示百分比视图，无论系统设置如何
            if (percentView.getVisibility() != android.view.View.VISIBLE) {
                percentView.setVisibility(android.view.View.VISIBLE);
            }

            // 更新百分比文本
            updatePercentageText(batteryMeterView);

        } catch (Throwable t) {
            logError("强制显示电池百分比失败", t);
        }
    }

    private void updatePercentageText(Object batteryMeterView) {
        try {
            TextView percentView = (TextView) XposedHelpers.getObjectField(
                    batteryMeterView, "mBatteryPercentView");

            if (percentView == null) {
                return;
            }

            // 获取当前电量级别
            int level = XposedHelpers.getIntField(batteryMeterView, "mLevel");

            // 设置百分比文本
            percentView.setText(level + "%");

            // 更新内容描述（辅助功能）
            boolean charging = XposedHelpers.getBooleanField(batteryMeterView, "mCharging");
            String description = getContext(batteryMeterView).getString(
                    charging ?
                            getResourceId(batteryMeterView, "accessibility_battery_level_charging") :
                            getResourceId(batteryMeterView, "accessibility_battery_level"),
                    level
            );

            LinearLayout batteryView = (LinearLayout) batteryMeterView;
            batteryView.setContentDescription(description);

        } catch (Throwable t) {
            logError("更新电池百分比文本失败", t);
        }
    }

    // 工具方法：获取维度值
    private int getDimenValue(Object batteryMeterView) {
        try {
            android.content.Context context = getContext(batteryMeterView);
            int resId = context.getResources().getIdentifier(
                    "qs_battery_padding", "dimen", "com.android.systemui");
            return context.getResources().getDimensionPixelOffset(resId);
        } catch (Throwable t) {
            return 8; // 默认值
        }
    }

    // 工具方法：获取资源ID
    private int getResourceId(Object batteryMeterView, String resourceName) {
        try {
            android.content.Context context = getContext(batteryMeterView);
            return context.getResources().getIdentifier(
                    resourceName, "string", "com.android.systemui");
        } catch (Throwable t) {
            return 0;
        }
    }

    // 工具方法：获取Context
    private android.content.Context getContext(Object batteryMeterView) {
        return (android.content.Context) XposedHelpers.getObjectField(batteryMeterView, "mContext");
    }
}
