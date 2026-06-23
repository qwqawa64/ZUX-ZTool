package com.qimian233.ztool.hook.modules.setting;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.qimian233.ztool.MainActivity;
import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ZToolSettingsEntryHook extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS = "com.android.settings.homepage.TopLevelSettings";
    private static final String ENTRY_KEY = "ztool_settings_entry";
    private static final String CATEGORY_KEY = "ztool_settings_category";
    private static final String APP_PACKAGE = "com.qimian233.ztool";
    private static final String ENTRY_TITLE = "ZTool";

    @Override
    public String getModuleName() {
        return "ztool_settings_entry";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Installing hook.");
            XposedHelpers.findAndHookMethod(
                    TARGET_CLASS,
                    lpparam.classLoader,
                    "onCreatePreferences",
                    android.os.Bundle.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object screen = XposedHelpers.callMethod(param.thisObject, "getPreferenceScreen");
                                if (screen == null) {
                                    return;
                                }
                                Context context = (Context) XposedHelpers.callMethod(screen, "getContext");
                                if (context == null) {
                                    return;
                                }

                                if (XposedHelpers.callMethod(screen, "findPreference", ENTRY_KEY) != null) {
                                    return;
                                }

                                Class<?> preferenceCategoryClass = XposedHelpers.findClass(
                                        "androidx.preference.PreferenceCategory",
                                        lpparam.classLoader);
                                Class<?> preferenceClass = XposedHelpers.findClass(
                                        "androidx.preference.Preference",
                                        lpparam.classLoader);

                                Object category = XposedHelpers.newInstance(preferenceCategoryClass, context);
                                XposedHelpers.callMethod(category, "setKey", CATEGORY_KEY);
                                //XposedHelpers.callMethod(category, "setTitle", ENTRY_TITLE);
                                XposedHelpers.callMethod(category, "setOrder", -90);

                                Object entry = XposedHelpers.newInstance(preferenceClass, context);
                                XposedHelpers.callMethod(entry, "setKey", ENTRY_KEY);
                                XposedHelpers.callMethod(entry, "setTitle", ENTRY_TITLE);
                                XposedHelpers.callMethod(entry, "setOrder", Integer.MIN_VALUE + 1);
                                Intent intent = new Intent();
                                intent.setComponent(new ComponentName(
                                        APP_PACKAGE,
                                        MainActivity.class.getName()
                                ));
                                XposedHelpers.callMethod(entry, "setIntent", intent);
                                XposedHelpers.callMethod(entry, "setIcon", context.getPackageManager().getApplicationIcon(APP_PACKAGE));

                                XposedHelpers.callMethod(screen, "addPreference", category);
                                XposedHelpers.callMethod(category, "addPreference", entry);
                                log("Injected ZTool entry into TopLevelSettings");
                            } catch (Throwable t) {
                                logError("Failed to inject ZTool settings entry", t);
                            }
                        }
                    }
            );
            log("Successfully installed hook.");
        } catch (Throwable t) {
            logError("Failed to hook TopLevelSettings.onCreatePreferences", t);
        }
    }
}
