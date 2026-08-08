package com.qimian233.ztool.hook.modules.setting;

import android.annotation.SuppressLint;
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

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

@SuppressLint({"PrivateApi", "DiscouragedApi"})
public class AppInfoHeaderDetailsHook extends AppHookModule {
    private static final String TARGET_PACKAGE = ScopeKeys.SETTINGS.packageName;
    private static final String CONTROLLER_CLASS =
            "com.android.settings.applications.appinfo.AppHeaderViewPreferenceController";
    private static final String APP_ENTRY_CLASS =
            "com.android.settingslib.applications.ApplicationsState$AppEntry";

    private String SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
    private final String[] DISPLAY_STRINGS_CN = {"包名", "首次安装", "最后更新", "安装自", "已复制到剪贴板", "未知"};
    private final String[] DISPLAY_STRINGS_ALTERNATIVE = {"Package Name", "First Installed", "Last Updated", "Source", "Copied to clipboard", "Unknown"};

    public AppInfoHeaderDetailsHook() {}

    private String getDisplayString(int stringIndex) {
        if (stringIndex <= 3) return this.SYSTEM_LANGUAGE.equals("zh") ? this.DISPLAY_STRINGS_CN[stringIndex] + ": " : this.DISPLAY_STRINGS_ALTERNATIVE[stringIndex] + ": ";
        return this.SYSTEM_LANGUAGE.equals("zh") ? this.DISPLAY_STRINGS_CN[stringIndex] : this.DISPLAY_STRINGS_ALTERNATIVE[stringIndex];
    }

    @Override
    public String getModuleName() {
        return "app_details";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        Class<?> appEntryClass = null;
        try {
            appEntryClass = classLoader.loadClass(APP_ENTRY_CLASS);
        } catch (ClassNotFoundException ignored) {}
        if (appEntryClass == null) {
            logger.warn("AppEntry class not found, skip app info header hook.");
            return;
        }

        Method m = classLoader
                .loadClass(CONTROLLER_CLASS)
                .getDeclaredMethod("setAppLabelAndIcon", PackageInfo.class, appEntryClass);
        hookWithId(m, "hook_76", chain -> {
            Object result = chain.proceed();
            try {
                PackageInfo pkgInfo = (PackageInfo) chain.getArg(0);
                if (pkgInfo == null || pkgInfo.applicationInfo == null) {
                    return result;
                }

                Field mContextField = findField(chain.getThisObject().getClass(), "mContext");
                mContextField.setAccessible(true);
                Context context = (Context) mContextField.get(chain.getThisObject());

                Field mHeaderField = findField(chain.getThisObject().getClass(), "mHeader");
                mHeaderField.setAccessible(true);
                Object headerPreference = mHeaderField.get(chain.getThisObject());

                TextView summaryView = findSummaryView(context, headerPreference);
                if (summaryView == null) {
                    logger.warn("entity_header_summary not found.");
                    return result;
                }

                String appInfo = buildAppInfo(summaryView.getContext(), pkgInfo);
                if (TextUtils.isEmpty(appInfo)) {
                    return result;
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
                logger.error("Failed to update app info header summary", t);
            }
            return result;
        });
        logger.info("Hooked AppHeaderViewPreferenceController#setAppLabelAndIcon.");
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

        try {
            Method findViewById = headerPreference.getClass().getDeclaredMethod("findViewById", int.class);
            View headerView = (View) findViewById.invoke(headerPreference, summaryId);
            if (headerView instanceof TextView) {
                return (TextView) headerView;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String buildAppInfo(Context context, PackageInfo pkgInfo) {
        this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
        ApplicationInfo appInfo = pkgInfo.applicationInfo;
        return this.getDisplayString(0) + pkgInfo.packageName +
                '\n' + "minSDK " +
                Objects.requireNonNull(appInfo).minSdkVersion +
                " / target " +
                appInfo.targetSdkVersion +
                '\n' + this.getDisplayString(1) + formatTime(pkgInfo.firstInstallTime) +
                '\n' + this.getDisplayString(2) + formatTime(pkgInfo.lastUpdateTime) +
                '\n' + this.getDisplayString(3) + getInstallSource(context, pkgInfo.packageName);
    }

    private String mergeSummary(CharSequence originalSummary, String appInfo) {
        this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
        if (TextUtils.isEmpty(originalSummary)) {
            return appInfo;
        }
        String original = originalSummary.toString();
        if (original.contains(this.getDisplayString(0)) && original.contains(this.getDisplayString(3))) {
            return appInfo;
        }
        return original + "\n" + appInfo;
    }

    private String formatTime(long timeMillis) {
        if (timeMillis <= 0L) {
            this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
            return this.getDisplayString(5);
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    private String getInstallSource(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            String source;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                InstallSourceInfo sourceInfo = pm.getInstallSourceInfo(packageName);
                source = firstNonEmpty(
                        sourceInfo.getInstallingPackageName(),
                        sourceInfo.getInitiatingPackageName(),
                        sourceInfo.getOriginatingPackageName());
            } else {
                source = pm.getInstallerPackageName(packageName);
            }
            if (TextUtils.isEmpty(source)) {
                this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
                return this.getDisplayString(5);
            }

            CharSequence label = getApplicationLabel(pm, source);
            if (!TextUtils.isEmpty(label)) {
                return label + " (" + source + ")";
            }
            return source;
        } catch (Throwable t) {
            this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
            return this.getDisplayString(5);
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
            this.SYSTEM_LANGUAGE = Locale.getDefault().getLanguage();
            Toast.makeText(context, this.getDisplayString(4), Toast.LENGTH_SHORT).show();
        }
    }
}
