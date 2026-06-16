package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomQsColor extends BaseHookModule {
    private static final int CUSTOM_QS_COLOR = 0xFFADD8E6;
    private static final float CUSTOM_QS_ALPHA = 0.5f;

    @Override
    public String getModuleName() {
        return "test_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            Class<?> utilsClass = XposedHelpers.findClass("com.android.settingslib.Utils", lpparam.classLoader);
            XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    lpparam.classLoader, "getOverlayColorForState", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("afterHookMethod of QSTileViewImpl$getOverlayColorForState triggered!");
                            String[] fieldNames = {"overlayColorInactive", "overlayColorActive", "colorLabelActive", "colorLabelInactive",
                                    "colorLabelUnavailable", "colorSecondaryLabelActive", "colorSecondaryLabelInactive", "colorSecondaryLabelUnavailable"};
                            try {
                                int overlayColor = (int) XposedHelpers.callStaticMethod(utilsClass, "applyAlpha",
                                        new Class<?>[] {float.class, int.class}, CUSTOM_QS_ALPHA, CUSTOM_QS_COLOR);
                                for (String i : fieldNames) {
                                    XposedHelpers.setIntField(param.thisObject, i, overlayColor);
                                    log("Successfully set field " + i);
                                }
                            } catch (Exception e) {
                                logError("Failed to set field!", e);
                            }
                        }
                    });
        } catch (Exception e1) {
            logError("Error!", e1);
        }
    }
}
