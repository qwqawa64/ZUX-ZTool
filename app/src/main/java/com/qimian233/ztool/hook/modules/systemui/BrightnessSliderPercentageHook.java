package com.qimian233.ztool.hook.modules.systemui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BrightnessSliderPercentageHook extends BaseHookModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView";
    private static final String SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent";
    private static final String BRIGHTNESS_ROOT_FIELD = "mBrightnessSliderRoot";
    private static final String BRIGHTNESS_ICON_FIELD = "mBrightnessIconMark";
    private static final int LABEL_GAP_DP = 2;
    private static final String PREF_KEY = "brightness_slider_percentage";

    private static boolean PERCENTAGE_ENABLED = false;

    private final Map<View, View.OnLayoutChangeListener> layoutListeners = new WeakHashMap<>();

    @Override
    public String getModuleName() {
        return PREF_KEY;
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEM_UI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        updatePrefs();
        hookToggleSliderViewLifecycle(lpparam);
        hookBrightnessControllerCallbacks(lpparam);
        hookSeekProgressChanges();
        log("Brightness slider percentage hooks installed");
    }

    private void hookBrightnessControllerCallbacks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessController",
                lpparam.classLoader,
                "onChanged",
                int.class,
                boolean.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        refreshBrightnessFromController(param.thisObject, (Integer) param.args[0]);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderController",
                lpparam.classLoader,
                "setValue",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object sliderController = param.thisObject;
                        Object view = XposedHelpers.getObjectField(sliderController, "mView");
                        if (view instanceof View) {
                            refreshBrightnessFromView((View) view, (Integer) param.args[0]);
                        }
                    }
                }
        );
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
                        attachSliderLabel(param.thisObject);
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
                        attachSliderLabel(param.thisObject);
                        refreshBrightnessLabel(param.thisObject);
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

    private void hookSeekProgressChanges() {
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
                        }
                    }
                }
        );
    }

    private void attachSliderLabel(Object sliderView) {
        try {
            attachLabelToRoot(sliderView);
        } catch (Throwable t) {
            logError("Failed to attach brightness slider label", t);
        }
    }

    private void attachLabelToRoot(Object sliderView) {
        FrameLayout root = getFrameLayoutField(sliderView);
        View icon = getViewField(sliderView);
        if (root == null || icon == null) {
            return;
        }

        if (!PERCENTAGE_ENABLED) {
            removePercentView(root);
            return;
        }

        TextView percentView = findPercentView(root);
        if (percentView == null) {
            percentView = createPercentView(root.getContext());
            root.addView(percentView, createLayoutParams());
        }

        ensureLayoutTracking(root, icon, percentView);
        updateBrightnessPercentColor(sliderView, percentView);
        refreshBrightnessLabel(sliderView, percentView);
    }

    private void refreshBrightnessLabel(Object sliderView) {
        if (!PERCENTAGE_ENABLED) {
            detachBrightnessLabel(sliderView);
            return;
        }
        FrameLayout root = getFrameLayoutField(sliderView);
        if (root == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView != null) {
            refreshBrightnessLabel(sliderView, percentView);
        }
    }

    private void refreshBrightnessLabel(Object sliderView, TextView percentView) {
        if (!PERCENTAGE_ENABLED) {
            removePercentView(getFrameLayoutField(sliderView));
            return;
        }
        try {
            SeekBar brightnessSlider = (SeekBar) XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
            if (brightnessSlider == null) {
                percentView.setText("--%");
                return;
            }
            percentView.setText(formatPercent(
                    brightnessSlider.getProgress(),
                    brightnessSlider.getMin(),
                    brightnessSlider.getMax()
            ));
            updateBrightnessPercentColor(sliderView, percentView);
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            percentView.setText("--%");
            if (DEBUG) {
                logError("Failed to refresh brightness percent", t);
            }
        }
    }

    private void refreshBrightnessFromController(Object brightnessController, int progress) {
        if (!PERCENTAGE_ENABLED || brightnessController == null) {
            return;
        }
        try {
            Object control = XposedHelpers.getObjectField(brightnessController, "mControl");
            if (control == null) {
                return;
            }
            Object view = XposedHelpers.getObjectField(control, "mView");
            if (!(view instanceof View)) {
                return;
            }
            refreshBrightnessFromView((View) view, progress);
        } catch (Throwable t) {
            if (DEBUG) {
                logError("Failed to refresh brightness from controller", t);
            }
        }
    }

    private void refreshBrightnessFromView(View view, int progress) {
        if (view == null) {
            return;
        }
        Object sliderView = findToggleSliderView(view);
        if (sliderView == null) {
            return;
        }
        FrameLayout root = getFrameLayoutField(sliderView);
        if (root == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView == null) {
            return;
        }

        try {
            SeekBar brightnessSlider = (SeekBar) XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
            int max = brightnessSlider != null ? brightnessSlider.getMax() : 65535;
            max = Math.max(1, max);
            int percent = Math.max(0, Math.min(100, Math.round((progress * 100f) / max)));
            percentView.setText(String.format(Locale.US, "%d%%", percent));
            updateBrightnessPercentColor(sliderView, percentView);
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            if (DEBUG) {
                logError("Failed to refresh brightness from view", t);
            }
        }
    }

    private void updateBrightnessPercentColor(Object sliderView, TextView percentView) {
        percentView.setTextColor(resolveBrightnessPercentColor(sliderView));
    }

    private int resolveBrightnessPercentColor(Object sliderView) {
        SeekBar brightnessSlider = sliderView instanceof SeekBar
                ? (SeekBar) sliderView
                : (SeekBar) XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
        if (brightnessSlider == null) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8);
        }
        float progress = ((brightnessSlider.getProgress() - brightnessSlider.getMin()) * 1.0f)
                / Math.max(1, brightnessSlider.getMax() - brightnessSlider.getMin());
        if (progress < 0.2f) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8);
        }
        int gray = (int) ((1.0f - Math.min((progress - 0.2f) / 0.2f, 1.0f)) * 216.0f);
        gray = Math.max(gray, 0x80);
        int alpha = Math.min(((int) Math.floor(progress * 85.0f)) + 170, 255);
        return Color.argb(alpha, gray, gray, gray);
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
        textView.setTextColor(Color.argb(0xff, 0xd8, 0xd8, 0xd8));
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextSize(13f);
        textView.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        textView.setSingleLine(true);
        textView.setIncludeFontPadding(false);
        textView.setGravity(android.view.Gravity.CENTER);
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setClickable(false);
        textView.setFocusable(false);
        textView.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2));
        textView.setElevation(dp(context, 2));
        return textView;
    }

    private FrameLayout.LayoutParams createLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        return params;
    }

    private TextView findPercentView(FrameLayout root) {
        View view = root.findViewWithTag(SLIDER_PERCENT_TAG);
        return view instanceof TextView ? (TextView) view : null;
    }

    private FrameLayout getFrameLayoutField(Object sliderView) {
        Object field = XposedHelpers.getObjectField(sliderView, BrightnessSliderPercentageHook.BRIGHTNESS_ROOT_FIELD);
        return field instanceof FrameLayout ? (FrameLayout) field : null;
    }

    private View getViewField(Object sliderView) {
        Object field = XposedHelpers.getObjectField(sliderView, BrightnessSliderPercentageHook.BRIGHTNESS_ICON_FIELD);
        return field instanceof View ? (View) field : null;
    }

    private boolean isBrightnessSlider(Object sliderView, Object view) {
        Object slider = XposedHelpers.getObjectField(sliderView, "mBrightnessSlider");
        return slider == view;
    }

    private Object findToggleSliderView(View seekBar) {
        View current = seekBar;
        for (int i = 0; i < 5 && current != null; i++) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            current = (View) parent;
            if (TOGGLE_SLIDER_VIEW_CLASS.equals(current.getClass().getName())) {
                return current;
            }
        }
        return null;
    }

    private void ensureLayoutTracking(FrameLayout root, View icon, TextView percentView) {
        if (layoutListeners.containsKey(root)) {
            root.post(() -> positionLabel(root, icon, percentView));
            return;
        }

        View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> positionLabel(root, icon, percentView);
        layoutListeners.put(root, listener);
        root.addOnLayoutChangeListener(listener);
        root.post(() -> positionLabel(root, icon, percentView));
    }

    private void detachBrightnessLabel(Object sliderView) {
        removePercentView(getFrameLayoutField(sliderView));
    }

    private void removePercentView(FrameLayout root) {
        if (root == null) {
            return;
        }
        View view = root.findViewWithTag(SLIDER_PERCENT_TAG);
        if (view != null) {
            root.removeView(view);
        }
        View.OnLayoutChangeListener listener = layoutListeners.remove(root);
        if (listener != null) {
            root.removeOnLayoutChangeListener(listener);
        }
    }

    private void schedulePositionUpdate(Object sliderView) {
        FrameLayout root = getFrameLayoutField(sliderView);
        View icon = getViewField(sliderView);
        if (root == null || icon == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView == null) {
            return;
        }
        root.post(() -> positionLabel(root, icon, percentView));
    }

    private void positionLabel(FrameLayout root, View icon, TextView percentView) {
        if (root == null || icon == null || percentView == null) {
            return;
        }

        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0) {
            return;
        }

        if (percentView.getMeasuredWidth() == 0 || percentView.getMeasuredHeight() == 0) {
            percentView.measure(
                    View.MeasureSpec.makeMeasureSpec(rootWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(rootHeight, View.MeasureSpec.AT_MOST)
            );
        }

        int labelWidth = Math.max(1, percentView.getMeasuredWidth());
        int labelHeight = Math.max(1, percentView.getMeasuredHeight());
        int iconCenterX = icon.getLeft() + (icon.getWidth() / 2);
        int targetLeft = iconCenterX - (labelWidth / 2);
        int targetTop = icon.getBottom() + dp(root.getContext(), LABEL_GAP_DP);

        targetLeft = Math.max(0, Math.min(targetLeft, Math.max(0, rootWidth - labelWidth)));
        targetTop = Math.max(0, Math.min(targetTop, Math.max(0, rootHeight - labelHeight)));

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) percentView.getLayoutParams();
        if (params == null) {
            params = createLayoutParams();
        }
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        percentView.setLayoutParams(params);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void updatePrefs() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        PERCENTAGE_ENABLED = prefs.getBoolean(PREF_KEY, false);
    }
}
