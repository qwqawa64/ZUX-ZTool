package com.qimian233.ztool.hook.modules.ota;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hides the red OTA update hint in Settings while keeping the OTA entry usable.
 */
public class HideOtaUpdateHint extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String CONTROLLER_CLASS =
            "com.lenovo.settings.deviceinfo.controller.DeviceUpdatePreferenceController";
    private static final String OTA_PACKAGE = "com.lenovo.ota";

    @Override
    public String getModuleName() {
        return "hide_ota_update_hint";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public boolean isEnabled() {
        return com.qimian233.ztool.config.ModuleConfig.isModuleEnabled("disable_OtaCheck");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    CONTROLLER_CLASS,
                    lpparam.classLoader,
                    "updateOtaHintState",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            applyNoUpdateState(param.thisObject);
                        }
                    }
            );
            log("Hooked Settings OTA hint state");
        } catch (Throwable t) {
            logError("Failed to hook Settings OTA hint state", t);
        }
    }

    private void applyNoUpdateState(Object controller) {
        try {
            Object preference = XposedHelpers.getObjectField(controller, "mPreference");
            if (preference == null) {
                return;
            }

            Context context = (Context) XposedHelpers.getObjectField(controller, "mContext");
            ClassLoader classLoader = controller.getClass().getClassLoader();

            XposedHelpers.callMethod(
                    preference,
                    "setTitle",
                    XposedHelpers.callStaticMethod(
                            classLoader.loadClass("com.lenovo.common.utils.AppInfoUtils"),
                            "getAppName",
                            context,
                            OTA_PACKAGE
                    )
            );

            Class<?> settingsLayoutClass = classLoader.loadClass("com.android.settings.R$layout");
            XposedHelpers.callMethod(
                    preference,
                    "setLayoutResource",
                    XposedHelpers.getStaticIntField(settingsLayoutClass, "bluetooth_preference_long_summary")
            );
            XposedHelpers.callMethod(
                    preference,
                    "setWidgetLayoutResource",
                    XposedHelpers.getStaticIntField(settingsLayoutClass, "preference_widget_forward")
            );

            Class<?> commonStringClass = classLoader.loadClass("com.lenovo.common.R$string");
            String versionTitle = context.getResources().getString(
                    XposedHelpers.getStaticIntField(commonStringClass, "version_num")
            );
            String versionNumber = (String) XposedHelpers.callStaticMethod(
                    classLoader.loadClass("com.lenovo.common.utils.LenovoUtils"),
                    "getVersionNum",
                    context,
                    false
            );
            XposedHelpers.callMethod(preference, "setSummary", versionTitle + " " + versionNumber);
        } catch (Throwable t) {
            logError("Failed to apply no-update OTA hint state", t);
        }
    }
}
