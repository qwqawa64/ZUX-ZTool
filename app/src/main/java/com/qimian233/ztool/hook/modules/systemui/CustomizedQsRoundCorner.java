package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;

public class CustomizedQsRoundCorner extends BaseHookModule {

    private static int headUpTileRoundCornerRadius = 32;
    private static int normalTileRoundCornerRadius = 96;

    @Override
    public String getModuleName() {
        return "qs_round_corner";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        updateRoundCornerPrefs();
        XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileViewImpl",
                lpparam.classLoader,
                "changeCornerRadius",
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = (float) headUpTileRoundCornerRadius;
                    }
                });
        // Wi-Fi and bluetooth QS radius, max 96.0f

        XposedHelpers.findAndHookMethod("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl",
                lpparam.classLoader,
                "updateRippleRadius",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        RippleDrawable rippleDrawable =
                                (RippleDrawable) XposedHelpers.getObjectField(param.thisObject, "qsTileBackground");

                        if (rippleDrawable != null) {
                            Drawable mask = rippleDrawable.findDrawableByLayerId(android.R.id.mask);
                            if (mask instanceof GradientDrawable) {
                                ((GradientDrawable) mask).setCornerRadius((float) normalTileRoundCornerRadius);
                            }
                        }

                        LayerDrawable backgroundDrawable =
                                (LayerDrawable) XposedHelpers.getObjectField(param.thisObject, "backgroundDrawable");

                        if (backgroundDrawable != null) {
                            int count = backgroundDrawable.getNumberOfLayers();
                            for (int i = 0; i < count; i++) {
                                Drawable layer = backgroundDrawable.getDrawable(i);
                                if (layer instanceof GradientDrawable) {
                                    ((GradientDrawable) layer).setCornerRadius((float) normalTileRoundCornerRadius);
                                }
                            }
                        }
                    }
                });
        // Small QS radius, 96.0f is the max value.
    }

    private void updateRoundCornerPrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        headUpTileRoundCornerRadius = prefs.getInt("head_up_round_corner_radius", 32);
        normalTileRoundCornerRadius = prefs.getInt("tile_round_corner_radius", 96);
    }

}
