package com.qimian233.ztool.hook.modules.setting;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

public class AlwaysDisplaySuggestion extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "AlwaysDisplaySuggestion";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.settings"};
    }

    @Override
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) {
        if (DEBUG) log("Load AlwaysDisplaySuggestion!");
        try {
            // Suggestion for screen timeout
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.settings.suggestion.ScreenTimeoutSuggestionActivity",
                    lpparam.classLoader,
                    "isSuggestionComplete",
                    Context.class,
                    XC_MethodReplacement.returnConstant(false)
            );
            // Suggestion for join user experience project
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.settings.suggestion.UserExperienceSuggestionActivity",
                    lpparam.classLoader,
                    "isSuggestionComplete",
                    Context.class,
                    XC_MethodReplacement.returnConstant(false)
            );
            log("Hook executed successfully!");
        } catch (Exception e) {
            logError("Error in AlwaysDisplaySuggestion: ", e);
        }
    }
}
