package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 系统设置Hook模块
 * 修改系统设置应用的行为
 */
public class yishijiecompletion extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "remove_blacklist";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.settings", "com.lenovo.settings"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.settings".equals(packageName)) {
            hookSettingsAppManager(lpparam);
        } else if ("com.lenovo.settings".equals(packageName)) {
            hookLenovoSettings();
        }
    }

    private void hookSettingsAppManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.settings.onevision.horizontal.SettingsEmbeddingAppManager",
                    lpparam.classLoader,
                    "getZuiLandScapeShouldBeHideAppList",
                    XC_MethodReplacement.returnConstant(new String[0])
            );
            log("Successfully hooked SettingsEmbeddingAppManager");
        } catch (Throwable t) {
            logError("Failed to hook SettingsEmbeddingAppManager", t);
        }
    }

    private void hookLenovoSettings() {
        try {
            // 这里可以添加更多Lenovo设置的Hook
            // 例如：XposedHelpers.findAndHookMethod(...)
            log("Lenovo settings hook placeholder");
        } catch (Throwable t) {
            logError("Failed to hook Lenovo settings", t);
        }
    }
}
