package com.qimian233.ztool.hook.modules.launcher;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CleanGlobalSearch extends BaseHookModule {

    private boolean  NO_SEARCH_BOX_RECOMMEND= false;
    private boolean NO_HOT_WORD_VIEW = false;

    @Override
    public String getModuleName() {
        return "clean_global_search";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.launcher"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        getPreferenceSettings();
        String[] methodNames = {"K0", "T0"};
        // Remove hot word view, key name: remove_hot_word_view
        if (this.NO_HOT_WORD_VIEW) {
            for (String methodName : methodNames) {
                try {
                    XposedHelpers.findAndHookMethod("com.zui.launcher.GlobalSearchView", lpparam.classLoader, methodName, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
                    log("Successfully hooked hotword inflation method with method name " + methodName + "!");
                    break;
                } catch (NoSuchMethodError e) {
                    log("Unable to find global search hotword inflation method! Try alternate method name!");
                }
            }
            try {
                XposedHelpers.findAndHookMethod("com.zui.launcher.GlobalSearchView", lpparam.classLoader, "E0", "java.util.List", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return null;
                    }
                });
            } catch (NoSuchMethodError ignored) {
                log("Unable to find com.zui.launcher.GlobalSearchView#E0.");
            }
        }
        if (this.NO_SEARCH_BOX_RECOMMEND) {
            try {
                // Remove hot word recommendations in search EditText, key name: remove_search_recommend
                XposedHelpers.findAndHookMethod("com.zui.launcher.GlobalSearchView", lpparam.classLoader, "setHotWordHint",
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                return null;
                            }
                        });
            } catch (NoSuchMethodError ignored) {
                log("Unable to find com.zui.launcher.GlobalSearchView#setHotWordHint.");
            }
        }
    }

    private void getPreferenceSettings() {
        PreferenceHelper prefs = PreferenceHelper.getInstance();
        this.NO_HOT_WORD_VIEW = prefs.getBoolean("remove_hot_word_view", false);
        this.NO_SEARCH_BOX_RECOMMEND = prefs.getBoolean("remove_search_recommend", false);
    }
}
