package com.qimian233.ztool.hook.modules.launcher;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

@SuppressLint("PrivateApi")
public class RecentTaskMemoryViewHook extends BaseHookModule {
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";
    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";
    private static final String RECENTS_VIEW_CLASS = "com.android.quickstep.views.RecentsView";
    private static final String MEMORY_VIEW_TAG = "ztool_recent_task_memory_view";
    private static final long REFRESH_INTERVAL_MS = 1000L;
    private static final String STRING_RAM_FORMATTER = "ram_formatter";
    private static final String STRING_RAM_UNAVAILABLE = "ram_unavailable";
    private static final String FALLBACK_RAM_FORMATTER = "%s / %s";
    private static final String FALLBACK_RAM_UNAVAILABLE = "-- / --";

    private static final String PROP_MEMORY_EXPANSION_LIST = "persist.sys.zram_wb_list";
    private static final String PROP_MEMORY_EXPANSION_ENABLED = "persist.sys.zram_wb_enabled";
    private static final String PROP_MEMORY_EXPANSION_SIZE = "persist.sys.zram_wb_size";
    private static final String SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties";
    private static final String SYSTEM_PROPERTIES_GET_WITH_DEFAULT = "get";

    private final Map<TextView, Runnable> updateRunnables = new WeakHashMap<>();
    private final Map<View, Boolean> overviewEnabledStates = new WeakHashMap<>();
    private volatile String cachedRamFormatter;
    private volatile String cachedRamUnavailable;
    private volatile Method systemPropertiesGetWithDefaultMethod;

    public RecentTaskMemoryViewHook() {}

