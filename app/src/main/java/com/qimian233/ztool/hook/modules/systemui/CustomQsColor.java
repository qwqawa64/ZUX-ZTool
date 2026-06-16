package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.Color;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomQsColor extends BaseHookModule {
    private static final int CUSTOM_QS_COLOR = Color.rgb(0xAD, 0xD8, 0xE6);
    private static final float CUSTOM_QS_ALPHA = 0.5f;
    private static final int STATE_ACTIVE = 2;

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
        String[] fieldNames = {
                "colorLabelActive", "colorLabelInactive", "colorLabelUnavailable", // Label
                "colorSecondaryLabelActive", "colorSecondaryLabelInactive", "colorSecondaryLabelUnavailable", //2nd label
                "fixedSpecialColorActive", "specialColorActive"}; // Fields for quick switch
        int overlayColor = Color.argb(Math.round(255 * CUSTOM_QS_ALPHA), Color.red(CUSTOM_QS_COLOR),
                Color.green(CUSTOM_QS_COLOR), Color.blue(CUSTOM_QS_COLOR));
        try { // QuickSwitch Hook
            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getBackgroundColorForState", int.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int state = (int) param.args[0];
                            boolean disabledByPolicy = (boolean) param.args[2];
                            if (state == STATE_ACTIVE && !disabledByPolicy) {
                                param.setResult(CUSTOM_QS_COLOR);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getOverlayColorForState", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("afterHookMethod of QSTileViewImpl$getOverlayColorForState triggered!");
                            int state = (int) param.args[0];
                            if (state == STATE_ACTIVE) {
                                param.setResult(overlayColor);
                            }
                            log("Overlay Color: " + overlayColor);
                            for (String i : fieldNames) {
                                try {
                                    XposedHelpers.setIntField(param.thisObject, i, CUSTOM_QS_COLOR);
                                    log("Successfully set field " + i);
                                } catch (Exception e2) {
                                    logError("Failed to set field!", e2);
                                }
                            }
                        }
                    });
        } catch (Exception e1) {
            logError("Error!", e1);
        }
    }
}
