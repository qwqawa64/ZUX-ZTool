package com.qimian233.ztool.hook.modules.setting;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.qimian233.ztool.MainActivity;
import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ZToolSettingsEntryHook extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS = "com.android.settings.homepage.TopLevelSettings";
    private static final String ENTRY_KEY = "ztool_settings_entry";
    private static final String CATEGORY_KEY = "ztool_settings_category";
    private static final String APP_PACKAGE = "com.qimian233.ztool";
    private static final String ENTRY_TITLE = "ZTool";

    public ZToolSettingsEntryHook() {}

    @Override
    public String getModuleName() {
        return "ztool_settings_entry";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        try {
            log("Installing hook.");
            Method m = classLoader
                    .loadClass(TARGET_CLASS)
                    .getDeclaredMethod("onCreatePreferences", android.os.Bundle.class, String.class);
            this.xposed.hook(m).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Method getPrefScreen = chain.getThisObject().getClass()
                            .getDeclaredMethod("getPreferenceScreen");
                    Object screen = getPrefScreen.invoke(chain.getThisObject());
                    if (screen == null) {
                        return result;
                    }

                    Method getContext = screen.getClass().getDeclaredMethod("getContext");
                    Context context = (Context) getContext.invoke(screen);
                    if (context == null) {
                        return result;
                    }

                    Method findPreference = screen.getClass()
                            .getDeclaredMethod("findPreference", CharSequence.class);
                    if (findPreference.invoke(screen, ENTRY_KEY) != null) {
                        return result;
                    }

                    Class<?> preferenceCategoryClass = classLoader
                            .loadClass("androidx.preference.PreferenceCategory");
                    Class<?> preferenceClass = classLoader
                            .loadClass("androidx.preference.Preference");

                    Constructor<?> categoryCtor = preferenceCategoryClass
                            .getDeclaredConstructor(Context.class);
                    Object category = categoryCtor.newInstance(context);
                    Method setKey = preferenceCategoryClass.getDeclaredMethod("setKey", String.class);
                    setKey.invoke(category, CATEGORY_KEY);
                    Method setOrder = preferenceCategoryClass.getDeclaredMethod("setOrder", int.class);
                    setOrder.invoke(category, -90);

                    Constructor<?> prefCtor = preferenceClass.getDeclaredConstructor(Context.class);
                    Object entry = prefCtor.newInstance(context);
                    setKey.invoke(entry, ENTRY_KEY);
                    Method setTitle = preferenceClass.getDeclaredMethod("setTitle", CharSequence.class);
                    setTitle.invoke(entry, ENTRY_TITLE);
                    setOrder.invoke(entry, Integer.MIN_VALUE + 1);

                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            APP_PACKAGE,
                            MainActivity.class.getName()
                    ));
                    Method setIntent = preferenceClass.getDeclaredMethod("setIntent", Intent.class);
                    setIntent.invoke(entry, intent);
                    Method setIcon = preferenceClass.getDeclaredMethod("setIcon", android.graphics.drawable.Drawable.class);
                    setIcon.invoke(entry, context.getPackageManager().getApplicationIcon(APP_PACKAGE));

                    Method addPreference = screen.getClass()
                            .getDeclaredMethod("addPreference",
                                    classLoader.loadClass("androidx.preference.Preference"));
                    addPreference.invoke(screen, category);
                    addPreference.invoke(category, entry);

                    log("Injected ZTool entry into TopLevelSettings");
                } catch (Throwable t) {
                    logError("Failed to inject ZTool settings entry", t);
                }
                return result;
            });
            log("Successfully installed hook.");
        } catch (Throwable t) {
            logError("Failed to hook TopLevelSettings.onCreatePreferences", t);
        }
    }
}