    @Override
    public String getModuleName() {
        return "launcher_recent_task_memory_view";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        try {
            Class<?> recentsViewClass = classLoader.loadClass(RECENTS_VIEW_CLASS);

            Method onAttachedMethod = recentsViewClass.getDeclaredMethod("onAttachedToWindow");
            this.xposed.hook(onAttachedMethod).intercept(chain -> {
                try {
                    chain.proceed();
                    attachMemoryView((View) chain.getThisObject());
                    log("onAttachedToWindow hook executed successfully.");
                } catch (Exception e) {
                    logError("Failed to hook onAttachedToWindow: ", e);
                }
                return null;
            });

            Method onDetachedMethod = recentsViewClass.getDeclaredMethod("onDetachedFromWindow");
            this.xposed.hook(onDetachedMethod).intercept(chain -> {
                try {
                    detachMemoryView((View) chain.getThisObject());
                    log("onDetachedFromWindow hook executed successfully");
                } catch (Exception e) {
                    logError("Failed to hook onDetachedFromWindow: ", e);
                }
                return chain.proceed();
            });

            Method setOverviewMethod = recentsViewClass.getDeclaredMethod("setOverviewStateEnabled", boolean.class);
            this.xposed.hook(setOverviewMethod).intercept(chain -> {
                chain.proceed();
                View recentsView = (View) chain.getThisObject();
                boolean enabled = (boolean) chain.getArg(0);
                overviewEnabledStates.put(recentsView, enabled);
                updateMemoryViewVisibility(recentsView);
                log("setOverviewStateEnabled hook executed successfully");
                return null;
            });

            Method setVisibilityMethod = recentsViewClass.getDeclaredMethod("setVisibility", int.class);
            this.xposed.hook(setVisibilityMethod).intercept(chain -> {
                chain.proceed();
                updateMemoryViewVisibility((View) chain.getThisObject());
                log("setVisibility hook executed successfully");
                return null;
            });

            Method onLayoutMethod = recentsViewClass.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class);
            this.xposed.hook(onLayoutMethod).intercept(chain -> {
                chain.proceed();
                attachMemoryView((View) chain.getThisObject());
                log("onLayout hook executed successfully");
                return null;
            });

            log("Recent task memory view hooks installed");
        } catch (Throwable t) {
            logError("Failed to install recent task memory view hooks", t);
        }
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
        textView.setTextColor(Color.argb(0xd9, 0xff, 0xff, 0xff));
        textView.setAlpha(0.8f);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setIncludeFontPadding(false);
        textView.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setClickable(false);
        textView.setFocusable(false);
        textView.setElevation(dp(context, 4));
        return textView;
    }

    private FrameLayout.LayoutParams createLayoutParams(Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.topMargin = dp(context, 26);
        params.leftMargin = dp(context, 16);
        return params;
    }

    private void ensureLayoutParams(TextView memoryView, Context context) {
        ViewGroup.LayoutParams currentParams = memoryView.getLayoutParams();
        if (!(currentParams instanceof FrameLayout.LayoutParams)) {
            memoryView.setLayoutParams(createLayoutParams(context));
            return;
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) currentParams;
        int topMargin = dp(context, 52);
        int leftMargin = dp(context, 16);
        if (params.gravity != (Gravity.TOP | Gravity.START)
                || params.topMargin != topMargin
                || params.leftMargin != leftMargin) {
            params.gravity = Gravity.TOP | Gravity.START;
            params.topMargin = topMargin;
            params.leftMargin = leftMargin;
            params.bottomMargin = 0;
            memoryView.setLayoutParams(params);
        }
    }

    private ViewGroup getDragLayer(View recentsView) {
        try {
            Field containerField = findField(recentsView.getClass(), "mContainer");
            Object container = containerField.get(recentsView);
            if (container == null) {
                return null;
            }
            Method getDragLayerMethod = findMethod(container.getClass(), "getDragLayer");
            Object dragLayer = getDragLayerMethod.invoke(container);
            return dragLayer instanceof ViewGroup ? (ViewGroup) dragLayer : null;
        } catch (Throwable t) {
            if (DEBUG) {logError("Exception happened in getDragLayer: ", t);}
            return null;
        }
    }

    private Field findField(Class<?> startClass, String name) throws NoSuchFieldException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + startClass);
    }

    private Method findMethod(Class<?> startClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + " in " + startClass);
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
                memoryView.setText(getRamUnavailableText(context));
                return;
            }

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            long usedMemory = Math.max(0L, memoryInfo.totalMem - memoryInfo.availMem);
            memoryView.setText(getRamFormatterText(context,
                    formatBytesToGigSuffix(usedMemory),
                    getTotalRamInfo(Math.max(0L, memoryInfo.totalMem))));
        } catch (Throwable t) {
            memoryView.setText(getRamUnavailableText(memoryView.getContext()));
            if (DEBUG) logError("Failed to update memory text", t);
        }
    }

    private String formatBytesToGigSuffix(long bytes) {
        return String.format(Locale.US, "%.1f GB", formatBytes(bytes));
    }

    private String formatBytesToGigSuffix(String gig) {
        return String.format(Locale.US, "%s GB", gig);
    }

    private double formatBytes(long bytes) {
        return bytes / 1073741824.0d;
    }

    private int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    private String getRamFormatterText(Context context, Object... args) {
        String template = cachedRamFormatter;
        if (template == null) {
            template = getModuleString(context, STRING_RAM_FORMATTER, FALLBACK_RAM_FORMATTER);
            cachedRamFormatter = template;
        }
        return String.format(Locale.getDefault(), template, args);
    }

    private String getRamUnavailableText(Context context) {
        String value = cachedRamUnavailable;
        if (value == null) {
            value = getModuleString(context, STRING_RAM_UNAVAILABLE, FALLBACK_RAM_UNAVAILABLE);
            cachedRamUnavailable = value;
        }
        return value;
    }

    private String getModuleString(Context hostContext, String resourceName, String fallback) {
        try {
            Resources resources = getModuleResources(hostContext);
            if (resources == null) {
                return fallback;
            }

            @SuppressLint("DiscouragedApi") int resId = resources.getIdentifier(resourceName, "string", MODULE_PACKAGE);
            if (resId == 0) {
                return fallback;
            }
            return resources.getString(resId);
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to load module string: " + resourceName, t);
            return fallback;
        }
    }

    private Resources getModuleResources(Context hostContext) {
        try {
            Context moduleContext = hostContext.createPackageContext(
                    MODULE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY
            );
            return moduleContext.getResources();
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to create module context for resources", t);
            return null;
        }
    }

    private String getTotalRamInfo(long availableMem) {
        boolean beautifyRamInfo;
        try {
            beautifyRamInfo = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("beautify_ram_info", false);
        } catch (Throwable t) {
            beautifyRamInfo = false;
        }
        if (!beautifyRamInfo) {
            return formatBytesToGigSuffix(availableMem);
        }

        String guessedRam = guessRamSize(availableMem);
        String expansionSize = getMemoryExpansionSize();
        if (expansionSize == null || expansionSize.isEmpty() || "0".equals(expansionSize)) {
            log("RAM expansion disabled, return guessed value");
            return guessedRam;
        }
        return String.format(Locale.getDefault(), "%s + %s", guessedRam, normalizeExpansionSize(expansionSize));
    }

    private String getMemoryExpansionSize() {
        if (!"true".equals(getSystemProperty(PROP_MEMORY_EXPANSION_ENABLED, "false"))) {
            return null;
        }

        String list = getSystemProperty(PROP_MEMORY_EXPANSION_LIST, "");
        if (list == null || list.isEmpty()) {
            return null;
        }

        return getSystemProperty(PROP_MEMORY_EXPANSION_SIZE, "0");
    }

    private String normalizeExpansionSize(String size) {
        String value = size == null ? "" : size.trim();
        if (value.isEmpty()
                || "0".equals(value) || "0G".equalsIgnoreCase(value) || "0GB".equalsIgnoreCase(value)
                || "0.0".equals(value) || "0 G".equalsIgnoreCase(value) || "0 GB".equalsIgnoreCase(value)) {
            return "0.0 GB";
        }
        value = value.replace("GB", "").replace("G", "").trim();
        try {
            return String.format(Locale.getDefault(), "%.1f GB", Double.parseDouble(value));
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to normalize expansion size: " + size, t);
            return size.endsWith("GB") ? size : value + ".0 GB";
        }
    }

    private String guessRamSize(long availableMem) {
        double ramInGig = formatBytes(availableMem);
        double[] ramSizes = {
                1f, 2f, 3f, 4f, 6f, 8f, 10f, 12f,
                14f, 16f, 18f, 20f, 22f, 24f, 26f,
                28f, 30f, 32f, 34f, 36f, 38f, 40f,
                42f, 44f, 46f, 48f, 50f, 52f, 54f,
                56f, 58f, 60f, 62f, 64f, 128f
        };
        for (double ramSize : ramSizes) {
            if (ramSize >= ramInGig) {
                return formatBytesToGigSuffix(String.format(Locale.US, "%.1f", ramSize));
            }
        }
        return formatBytesToGigSuffix(availableMem);
    }

    private String getSystemProperty(String key, String defValue) {
        try {
            Method method = getSystemPropertiesGetWithDefaultMethod();
            if (method != null) {
                Object result = method.invoke(null, key, defValue);
                return result instanceof String ? (String) result : defValue;
            }
        } catch (Throwable t) {
            if (DEBUG) logError("Failed to read system property: " + key, t);
        }
        return defValue;
    }

    private Method getSystemPropertiesGetWithDefaultMethod() {
        Method method = systemPropertiesGetWithDefaultMethod;
        if (method != null) {
            return method;
        }
        synchronized (this) {
            method = systemPropertiesGetWithDefaultMethod;
            if (method != null) {
                return method;
            }
            try {
                Class<?> clz = Class.forName(SYSTEM_PROPERTIES_CLASS);
                method = clz.getMethod(SYSTEM_PROPERTIES_GET_WITH_DEFAULT, String.class, String.class);
                method.setAccessible(true);
                systemPropertiesGetWithDefaultMethod = method;
                return method;
            } catch (Throwable t) {
                if (DEBUG) logError("Failed to resolve SystemProperties.get(String, String)", t);
                return null;
            }
        }
    }
}
