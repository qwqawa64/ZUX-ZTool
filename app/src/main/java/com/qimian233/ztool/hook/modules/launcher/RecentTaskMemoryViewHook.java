package com.qimian233.ztool.hook.modules.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RecentTaskMemoryViewHook extends BaseHookModule {
    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String MEMORY_VIEW_TAG = "ztool_recent_task_memory_view";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final Map<TextView, Runnable> updateRunnables = new WeakHashMap<>();
    private final Map<View, Boolean> overviewEnabledStates = new WeakHashMap<>();

    @Override
    public String getModuleName() {
        return "launcher_recent_task_memory_view";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                RECENTS_VIEW_CLASS,
                lpparam.classLoader,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        attachMemoryView((View) param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                RECENTS_VIEW_CLASS,
                lpparam.classLoader,
                "onDetachedFromWindow",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        detachMemoryView((View) param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                RECENTS_VIEW_CLASS,
                lpparam.classLoader,
                "setOverviewStateEnabled",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View recentsView = (View) param.thisObject;
                        boolean enabled = (boolean) param.args[0];
                        overviewEnabledStates.put(recentsView, enabled);
                        updateMemoryViewVisibility(recentsView);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                RECENTS_VIEW_CLASS,
                lpparam.classLoader,
                "setVisibility",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        updateMemoryViewVisibility((View) param.thisObject);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                RECENTS_VIEW_CLASS,
                lpparam.classLoader,
                "onLayout",
                boolean.class,
                int.class,
                int.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        attachMemoryView((View) param.thisObject);
                    }
                }
        );

        log("Recent task memory view hooks installed");
    }

    private void attachMemoryView(View recentsView) {
        try {
            ViewGroup dragLayer = getDragLayer(recentsView);
            if (dragLayer == null) {
                return;
            }

            TextView memoryView = findMemoryView(dragLayer);
            if (memoryView == null) {
                memoryView = createMemoryView(recentsView.getContext());
                dragLayer.addView(memoryView, createLayoutParams(recentsView.getContext()));
                if (DEBUG) log("Memory view added to launcher drag layer");
            } else {
                ensureLayoutParams(memoryView, recentsView.getContext());
            }

            refreshMemoryText(memoryView);
            updateMemoryViewVisibility(recentsView, memoryView);
        } catch (Throwable t) {
            logError("Failed to attach memory view", t);
        }
    }

    private void detachMemoryView(View recentsView) {
        try {
            ViewGroup dragLayer = getDragLayer(recentsView);
            if (dragLayer == null) {
                return;
            }

            TextView memoryView = findMemoryView(dragLayer);
            if (memoryView != null) {
                stopRefreshing(memoryView);
                dragLayer.removeView(memoryView);
                updateRunnables.remove(memoryView);
                if (DEBUG) log("Memory view removed from launcher drag layer");
            }
            overviewEnabledStates.remove(recentsView);
        } catch (Throwable t) {
            logError("Failed to detach memory view", t);
        }
    }

    private TextView createMemoryView(Context context) {
        TextView textView = new TextView(context);
        textView.setTag(MEMORY_VIEW_TAG);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setIncludeFontPadding(false);
        textView.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        textView.setBackground(createBackground());
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setClickable(false);
        textView.setFocusable(false);
        textView.setElevation(dp(context, 4));
        return textView;
    }

    private GradientDrawable createBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x99000000);
        background.setCornerRadius(999f);
        background.setStroke(1, 0x33FFFFFF);
        return background;
    }

    private FrameLayout.LayoutParams createLayoutParams(Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(context, 96);
        return params;
    }

    private void ensureLayoutParams(TextView memoryView, Context context) {
        ViewGroup.LayoutParams currentParams = memoryView.getLayoutParams();
        if (!(currentParams instanceof FrameLayout.LayoutParams)) {
            memoryView.setLayoutParams(createLayoutParams(context));
            return;
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) currentParams;
        int bottomMargin = dp(context, 96);
        if (params.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                || params.bottomMargin != bottomMargin) {
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.bottomMargin = bottomMargin;
            memoryView.setLayoutParams(params);
        }
    }

    private ViewGroup getDragLayer(View recentsView) {
        Object container = XposedHelpers.getObjectField(recentsView, "mContainer");
        Object dragLayer = XposedHelpers.callMethod(container, "getDragLayer");
        return dragLayer instanceof ViewGroup ? (ViewGroup) dragLayer : null;
    }

    private TextView findMemoryView(ViewGroup dragLayer) {
        View view = dragLayer.findViewWithTag(MEMORY_VIEW_TAG);
        return view instanceof TextView ? (TextView) view : null;
    }

    private void updateMemoryViewVisibility(View recentsView) {
        try {
            ViewGroup dragLayer = getDragLayer(recentsView);
            if (dragLayer == null) {
                return;
            }

            TextView memoryView = findMemoryView(dragLayer);
            if (memoryView != null) {
                updateMemoryViewVisibility(recentsView, memoryView);
            }
        } catch (Throwable t) {
            logError("Failed to update memory view visibility", t);
        }
    }

    private void updateMemoryViewVisibility(View recentsView, TextView memoryView) {
        boolean overviewEnabled = Boolean.TRUE.equals(overviewEnabledStates.get(recentsView));
        boolean visible = overviewEnabled && recentsView.getVisibility() == View.VISIBLE;
        memoryView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            refreshMemoryText(memoryView);
        } else {
            stopRefreshing(memoryView);
        }
    }

    private void refreshMemoryText(TextView memoryView) {
        stopRefreshing(memoryView);
        updateMemoryText(memoryView);

        Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (!memoryView.isAttachedToWindow() || memoryView.getVisibility() != View.VISIBLE) {
                    return;
                }
                updateMemoryText(memoryView);
                memoryView.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
        updateRunnables.put(memoryView, updater);

        if (memoryView.getVisibility() == View.VISIBLE) {
            memoryView.postDelayed(updater, REFRESH_INTERVAL_MS);
        }
    }

    private void stopRefreshing(TextView memoryView) {
        Runnable updater = updateRunnables.remove(memoryView);
        if (updater != null) {
            memoryView.removeCallbacks(updater);
        }
    }

    private void updateMemoryText(TextView memoryView) {
        try {
            Context context = memoryView.getContext();
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                memoryView.setText("RAM -- / --");
                return;
            }

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            long usedMemory = Math.max(0L, memoryInfo.totalMem - memoryInfo.availMem);
            memoryView.setText(String.format(
                    Locale.US,
                    "RAM %s / %s",
                    formatBytes(usedMemory),
                    formatBytes(memoryInfo.totalMem)
            ));
        } catch (Throwable t) {
            memoryView.setText("RAM -- / --");
            if (DEBUG) logError("Failed to update memory text", t);
        }
    }

    private String formatBytes(long bytes) {
        double gib = bytes / 1073741824.0d;
        if (gib >= 10.0d) {
            return String.format(Locale.US, "%.0f GB", gib);
        }
        return String.format(Locale.US, "%.1f GB", gib);
    }

    private int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }
}
