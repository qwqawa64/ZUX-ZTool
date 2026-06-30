package com.qimian233.ztool.hook.modules.setting;

import android.content.ContentResolver;
import android.provider.Settings;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * Hides the red OTA update hint in Settings while keeping the OTA entry usable.
 */
public class HideOtaUpdateHint extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String OTA_NEW_VERSION_FOUND = "lenovo_ota_new_version_found";

    public HideOtaUpdateHint() {}

    @Override
    public String getModuleName() {
        return "hide_ota_update_hint";
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
            // getInt(ContentResolver, String, int)
            Method getInt3 = Settings.Secure.class.getDeclaredMethod(
                    "getInt", ContentResolver.class, String.class, int.class);
            this.xposed.hook(getInt3).intercept(chain -> {
                if (OTA_NEW_VERSION_FOUND.equals(chain.getArg(1))) {
                    return 0;
                }
                return chain.proceed();
            });

            // getInt(ContentResolver, String)
            Method getInt2 = Settings.Secure.class.getDeclaredMethod(
                    "getInt", ContentResolver.class, String.class);
            this.xposed.hook(getInt2).intercept(chain -> {
                if (OTA_NEW_VERSION_FOUND.equals(chain.getArg(1))) {
                    return 0;
                }
                return chain.proceed();
            });

            log("Hooked Settings OTA new-version flag reads");
        } catch (Throwable t) {
            logError("Failed to hook Settings OTA new-version flag reads", t);
        }
    }
}
