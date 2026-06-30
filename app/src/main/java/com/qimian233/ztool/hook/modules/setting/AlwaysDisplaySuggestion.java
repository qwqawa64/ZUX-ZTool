package com.qimian233.ztool.hook.modules.setting;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class AlwaysDisplaySuggestion extends BaseHookModule {

    public AlwaysDisplaySuggestion() {}

    @Override
    public String getModuleName() {
        return "AlwaysDisplaySuggestion";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.settings"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (DEBUG) log("Load AlwaysDisplaySuggestion!");
        try {
            // Suggestion for screen timeout
            Method m1 = classLoader
                    .loadClass("com.lenovo.settings.suggestion.ScreenTimeoutSuggestionActivity")
                    .getDeclaredMethod("isSuggestionComplete", Context.class);
            this.xposed.hook(m1).intercept(chain -> false);

            // Suggestion for join user experience project
            Method m2 = classLoader
                    .loadClass("com.lenovo.settings.suggestion.UserExperienceSuggestionActivity")
                    .getDeclaredMethod("isSuggestionComplete", Context.class);
            this.xposed.hook(m2).intercept(chain -> false);

            log("Hook executed successfully!");
        } catch (Exception e) {
            logError("Error in AlwaysDisplaySuggestion: ", e);
        }
    }
}
