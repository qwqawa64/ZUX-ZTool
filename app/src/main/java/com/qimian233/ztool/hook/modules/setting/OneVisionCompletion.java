package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 系统设置Hook模块
 * 修改系统设置应用的行为
 */
public class OneVisionCompletion extends BaseHookModule {

    public OneVisionCompletion() {}

    @Override
    public String getModuleName() {
        return "remove_blacklist";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.settings", "com.lenovo.settings"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.settings".equals(packageName)) {
            hookSettingsAppManager(classLoader);
        } else if ("com.lenovo.settings".equals(packageName)) {
            hookLenovoSettings();
        }
    }

    private void hookSettingsAppManager(ClassLoader classLoader) {
        try {
            Method m = classLoader
                    .loadClass("com.lenovo.settings.onevision.horizontal.SettingsEmbeddingAppManager")
                    .getDeclaredMethod("getZuiLandScapeShouldBeHideAppList");
            hookWithId(m, "hook_44", chain -> new String[0]);
            logger.info("Successfully hooked SettingsEmbeddingAppManager");
        } catch (Throwable t) {
            logger.error("Failed to hook SettingsEmbeddingAppManager", t);
        }
    }

    private void hookLenovoSettings() {
        try {
            // 这里可以添加更多Lenovo设置的Hook
            logger.debug("Lenovo settings hook placeholder");
        } catch (Throwable t) {
            logger.error("Failed to hook Lenovo settings", t);
        }
    }
}
