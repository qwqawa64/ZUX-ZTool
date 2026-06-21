package com.qimian233.ztool.hook.modules.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ControlCenterNoTileLabelsHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "control_center_no_tile_labels";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(
                "com.android.systemui.qs.tileimpl.CustomQSTileViewImpl",
                lpparam.classLoader,
                "createAndAddLabels",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ViewGroup labelContainer = (ViewGroup) XposedHelpers.getObjectField(
                                param.thisObject,
                                "labelContainer"
                        );
                        if (labelContainer != null) {
                            labelContainer.setVisibility(View.GONE);
                            labelContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                    }
                }
        );
    }
}
