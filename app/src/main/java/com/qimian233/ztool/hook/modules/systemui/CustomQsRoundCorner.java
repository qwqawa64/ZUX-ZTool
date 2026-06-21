package com.qimian233.ztool.hook.modules.systemui;

import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.ProgressBar;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomQsRoundCorner extends BaseHookModule {

    private static int headUpTileRoundCornerRadius = 32;
    private static int normalTileRoundCornerRadius = 96;
    private static Class<?> shellResourceIdClass;

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

        XposedHelpers.findAndHookMethod("com.android.systemui.settings.ToggleSliderView",
                lpparam.classLoader,
                "refreshSeekBar",
                ProgressBar.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("refreshSeekBar afterHookedMethod called!");
                        applySeekBarRoundCorner((ProgressBar) param.args[0], "refreshSeekBar");
                    }
                });

        XposedHelpers.findAndHookMethod("com.android.systemui.settings.ToggleSliderView",
                lpparam.classLoader,
                "updateBrightnessSlider",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("updateBrightnessSlider afterHookedMethod called!");
                        applySeekBarRoundCorner((ProgressBar) XposedHelpers.getObjectField(param.thisObject, "mBrightnessSlider"), "updateBrightnessSlider");
                    }
                });

        XposedHelpers.findAndHookMethod("com.android.systemui.settings.ToggleSliderView",
                lpparam.classLoader,
                "updateVolumeSlider",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("updateVolumeSlider afterHookedMethod called!");
                        applySeekBarRoundCorner((ProgressBar) XposedHelpers.getObjectField(param.thisObject, "mMediaVolumeSlider"), "updateVolumeSlider");
                    }
                });

        XposedHelpers.findAndHookConstructor("com.android.systemui.settings.ToggleSliderView",
                lpparam.classLoader,
                "android.content.Context",
                "android.util.AttributeSet",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ProgressBar brightnessSlider = (ProgressBar) XposedHelpers.getObjectField(param.thisObject, "mBrightnessSlider");
                        ProgressBar mediaSlider = (ProgressBar) XposedHelpers.getObjectField(param.thisObject, "mMediaVolumeSlider");
                        applySeekBarRoundCorner(brightnessSlider, "constructor");
                        applySeekBarRoundCorner(mediaSlider, "constructor");
                    }
                });
    }

    private void applySeekBarRoundCorner(ProgressBar progressBar, String source) {
        if (progressBar == null) {
            log("applySeekBarRoundCorner skipped from " + source + ": progressBar is null");
            return;
        }

        Drawable progressDrawable = progressBar.getProgressDrawable();
        if (!(progressDrawable instanceof LayerDrawable)) {
            log("applySeekBarRoundCorner skipped from " + source + ": progress drawable is " + describeDrawable(progressDrawable));
            return;
        }

        Class<?> resourceIdClass = getShellResourceIdClass();
        if (resourceIdClass == null) {
            log("applySeekBarRoundCorner skipped from " + source + ": resource id class is null");
            return;
        }

        try {
            LayerDrawable layerDrawable = (LayerDrawable) progressDrawable;
            int backgroundId = XposedHelpers.getIntField(resourceIdClass, "background");

            Drawable backgroundDrawable = layerDrawable.findDrawableByLayerId(backgroundId);
            if (backgroundDrawable instanceof GradientDrawable) {
                ((GradientDrawable) backgroundDrawable).setCornerRadius((float) headUpTileRoundCornerRadius);
            } else {
                log("Background layer is " + describeDrawable(backgroundDrawable) + " from " + source);
            }

            Drawable progressLayer = layerDrawable.findDrawableByLayerId(android.R.id.progress);
            if (progressLayer instanceof StateListDrawable) {
                StateListDrawable stateListDrawable = (StateListDrawable) progressLayer;
                for (int i = 0; i < stateListDrawable.getStateCount(); i++) {
                    Drawable stateDrawable = stateListDrawable.getStateDrawable(i);
                    if (stateDrawable instanceof ClipDrawable) {
                        Drawable innerDrawable = ((ClipDrawable) stateDrawable).getDrawable();
                        if (innerDrawable instanceof GradientDrawable) {
                            ((GradientDrawable) innerDrawable).setCornerRadius((float) headUpTileRoundCornerRadius);
                        }
                    }
                }
            } else {
                log("Progress layer is " + describeDrawable(progressLayer) + " from " + source);
            }
        } catch (Throwable t) {
            logError("applySeekBarRoundCorner failed from " + source, t);
        }
    }

    private Class<?> getShellResourceIdClass() {
        if (shellResourceIdClass != null) {
            return shellResourceIdClass;
        }
        try {
            shellResourceIdClass = Class.forName("com.android.wm.shell.R$id");
            log("Successfully found resource ID class via reflection.");
            return shellResourceIdClass;
        } catch (Exception e) {
            logError("Failed to find resource ID class!", e);
            return null;
        }
    }

    private String describeDrawable(Drawable drawable) {
        return drawable == null ? "null" : drawable.getClass().getName();
    }

    private void updateRoundCornerPrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        headUpTileRoundCornerRadius = prefs.getInt("head_up_round_corner_radius", 32);
        normalTileRoundCornerRadius = prefs.getInt("tile_round_corner_radius", 96);
    }
}
