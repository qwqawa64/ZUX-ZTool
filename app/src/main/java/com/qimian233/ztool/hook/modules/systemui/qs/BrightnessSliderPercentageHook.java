package com.qimian233.ztool.hook.modules.systemui.qs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

@SuppressLint("PrivateApi")
public class BrightnessSliderPercentageHook extends AppHookModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView";
    private static final String SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent";
    private static final String BRIGHTNESS_ROOT_FIELD = "mBrightnessSliderRoot";
    private static final String BRIGHTNESS_ICON_FIELD = "mBrightnessIconMark";
    private static final int LABEL_GAP_DP = 2;
    private static final String PREF_KEY = "brightness_slider_percentage";

    private static boolean PERCENTAGE_ENABLED = false;

    private final Map<View, View.OnLayoutChangeListener> layoutListeners = new WeakHashMap<>();
    private final Map<FrameLayout, Boolean> pendingPositionUpdates = new WeakHashMap<>();

    public BrightnessSliderPercentageHook() {}

    @Override
    public String getModuleName() {
        return PREF_KEY;
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEM_UI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        updatePrefs();
        hookToggleSliderViewLifecycle(classLoader);
        hookBrightnessControllerCallbacks(classLoader);
        hookSeekProgressChanges();
        logger.info("Brightness slider percentage hooks installed");
    }

    private void hookBrightnessControllerCallbacks(ClassLoader classLoader) {
        try {
            Method onChangedMethod = classLoader
                    .loadClass("com.android.systemui.settings.brightness.BrightnessController")
                    .getDeclaredMethod("onChanged", int.class, boolean.class, boolean.class);
            hookWithId(onChangedMethod, "on_changed", chain -> {
                Object result = chain.proceed();
                refreshBrightnessFromController(chain.getThisObject(), (Integer) chain.getArg(0));
                return result;
            });
        } catch (Throwable ignored) {}

        try {
            Method setValueMethod = classLoader
                    .loadClass("com.android.systemui.settings.brightness.BrightnessSliderController")
                    .getDeclaredMethod("setValue", int.class);
            hookWithId(setValueMethod, "set_value", chain -> {
                Object result = chain.proceed();
                Object sliderController = chain.getThisObject();
                Class<?> scCls = sliderController.getClass();
                try {
                    scCls.getDeclaredField("mBrightnessSliderHapticPlugin");
                } catch (NoSuchFieldException ignored) {
                    logger.warn("Field mBrightnessSliderHapticPlugin not found, shouldn't hook mView of this class!");
                    return chain.proceed();
                }
                Object view = findField(scCls, "mView").get(sliderController);
                if (view instanceof View) {
                    refreshBrightnessFromView((View) view, (Integer) chain.getArg(0));
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void hookToggleSliderViewLifecycle(ClassLoader classLoader) {
        try {
            Constructor<?> ctor = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredConstructor(Context.class, android.util.AttributeSet.class, int.class);
            hookWithId(ctor, "ctor", chain -> {
                chain.proceed();
                attachSliderLabel(chain.getThisObject());
                return null;
            });
        } catch (Throwable ignored) {}

        try {
            Method updateBrightnessMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("updateBrightnessSlider");
            hookWithId(updateBrightnessMethod, "update_brightness", chain -> {
                Object result = chain.proceed();
                attachSliderLabel(chain.getThisObject());
                refreshBrightnessLabel(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {}

        try {
            Method refreshSeekBarMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("refreshSeekBar", ProgressBar.class);
            hookWithId(refreshSeekBarMethod, "refresh_seek_bar", chain -> {
                Object result = chain.proceed();
                Object sliderView = chain.getThisObject();
                ProgressBar progressBar = (ProgressBar) chain.getArg(0);
                if (isBrightnessSlider(sliderView, progressBar)) {
                    refreshBrightnessLabel(sliderView);
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void hookSeekProgressChanges() {
        try {
            Method setProgressMethod = SeekBar.class.getDeclaredMethod("setProgress", int.class);
            hookWithId(setProgressMethod, "set_progress", chain -> {
                Object result = chain.proceed();
                SeekBar seekBar = (SeekBar) chain.getThisObject();
                Object sliderView = findToggleSliderView(seekBar);
                if (sliderView == null) {
                    return result;
                }
                if (isBrightnessSlider(sliderView, seekBar)) {
                    refreshBrightnessLabel(sliderView);
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void attachSliderLabel(Object sliderView) {
        try {
            attachLabelToRoot(sliderView);
        } catch (Throwable t) {
            logger.error("Failed to attach brightness slider label", t);
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
            FrameLayout root = getFrameLayoutField(sliderView);
            if (root != null && root.isInLayout()) {
                schedulePositionUpdate(sliderView);
                return;
            }
            Class<?> cl = sliderView.getClass();
            SeekBar brightnessSlider = (SeekBar) cl.getDeclaredField("mBrightnessSlider").get(sliderView);
            if (brightnessSlider == null) {
                setPercentTextIfChanged(percentView, "--%");
                return;
            }
            setPercentTextIfChanged(percentView, formatPercent(
                    brightnessSlider.getProgress(),
                    brightnessSlider.getMin(),
                    brightnessSlider.getMax()
            ));
            updateBrightnessPercentColor(sliderView, percentView);
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            setPercentTextIfChanged(percentView, "--%");
            logger.error("Failed to refresh brightness percent", t);
        }
    }

    private void refreshBrightnessFromController(Object brightnessController, int progress) {
        if (!PERCENTAGE_ENABLED || brightnessController == null) {
            return;
        }
        try {
            Class<?> bcCls = brightnessController.getClass();
            Object control = bcCls.getDeclaredField("mControl").get(brightnessController);
            if (control == null) {
                return;
            }
            try {
                bcCls.getDeclaredField("mBrightnessObserver");
            } catch (NoSuchFieldException ignored) {
                logger.warn("Field mBrightnessObserver not found, shouldn't hook mView of this class!");
                return;
            }
            Object view = findField(control.getClass(), "mView").get(control);
            if (!(view instanceof View)) {
                return;
            }
            refreshBrightnessFromView((View) view, progress);
        } catch (Throwable t) {
            logger.error("Failed to refresh brightness from controller", t);
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
            Class<?> cl = sliderView.getClass();
            SeekBar brightnessSlider = (SeekBar) cl.getDeclaredField("mBrightnessSlider").get(sliderView);
            int max = brightnessSlider != null ? brightnessSlider.getMax() : 65535;
            max = Math.max(1, max);
            int percent = Math.max(0, Math.min(100, Math.round((progress * 100f) / max)));
            if (root.isInLayout()) {
                schedulePositionUpdate(sliderView);
                return;
            }
            setPercentTextIfChanged(percentView, String.format(Locale.US, "%d%%", percent));
            updateBrightnessPercentColor(sliderView, percentView);
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            logger.error("Failed to refresh brightness from view", t);
        }
    }

    private void updateBrightnessPercentColor(Object sliderView, TextView percentView) {
        percentView.setTextColor(resolveBrightnessPercentColor(sliderView));
    }

    private int resolveBrightnessPercentColor(Object sliderView) {
        SeekBar brightnessSlider;
        try {
            brightnessSlider = sliderView instanceof SeekBar
                    ? (SeekBar) sliderView
                    : (SeekBar) sliderView.getClass().getDeclaredField("mBrightnessSlider").get(sliderView);
        } catch (Throwable t) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8);
        }
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
        try {
            Object field = sliderView.getClass().getDeclaredField(BRIGHTNESS_ROOT_FIELD).get(sliderView);
            return field instanceof FrameLayout ? (FrameLayout) field : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private View getViewField(Object sliderView) {
        try {
            Object field = sliderView.getClass().getDeclaredField(BRIGHTNESS_ICON_FIELD).get(sliderView);
            return field instanceof View ? (View) field : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isBrightnessSlider(Object sliderView, Object view) {
        try {
            Object slider = sliderView.getClass().getDeclaredField("mBrightnessSlider").get(sliderView);
            return slider == view;
        } catch (Throwable t) {
            return false;
        }
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
            schedulePositionUpdate(root, icon, percentView);
            return;
        }

        View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                schedulePositionUpdate(root, icon, percentView);
        layoutListeners.put(root, listener);
        root.addOnLayoutChangeListener(listener);
        schedulePositionUpdate(root, icon, percentView);
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
        schedulePositionUpdate(root, icon, percentView);
    }

    private void schedulePositionUpdate(FrameLayout root, View icon, TextView percentView) {
        if (root == null || icon == null || percentView == null) {
            return;
        }
        if (Boolean.TRUE.equals(pendingPositionUpdates.get(root))) {
            return;
        }
        pendingPositionUpdates.put(root, Boolean.TRUE);
        root.post(() -> {
            try {
                positionLabel(root, icon, percentView);
            } finally {
                pendingPositionUpdates.remove(root);
            }
        });
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
        if (params.leftMargin == targetLeft && params.topMargin == targetTop
                && params.width == ViewGroup.LayoutParams.WRAP_CONTENT
                && params.height == ViewGroup.LayoutParams.WRAP_CONTENT
                && params.gravity == (android.view.Gravity.TOP | android.view.Gravity.START)) {
            return;
        }
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        percentView.setLayoutParams(params);
    }

    private void setPercentTextIfChanged(TextView percentView, String text) {
        if (percentView == null || TextUtils.equals(percentView.getText(), text)) {
            return;
        }
        percentView.setText(text);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void updatePrefs() {
        try {
            PERCENTAGE_ENABLED = this.xposed.getRemotePreferences("xposed_module_config").getBoolean(PREF_KEY, false);
        } catch (Throwable t) {
            PERCENTAGE_ENABLED = false;
        }
    }
}
