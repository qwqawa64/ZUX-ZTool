package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.Color;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomQsColor extends BaseHookModule {
    private static final int STATE_ACTIVE = 2;
    private static boolean CUSTOM_QS_COLOR = false; // 是否启用磁贴背景色修改
    private static boolean CUSTOM_LABEL_COLOR = false; // 是否启用磁贴主要说明文本在开关被启用时的颜色修改
    private static boolean CUSTOM_SECOND_LABEL_COLOR = false; // 是否启用磁贴次要说明文本在开关被启用后的颜色修改
    private static int CUSTOM_QS_ACTIVE_COLOR_VAL = 0; // 磁贴背景色，AARRGGBB 格式，在存储时要通过 Color.argb() 提前转化好
    private static int CUSTOM_LABEL_ACTIVE_COLOR_VAL = 0; // 主要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好
    private static int CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = 0; // 次要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好

    @Override
    public String getModuleName() {
        return "qs_color";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        updatePrefs();
        try {
            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getBackgroundColorForState", int.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            updatePrefs();
                            int state = (int) param.args[0];
                            boolean disabledByPolicy = (boolean) param.args[2];
                            if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_QS_COLOR) {
                                param.setResult(CUSTOM_QS_ACTIVE_COLOR_VAL);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getLabelColorForState", int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            updatePrefs();
                            int state = (int) param.args[0];
                            boolean disabledByPolicy = (boolean) param.args[1];
                            if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_LABEL_COLOR) {
                                param.setResult(CUSTOM_LABEL_ACTIVE_COLOR_VAL);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getSecondaryLabelColorForState", int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            updatePrefs();
                            int state = (int) param.args[0];
                            boolean disabledByPolicy = (boolean) param.args[1];
                            if (state == STATE_ACTIVE && !disabledByPolicy && CUSTOM_SECOND_LABEL_COLOR) {
                                param.setResult(CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL);
                            }
                        }
                    });
        } catch (Exception e1) {
            logError("Error!", e1);
        }
    }

    private void updatePrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        CUSTOM_QS_COLOR = prefs.getBoolean("custom_qs_color", false);
        CUSTOM_LABEL_COLOR = prefs.getBoolean("custom_label_color", false);
        CUSTOM_SECOND_LABEL_COLOR = prefs.getBoolean("custom_second_label_color", false);
        CUSTOM_QS_ACTIVE_COLOR_VAL = prefs.getInt("custom_qs_active_color_val", Color.argb(0xff, 0xff, 0xff, 0xff));
        CUSTOM_LABEL_ACTIVE_COLOR_VAL = prefs.getInt("custom_label_active_color_val", Color.argb(0xff, 0x00, 0x00, 0x00));
        CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = prefs.getInt("custom_second_label_active_color_val", Color.argb(0xff, 0x00, 0x00, 0x00));
    }
}
