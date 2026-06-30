package com.qimian233.ztool.hook.modules.launcher;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class CleanGlobalSearch extends BaseHookModule {

    private boolean NO_SEARCH_BOX_RECOMMEND = false;
    private boolean NO_HOT_WORD_VIEW = false;

    public CleanGlobalSearch() {}

    @Override
    public String getModuleName() {
        return "clean_global_search";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.launcher"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        getPreferenceSettings();
        String[] methodNames = {"K0", "T0"};
        // Remove hot word view, key name: remove_hot_word_view
        if (this.NO_HOT_WORD_VIEW) {
            for (String methodName : methodNames) {
                try {
                    Class<?> globalSearchViewClass = classLoader.loadClass("com.zui.launcher.GlobalSearchView");
                    Method method = globalSearchViewClass.getDeclaredMethod(methodName);
                    this.xposed.hook(method).intercept(chain -> null);
                    log("Successfully hooked hotword inflation method with method name " + methodName + "!");
                    break;
                } catch (NoSuchMethodError | Exception e) {
                    log("Unable to find global search hotword inflation method! Try alternate method name!");
                }
            }
            try {
                Class<?> globalSearchViewClass = classLoader.loadClass("com.zui.launcher.GlobalSearchView");
                Class<?> listClass = classLoader.loadClass("java.util.List");
                Method e0Method = globalSearchViewClass.getDeclaredMethod("E0", listClass);
                this.xposed.hook(e0Method).intercept(chain -> null);
            } catch (NoSuchMethodError | Exception ignored) {
                log("Unable to find com.zui.launcher.GlobalSearchView#E0.");
            }
        }
        if (this.NO_SEARCH_BOX_RECOMMEND) {
            try {
                Class<?> globalSearchViewClass = classLoader.loadClass("com.zui.launcher.GlobalSearchView");
                Method setHotWordHintMethod = globalSearchViewClass.getDeclaredMethod("setHotWordHint");
                this.xposed.hook(setHotWordHintMethod).intercept(chain -> null);
            } catch (NoSuchMethodError | Exception ignored) {
                log("Unable to find com.zui.launcher.GlobalSearchView#setHotWordHint.");
            }
        }
    }

    private void getPreferenceSettings() {
        try {
            this.NO_HOT_WORD_VIEW = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("remove_hot_word_view", false);
        } catch (Throwable t) {
            this.NO_HOT_WORD_VIEW = false;
        }
        try {
            this.NO_SEARCH_BOX_RECOMMEND = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("remove_search_recommend", false);
        } catch (Throwable t) {
            this.NO_SEARCH_BOX_RECOMMEND = false;
        }
    }
}
