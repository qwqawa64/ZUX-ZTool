package com.qimian233.ztool.hook.modules.setting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AppInfoHeaderDetailsHook extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String CONTROLLER_CLASS =
            "com.android.settings.applications.appinfo.AppHeaderViewPreferenceController";
    private static final String APP_ENTRY_CLASS =
            "com.android.settingslib.applications.ApplicationsState$AppEntry";

    @Override
    public String getModuleName() {
        return "hook_test";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        Class<?> appEntryClass = XposedHelpers.findClassIfExists(APP_ENTRY_CLASS, lpparam.classLoader);
        if (appEntryClass == null) {
            log("AppEntry class not found, skip app info header hook.");
            return;
        }

        XposedHelpers.findAndHookMethod(
                CONTROLLER_CLASS,
                lpparam.classLoader,
                "setAppLabelAndIcon",
                PackageInfo.class,
                appEntryClass,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            PackageInfo pkgInfo = (PackageInfo) param.args[0];
                            if (pkgInfo == null || pkgInfo.applicationInfo == null) {
                                return;
                            }

                            Context context = (Context) XposedHelpers.getObjectField(
                                    param.thisObject, "mContext");
                            Object headerPreference = XposedHelpers.getObjectField(
                                    param.thisObject, "mHeader");
                            TextView summaryView = findSummaryView(context, headerPreference);
                            if (summaryView == null) {
                                log("entity_header_summary not found.");
                                return;
                            }

                            String appInfo = buildAppInfo(summaryView.getContext(), pkgInfo);
                            if (TextUtils.isEmpty(appInfo)) {
                                return;
                            }

                            CharSequence originalSummary = summaryView.getText();
                            String displayText = mergeSummary(originalSummary, appInfo);
                            summaryView.setSingleLine(false);
                            summaryView.setMaxLines(Integer.MAX_VALUE);
                            summaryView.setText(displayText);
                            summaryView.setOnLongClickListener(v -> {
                                copyToClipboard(v.getContext(), displayText);
                                return true;
                            });
                        } catch (Throwable t) {
                            logError("Failed to update app info header summary", t);
                        }
                    }
                });
        log("Hooked AppHeaderViewPreferenceController#setAppLabelAndIcon.");
    }

    private TextView findSummaryView(Context context, Object headerPreference) {
        if (context == null || headerPreference == null) {
            return null;
        }

        int summaryId = context.getResources().getIdentifier(
                "entity_header_summary", "id", TARGET_PACKAGE);
        if (summaryId == 0) {
            return null;
        }

        View headerView = (View) XposedHelpers.callMethod(
                headerPreference, "findViewById", summaryId);
        if (headerView instanceof TextView) {
            return (TextView) headerView;
        }
        return null;
    }

    private String buildAppInfo(Context context, PackageInfo pkgInfo) {
        ApplicationInfo appInfo = pkgInfo.applicationInfo;
        StringBuilder builder = new StringBuilder();
        builder.append("包名: ").append(pkgInfo.packageName);
        builder.append('\n').append("SDK版本: min ")
                .append(appInfo.minSdkVersion)
                .append(" / target ")
                .append(appInfo.targetSdkVersion);
        builder.append('\n').append("首次安装时间: ").append(formatTime(pkgInfo.firstInstallTime));
        builder.append('\n').append("最后更新时间: ").append(formatTime(pkgInfo.lastUpdateTime));
        builder.append('\n').append("安装来源: ").append(getInstallSource(context, pkgInfo.packageName));
        return builder.toString();
    }

    private String mergeSummary(CharSequence originalSummary, String appInfo) {
        if (TextUtils.isEmpty(originalSummary)) {
            return appInfo;
        }
        String original = originalSummary.toString();
        if (original.contains("包名: ") && original.contains("安装来源: ")) {
            return appInfo;
        }
        return original + "\n" + appInfo;
    }

    private String formatTime(long timeMillis) {
        if (timeMillis <= 0L) {
            return "未知";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private String getInstallSource(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            InstallSourceInfo sourceInfo = pm.getInstallSourceInfo(packageName);
            String source = firstNonEmpty(
                    sourceInfo.getInstallingPackageName(),
                    sourceInfo.getInitiatingPackageName(),
                    sourceInfo.getOriginatingPackageName());
            if (TextUtils.isEmpty(source)) {
                return "未知";
            }

            CharSequence label = getApplicationLabel(pm, source);
            if (!TextUtils.isEmpty(label)) {
                return label + " (" + source + ")";
            }
            return source;
        } catch (Throwable t) {
            return "未知";
        }
    }

    private CharSequence getApplicationLabel(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String firstNonEmpty(String first, String second, String third) {
        if (!TextUtils.isEmpty(first)) {
            return first;
        }
        if (!TextUtils.isEmpty(second)) {
            return second;
        }
        return TextUtils.isEmpty(third) ? null : third;
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("app_info", text));
            Toast.makeText(context, "已复制应用信息", Toast.LENGTH_SHORT).show();
        }
    }
}
