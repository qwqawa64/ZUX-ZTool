package com.qimian233.ztool.hook.modules.systemui.qs;

import android.annotation.SuppressLint;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.ProgressBar;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.data.keys.PreferenceKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class CustomQsRoundCorner extends AppHookModule {

    private static int headUpTileRoundCornerRadius = 32;
    private static int normalTileRoundCornerRadius = 96;

    public CustomQsRoundCorner() {}

    @Override
    public String getModuleName() {
        return "qs_round_corner";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {ScopeKeys.SYSTEM_UI.packageName};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        updateRoundCornerPrefs();

        // Head-up tiles
        Method changeCornerRadiusMethod = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                .getDeclaredMethod("changeCornerRadius", float.class);
        hookWithId(changeCornerRadiusMethod, "change_corner_radius", chain -> chain.proceed(new Object[]{(float) headUpTileRoundCornerRadius}));

        // Normal tiles
        Method updateRippleRadiusMethod = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl")
                .getDeclaredMethod("updateRippleRadius");
        hookWithId(updateRippleRadiusMethod, "update_ripple_radius", chain -> {
            Object result = chain.proceed();
            try {
                Class<?> cl = chain.getThisObject().getClass();

                RippleDrawable rippleDrawable =
                        (RippleDrawable) findField(cl, "qsTileBackground").get(chain.getThisObject());

                if (rippleDrawable != null) {
                    Drawable mask = rippleDrawable.findDrawableByLayerId(android.R.id.mask);
                    if (mask instanceof GradientDrawable) {
                        ((GradientDrawable) mask).setCornerRadius((float) normalTileRoundCornerRadius);
                    }
                }

                LayerDrawable backgroundDrawable =
                        (LayerDrawable) findField(cl, "backgroundDrawable").get(chain.getThisObject());

                if (backgroundDrawable != null) {
                    int count = backgroundDrawable.getNumberOfLayers();
                    for (int i = 0; i < count; i++) {
                        Drawable layer = backgroundDrawable.getDrawable(i);
                        if (layer instanceof GradientDrawable) {
                            ((GradientDrawable) layer).setCornerRadius((float) normalTileRoundCornerRadius);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Cannot set normal tile round corner radius!", e);
            }
            return result;
        });

        // Sliders
        Class<?> toggleSliderViewClass = classLoader.loadClass("com.android.systemui.settings.ToggleSliderView");

        Method refreshSeekBarMethod = toggleSliderViewClass.getDeclaredMethod("refreshSeekBar", ProgressBar.class);
        hookWithId(refreshSeekBarMethod, "refresh_seek_bar", chain -> {
            logger.debug("refreshSeekBar afterHookedMethod called!");
            Object result = chain.proceed();
            applySeekBarRoundCorner((ProgressBar) chain.getArg(0));
            return result;
        });

        Method updateBrightnessSliderMethod = toggleSliderViewClass.getDeclaredMethod("updateBrightnessSlider");
        hookWithId(updateBrightnessSliderMethod, "update_brightness_slider", chain -> {
            Object result = chain.proceed();
            logger.debug("updateBrightnessSlider afterHookedMethod called!");
            Class<?> cl = chain.getThisObject().getClass();
            ProgressBar brightnessSlider = (ProgressBar) cl.getDeclaredField("mBrightnessSlider")
                    .get(chain.getThisObject());
            if (brightnessSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar.class)
                        .invoke(chain.getThisObject(), brightnessSlider);
            }
            return result;
        });

        Method updateVolumeSliderMethod = toggleSliderViewClass.getDeclaredMethod("updateVolumeSlider");
        hookWithId(updateVolumeSliderMethod, "update_volume_slider", chain -> {
            Object result = chain.proceed();
            logger.debug("updateVolumeSlider afterHookedMethod called!");
            Class<?> cl = chain.getThisObject().getClass();
            ProgressBar mediaSlider = (ProgressBar) cl.getDeclaredField("mMediaVolumeSlider")
                    .get(chain.getThisObject());
            if (mediaSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar.class)
                        .invoke(chain.getThisObject(), mediaSlider);
            }
            return result;
        });

        Constructor<?> toggleCtor = toggleSliderViewClass.getDeclaredConstructor(
                android.content.Context.class,
                android.util.AttributeSet.class,
                int.class);
        hookWithId(toggleCtor, "toggle", chain -> {
            chain.proceed();
            Class<?> cl = chain.getThisObject().getClass();
            ProgressBar brightnessSlider = (ProgressBar) cl.getDeclaredField("mBrightnessSlider")
                    .get(chain.getThisObject());
            ProgressBar mediaSlider = (ProgressBar) cl.getDeclaredField("mMediaVolumeSlider")
                    .get(chain.getThisObject());
            if (brightnessSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar.class)
                        .invoke(chain.getThisObject(), brightnessSlider);
            }
            if (mediaSlider != null) {
                cl.getDeclaredMethod("refreshSeekBar", ProgressBar.class)
                        .invoke(chain.getThisObject(), mediaSlider);
            }
            return null;
        });
    }

    private void applySeekBarRoundCorner(ProgressBar progressBar) {
        if (progressBar == null) {
            logger.debug("applySeekBarRoundCorner skipped from " + "refreshSeekBar" + ": progressBar is null");
            return;
        }

        Drawable progressDrawable = progressBar.getProgressDrawable();
        if (!(progressDrawable instanceof LayerDrawable)) {
            logger.debug("applySeekBarRoundCorner skipped from " + "refreshSeekBar" + ": progress drawable is " + describeDrawable(progressDrawable));
            return;
        }

        try {
            LayerDrawable layerDrawable = (LayerDrawable) progressDrawable;
            Drawable backgroundDrawable = layerDrawable.findDrawableByLayerId(android.R.id.background);
            if (backgroundDrawable instanceof GradientDrawable) {
                ((GradientDrawable) backgroundDrawable).setCornerRadius((float) headUpTileRoundCornerRadius);
            } else {
                logger.debug("Background layer is " + describeDrawable(backgroundDrawable) + " from " + "refreshSeekBar");
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
                logger.debug("Progress layer is " + describeDrawable(progressLayer) + " from " + "refreshSeekBar");
            }
        } catch (Throwable t) {
            logger.error("applySeekBarRoundCorner failed from " + "refreshSeekBar", t);
        }
    }

    private String describeDrawable(Drawable drawable) {
        return drawable == null ? "null" : drawable.getClass().getName();
    }

    private void updateRoundCornerPrefs() {
        try {
            headUpTileRoundCornerRadius = getRemotePreferences().getInt(PreferenceKeys.HEAD_UP_ROUND_CORNER_RADIUS.name, 32);
        } catch (Throwable t) {
            headUpTileRoundCornerRadius = 32;
        }
        try {
            normalTileRoundCornerRadius = getRemotePreferences().getInt(PreferenceKeys.TILE_ROUND_CORNER_RADIUS.name, 96);
        } catch (Throwable t) {
            normalTileRoundCornerRadius = 96;
        }
    }
}
