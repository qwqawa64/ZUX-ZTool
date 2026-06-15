package com.qimian233.ztool.hook.modules.setting;

import android.content.ContentResolver;
import android.provider.Settings;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hides the red OTA update hint in Settings while keeping the OTA entry usable.
 */
public class HideOtaUpdateHint extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String OTA_NEW_VERSION_FOUND = "lenovo_ota_new_version_found";

    @Override
    public String getModuleName() {
        return "hook_test";
    }
    //hide_ota_update_hint

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.Secure.class,
                    "getInt",
                    ContentResolver.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (OTA_NEW_VERSION_FOUND.equals(param.args[1])) {
                                param.setResult(0);
                            }
                        }
                    }
            );
            XposedHelpers.findAndHookMethod(
                    Settings.Secure.class,
                    "getInt",
                    ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (OTA_NEW_VERSION_FOUND.equals(param.args[1])) {
                                param.setResult(0);
                            }
                        }
                    }
            );
            log("Hooked Settings OTA new-version flag reads");
        } catch (Throwable t) {
            logError("Failed to hook Settings OTA new-version flag reads", t);
        }
    }
}
