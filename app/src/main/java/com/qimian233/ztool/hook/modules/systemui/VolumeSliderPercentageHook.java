package com.qimian233.ztool.hook.modules.systemui;

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

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public class VolumeSliderPercentageHook extends BaseHookModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView";
    private static final String SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent";
    private static final String VOLUME_ROOT_FIELD = "mVolumeSliderRoot";
    private static final String VOLUME_ICON_FIELD = "mMediaVolumeIconMark";
    private static final String PREF_KEY = "volume_slider_percentage";
    private static final int LABEL_GAP_DP = 2;

    private static boolean PERCENTAGE_ENABLED = false;

    private final Map<View, View.OnLayoutChangeListener> layoutListeners = new WeakHashMap<>();
    private final Map<FrameLayout, Boolean> pendingPositionUpdates = new WeakHashMap<>();

    public VolumeSliderPercentageHook() {}

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
        String packageName = param.getPackageName();
        updatePrefs();
        hookToggleSliderViewLifecycle(classLoader);
        hookVolumeControllerCallbacks(classLoader);
        hookSeekProgressChanges(classLoader);
        log("Volume slider percentage hooks installed");
    }

    private void hookVolumeControllerCallbacks(ClassLoader classLoader) {
        try {
            Method updateMusicSliderMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("updateMusicSlider");
            this.xposed.hook(updateMusicSliderMethod).intercept(chain -> {
                Object result = chain.proceed();
                refreshVolumeFromToggleSlider(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {}

        try {
            Method registerVolumeObserverMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("registerVolumeObserver");
            this.xposed.hook(registerVolumeObserverMethod).intercept(chain -> {
                Object result = chain.proceed();
                refreshVolumeFromToggleSlider(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void hookToggleSliderViewLifecycle(ClassLoader classLoader) {
        try {
            Constructor<?> ctor = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredConstructor(Context.class, android.util.AttributeSet.class, int.class);
            this.xposed.hook(ctor).intercept(chain -> {
                chain.proceed();
                attachSliderLabel(chain.getThisObject());
                return null;
            });
        } catch (Throwable ignored) {}

        try {
            Method updateVolumeSliderMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("updateVolumeSlider");
            this.xposed.hook(updateVolumeSliderMethod).intercept(chain -> {
                Object result = chain.proceed();
                attachSliderLabel(chain.getThisObject());
                refreshVolumeLabel(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {}

        try {
            Method setVolumeProgressMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("setVolumeProgress", int.class);
            this.xposed.hook(setVolumeProgressMethod).intercept(chain -> {
                Object result = chain.proceed();
                refreshVolumeLabel(chain.getThisObject());
                return result;
            });
        } catch (Throwable ignored) {}

        try {
            Method refreshSeekBarMethod = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                    .getDeclaredMethod("refreshSeekBar", ProgressBar.class);
            this.xposed.hook(refreshSeekBarMethod).intercept(chain -> {
                Object result = chain.proceed();
                Object sliderView = chain.getThisObject();
                ProgressBar progressBar = (ProgressBar) chain.getArg(0);
                if (isVolumeSlider(sliderView, progressBar)) {
                    refreshVolumeLabel(sliderView);
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void hookSeekProgressChanges(ClassLoader classLoader) {
        try {
            Method onProgressChangedMethod = classLoader
                    .loadClass("com.android.systemui.settings.ToggleSliderView$2")
                    .getDeclaredMethod("onProgressChanged", SeekBar.class, int.class, boolean.class);
            this.xposed.hook(onProgressChangedMethod).intercept(chain -> {
                Object result = chain.proceed();
                SeekBar seekBar = (SeekBar) chain.getArg(0);
                Object sliderView = findToggleSliderView(seekBar);
                if (sliderView == null || !isVolumeSlider(sliderView, seekBar)) {
                    return result;
                }
                if (Boolean.TRUE.equals(chain.getArg(2))) {
                    refreshVolumeLabel(sliderView, (Integer) chain.getArg(1));
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void attachSliderLabel(Object sliderView) {
        try {
            attachLabelToRoot(sliderView);
        } catch (Throwable t) {
            logError("Failed to attach volume slider label", t);
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
        Integer rawProgress = getVolumeRawProgress(sliderView, null);
        if (rawProgress != null) {
            updateVolumePercentColor(percentView, rawProgress);
        }
        refreshVolumeLabel(sliderView, percentView, null);
    }

    private void refreshVolumeLabel(Object sliderView) {
        refreshVolumeLabel(sliderView, null);
    }

    private void refreshVolumeLabel(Object sliderView, Integer rawProgress) {
        if (!PERCENTAGE_ENABLED) {
            detachVolumeLabel(sliderView);
            return;
        }
        FrameLayout root = getFrameLayoutField(sliderView);
        if (root == null) {
            return;
        }
        TextView percentView = findPercentView(root);
        if (percentView != null) {
            refreshVolumeLabel(sliderView, percentView, rawProgress);
        }
    }

    private void refreshVolumeLabel(Object sliderView, TextView percentView, Integer rawProgress) {
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
            Integer rawProgress2 = getVolumeRawProgress(sliderView, rawProgress);
            Integer volumeProgress = getVolumeProgress(sliderView, rawProgress);
            if (rawProgress2 == null || volumeProgress == null) {
                setPercentTextIfChanged(percentView, "--%");
                return;
            }
            updateVolumePercentColor(percentView, rawProgress2);
            setPercentTextIfChanged(percentView, formatPercent(volumeProgress));
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            setPercentTextIfChanged(percentView, "--%");
            if (DEBUG) {
                logError("Failed to refresh volume percent", t);
            }
        }
    }

    private void refreshVolumeFromToggleSlider(Object sliderView) {
        refreshVolumeFromToggleSlider(sliderView, null);
    }

    private void refreshVolumeFromToggleSlider(Object sliderView, Integer rawProgress) {
        if (!PERCENTAGE_ENABLED || sliderView == null) {
            return;
        }
        try {
            Integer rawProgress2 = getVolumeRawProgress(sliderView, rawProgress);
            Integer volumeProgress = getVolumeProgress(sliderView, rawProgress);
            if (rawProgress2 == null || volumeProgress == null) {
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
            updateVolumePercentColor(percentView, rawProgress2);
            if (root != null && root.isInLayout()) {
                schedulePositionUpdate(sliderView);
                return;
            }
            setPercentTextIfChanged(percentView, formatPercent(volumeProgress));
            schedulePositionUpdate(sliderView);
        } catch (Throwable t) {
            if (DEBUG) {
                logError("Failed to refresh volume from toggle slider", t);
            }
        }
    }

    private Integer getVolumeRawProgress(Object sliderView, Integer rawProgress) {
        try {
            SeekBar volumeSlider = (SeekBar) sliderView.getClass()
                    .getDeclaredField("mMediaVolumeSlider").get(sliderView);
            if (volumeSlider == null) {
                return null;
            }
            return rawProgress != null ? rawProgress : volumeSlider.getProgress();
        } catch (Throwable t) {
            return null;
        }
    }

    private Integer getVolumeProgress(Object sliderView, Integer rawProgress) {
        try {
            SeekBar volumeSlider = (SeekBar) sliderView.getClass()
                    .getDeclaredField("mMediaVolumeSlider").get(sliderView);
            if (volumeSlider == null) {
                return null;
            }
            int progress = rawProgress != null ? rawProgress : volumeSlider.getProgress();
            int min = volumeSlider.getMin();
            int max = volumeSlider.getMax();
            int range = Math.max(1, max - min);
            int percent = Math.round(((progress - min) * 100f) / range);
            percent = Math.max(0, Math.min(100, percent));
            return percent;
        } catch (Throwable t) {
            if (DEBUG) {
                logError("Failed to resolve volume progress", t);
            }
            return null;
        }
    }

    private String formatPercent(int progress) {
        int range = Math.max(1, 100);
        int value = Math.round(((progress) * 100f) / range);
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
            Object field = sliderView.getClass().getDeclaredField(VOLUME_ROOT_FIELD).get(sliderView);
            return field instanceof FrameLayout ? (FrameLayout) field : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private View getViewField(Object sliderView) {
        try {
            Object field = sliderView.getClass().getDeclaredField(VOLUME_ICON_FIELD).get(sliderView);
            return field instanceof View ? (View) field : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isVolumeSlider(Object sliderView, Object view) {
        try {
            Object slider = sliderView.getClass().getDeclaredField("mMediaVolumeSlider").get(sliderView);
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

    private void detachVolumeLabel(Object sliderView) {
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

    private void updateVolumePercentColor(TextView percentView, int seekBarProgress) {
        percentView.setTextColor(resolveVolumePercentColor(seekBarProgress));
    }

    private int resolveVolumePercentColor(int seekBarProgress) {
        float progress = (seekBarProgress * 1.0f) / 15000.0f;
        if (progress < 0.2f) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8);
        }
        int gray = (int) ((1.0f - Math.min((progress - 0.2f) / 0.2f, 1.0f)) * 216.0f);
        gray = Math.max(gray, 0x80);
        int alpha = Math.min(((int) Math.floor(progress * 85.0f)) + 170, 255);
        return Color.argb(alpha, gray, gray, gray);
    }

    private void updatePrefs() {
        try {
            PERCENTAGE_ENABLED = this.xposed.getRemotePreferences("xposed_module_config").getBoolean(PREF_KEY, false);
        } catch (Throwable t) {
            PERCENTAGE_ENABLED = false;
        }
    }
}
