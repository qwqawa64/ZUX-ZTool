package com.qimian233.ztool.hook.modules.systemui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ControlCenterSliderPercentageHook extends BaseHookModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView";
    private static final String SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent";
    private static final String BRIGHTNESS_ROOT_FIELD = "mBrightnessSliderRoot";
    private static final String VOLUME_ROOT_FIELD = "mVolumeSliderRoot";

    @Override
    public String getModuleName() {
        return "control_center_slider_percentage";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEM_UI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookToggleSliderViewLifecycle(lpparam);
        hookSeekProgressChanges(lpparam);
        log("Control center slider percentage hooks installed");
    }

    private void hookToggleSliderViewLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookConstructor(
                TOGGLE_SLIDER_VIEW_CLASS,
                lpparam.classLoader,
                Context.class,
                android.util.AttributeSet.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        attachSliderLabels(param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                TOGGLE_SLIDER_VIEW_CLASS,
                lpparam.classLoader,
                "updateBrightnessSlider",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        attachSliderLabels(param.thisObject);
                        refreshBrightnessLabel(param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                TOGGLE_SLIDER_VIEW_CLASS,
                lpparam.classLoader,
                "updateVolumeSlider",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        attachSliderLabels(param.thisObject);
                        refreshVolumeLabel(param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                TOGGLE_SLIDER_VIEW_CLASS,
                lpparam.classLoader,
                "setVolumeProgress",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        refreshVolumeLabel(param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                TOGGLE_SLIDER_VIEW_CLASS,
                lpparam.classLoader,
                "refreshSeekBar",
                ProgressBar.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object sliderView = param.thisObject;
                        ProgressBar progressBar = (ProgressBar) param.args[0];
                        if (isBrightnessSlider(sliderView, progressBar)) {
                            refreshBrightnessLabel(sliderView);
                        }
                    }
                }
        );
    }

    private void hookSeekProgressChanges(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                SeekBar.class,
                "setProgress",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        SeekBar seekBar = (SeekBar) param.thisObject;
                        Object sliderView = findToggleSliderView(seekBar);
                        if (sliderView == null) {
                            return;
                        }

                        if (isBrightnessSlider(sliderView, seekBar)) {
                            refreshBrightnessLabel(sliderView);
                        } else if (isVolumeSlider(sliderView, seekBar)) {
                            refreshVolumeLabel(sliderView);
                        }
                    }
                }
        );
    }

    private void attachSliderLabels(Object sliderView) {
        try {
            attachLabelToRoot(sliderView, BRIGHTNESS_ROOT_FIELD, true);
            attachLabelToRoot(sliderView, VOLUME_ROOT_FIELD, false);
        } catch (Throwable t) {
            logError("Failed to attach slider labels", t);
        }
    }

    private void attachLabelToRoot(Object sliderView, String rootFieldName, boolean brightness) {
        FrameLayout root = getFrameLayoutField(sliderView, rootFieldName);
        if (root == null) {
            return;
        }

        TextView percentView = findPercentView(root);
        if (percentView == null) {
            percentView = createPercentView(root.getContext());
            root.addView(percentView, createLayoutParams(root.getContext()));
        }

        if (brightness) {
            refreshBrightnessLabel(sliderView, percentView);
        } else {
            refreshVolumeLabel(sliderView, percentView);
        }
    }

    private void refreshBrightnessLabel(Object sliderView) {
        FrameLayout root = getFrameLayoutField(sliderView, BRIGHTNESS_ROOT_FIELD);
        if (root == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView != null) {
            refreshBrightnessLabel(sliderView, percentView);
        }
    }

    private void refreshVolumeLabel(Object sliderView) {
        FrameLayout root = getFrameLayoutField(sliderView, VOLUME_ROOT_FIELD);
        if (root == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView != null) {
            refreshVolumeLabel(sliderView, percentView);
        }
    }

    private void refreshBrightnessLabel(Object sliderView, TextView percentView) {
        try {
            SeekBar brightnessSlider = (SeekBar) XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
            if (brightnessSlider == null) {
                percentView.setText("--%");
                return;
            }
            percentView.setText(formatPercent(brightnessSlider.getProgress(), brightnessSlider.getMin(), brightnessSlider.getMax()));
        } catch (Throwable t) {
            percentView.setText("--%");
            if (DEBUG) logError("Failed to refresh brightness percent", t);
        }
    }

    private void refreshVolumeLabel(Object sliderView, TextView percentView) {
        try {
            SeekBar volumeSlider = (SeekBar) XposedHelpers.getObjectField(sliderView, "mMediaVolumeSlider");
            if (volumeSlider == null) {
                percentView.setText("--%");
                return;
            }
            percentView.setText(formatPercent(volumeSlider.getProgress(), volumeSlider.getMin(), volumeSlider.getMax()));
        } catch (Throwable t) {
            percentView.setText("--%");
            if (DEBUG) logError("Failed to refresh volume percent", t);
        }
    }

    private String formatPercent(int progress, int min, int max) {
        int range = Math.max(1, max - min);
        int value = Math.round(((progress - min) * 100f) / range);
        value = Math.max(0, Math.min(100, value));
        return String.format(Locale.US, "%d%%", value);
    }

    private TextView createPercentView(Context context) {
        TextView textView = new TextView(context);
        textView.setTag(SLIDER_PERCENT_TAG);
        textView.setTextColor(Color.WHITE);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextSize(11f);
        textView.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        textView.setSingleLine(true);
        textView.setIncludeFontPadding(false);
        textView.setGravity(android.view.Gravity.CENTER);
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setClickable(false);
        textView.setFocusable(false);
        textView.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2));
        textView.setElevation(dp(context, 2));
        return textView;
    }

    private FrameLayout.LayoutParams createLayoutParams(Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.CENTER;
        return params;
    }

    private TextView findPercentView(FrameLayout root) {
        View view = root.findViewWithTag(SLIDER_PERCENT_TAG);
        return view instanceof TextView ? (TextView) view : null;
    }

    private FrameLayout getFrameLayoutField(Object sliderView, String fieldName) {
        Object field = XposedHelpers.getObjectField(sliderView, fieldName);
        return field instanceof FrameLayout ? (FrameLayout) field : null;
    }

    private boolean isBrightnessSlider(Object sliderView, Object view) {
        Object slider = XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
        return slider == view;
    }

    private boolean isVolumeSlider(Object sliderView, Object view) {
        Object slider = XposedHelpers.getObjectField(sliderView, "mMediaVolumeSlider");
        return slider == view;
    }

    private Object findToggleSliderView(View seekBar) {
        ViewParent parent = seekBar.getParent();
        if (parent instanceof View) {
            View grandParent = ((View) parent).getParent() instanceof View ? (View) ((View) parent).getParent() : null;
            if (grandParent != null && TOGGLE_SLIDER_VIEW_CLASS.equals(grandParent.getClass().getName())) {
                return grandParent;
            }
            if (TOGGLE_SLIDER_VIEW_CLASS.equals(parent.getClass().getName())) {
                return parent;
            }
        }
        View current = seekBar;
        for (int i = 0; i < 4 && current != null; i++) {
            ViewParent viewParent = current.getParent();
            if (!(viewParent instanceof View)) {
                break;
            }
            current = (View) viewParent;
            if (TOGGLE_SLIDER_VIEW_CLASS.equals(current.getClass().getName())) {
                return current;
            }
        }
        return null;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
