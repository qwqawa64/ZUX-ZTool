package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.Color;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class CustomQsColor extends BaseHookModule {
    private static final int STATE_ACTIVE = 2;
    private static boolean CUSTOM_QS_COLOR = false; // 是否启用磁贴背景色修改
    private static boolean CUSTOM_LABEL_COLOR = false; // 是否启用磁贴主要说明文本在开关被启用时的颜色修改
    private static boolean CUSTOM_SECOND_LABEL_COLOR = false; // 是否启用磁贴次要说明文本在开关被启用后的颜色修改
    private static int CUSTOM_QS_ACTIVE_COLOR_VAL = 0; // 磁贴背景色，AARRGGBB 格式，在存储时要通过 Color.argb() 提前转化好
    private static int CUSTOM_LABEL_ACTIVE_COLOR_VAL = 0; // 主要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好
    private static int CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = 0; // 次要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好

    public CustomQsColor() {}

    @Override
    public String getModuleName() {
        return "qs_color";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        updatePrefs();
        try {
            Method getBackgroundMethod = classLoader
                    .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                    .getDeclaredMethod("getBackgroundColorForState", int.class, boolean.class, boolean.class);
            hookWithId(getBackgroundMethod, "get_background", chain -> {
                updatePrefs();
                int state = (int) chain.getArg(0);
                boolean disabledByPolicy = (boolean) chain.getArg(2);
                if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_QS_COLOR) {
                    return CUSTOM_QS_ACTIVE_COLOR_VAL;
                }
                return chain.proceed();
            });

            Method getLabelMethod = classLoader
                    .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                    .getDeclaredMethod("getLabelColorForState", int.class, boolean.class);
            hookWithId(getLabelMethod, "get_label", chain -> {
                updatePrefs();
                int state = (int) chain.getArg(0);
                boolean disabledByPolicy = (boolean) chain.getArg(1);
                if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_LABEL_COLOR) {
                    return CUSTOM_LABEL_ACTIVE_COLOR_VAL;
                }
                return chain.proceed();
            });

            Method getSecondaryMethod = classLoader
                    .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                    .getDeclaredMethod("getSecondaryLabelColorForState", int.class, boolean.class);
            hookWithId(getSecondaryMethod, "get_secondary", chain -> {
                updatePrefs();
                int state = (int) chain.getArg(0);
                boolean disabledByPolicy = (boolean) chain.getArg(1);
                if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_SECOND_LABEL_COLOR) {
                    return CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL;
                }
                return chain.proceed();
            });
        } catch (Exception e1) {
            logError("Error!", e1);
        }
    }

    private void updatePrefs() {
        try {
            CUSTOM_QS_COLOR = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("custom_qs_color", false);
        } catch (Throwable t) {
            CUSTOM_QS_COLOR = false;
        }
        try {
            CUSTOM_LABEL_COLOR = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("custom_label_color", false);
        } catch (Throwable t) {
            CUSTOM_LABEL_COLOR = false;
        }
        try {
            CUSTOM_SECOND_LABEL_COLOR = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("custom_second_label_color", false);
        } catch (Throwable t) {
            CUSTOM_SECOND_LABEL_COLOR = false;
        }
        try {
            CUSTOM_QS_ACTIVE_COLOR_VAL = this.xposed.getRemotePreferences("xposed_module_config").getInt("custom_qs_active_color_val", Color.argb(0xff, 0xff, 0xff, 0xff));
        } catch (Throwable t) {
            CUSTOM_QS_ACTIVE_COLOR_VAL = Color.argb(0xff, 0xff, 0xff, 0xff);
        }
        try {
            CUSTOM_LABEL_ACTIVE_COLOR_VAL = this.xposed.getRemotePreferences("xposed_module_config").getInt("custom_label_active_color_val", Color.argb(0xff, 0x00, 0x00, 0x00));
        } catch (Throwable t) {
            CUSTOM_LABEL_ACTIVE_COLOR_VAL = Color.argb(0xff, 0x00, 0x00, 0x00);
        }
        try {
            CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = this.xposed.getRemotePreferences("xposed_module_config").getInt("custom_second_label_active_color_val", Color.argb(0xff, 0x00, 0x00, 0x00));
        } catch (Throwable t) {
            CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = Color.argb(0xff, 0x00, 0x00, 0x00);
        }
    }
}
