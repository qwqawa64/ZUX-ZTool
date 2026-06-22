package com.qimian233.ztool.hook.modules.setting;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomizeAboutDeviceInfo extends BaseHookModule {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String PREF_KEY_MODEL_ENABLED = "about_device_info_model_enabled";
    private static final String PREF_KEY_CPU_ENABLED = "about_device_info_cpu_enabled";
    private static final String PREF_KEY_RAM_ENABLED = "about_device_info_ram_enabled";
    private static final String PREF_KEY_ROM_ENABLED = "about_device_info_rom_enabled";
    private static final String PREF_KEY_SOFTWARE_ENABLED = "about_device_info_software_enabled";
    private static final String PREF_KEY_HEADER_ENABLED = "about_device_info_header_enabled";
    private static final String PREF_KEY_MODEL = "about_device_info_model";
    private static final String PREF_KEY_CPU = "about_device_info_cpu";
    private static final String PREF_KEY_RAM = "about_device_info_ram";
    private static final String PREF_KEY_ROM = "about_device_info_rom";
    private static final String PREF_KEY_SOFTWARE = "about_device_info_software";
    private static final String DEVICE_IMAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/Download/ZTool/device_info.jpg";
    private static final String HEADER_VIEW_CLASS = "com.lenovo.settings.deviceinfo.aboutphone.PadTopImgPreference";
    private static final String CPU_CLASS = "com.lenovo.settings.deviceinfo.controller.CpuInfoDisplayPreferenceController";
    private static final String RAM_CLASS = "com.lenovo.settings.deviceinfo.controller.RamSizePreferenceController";
    private static final String ROM_CLASS = "com.lenovo.settings.deviceinfo.controller.RomSizePreferenceController";
    private static final String MODEL_CLASS = "com.android.settings.deviceinfo.hardwareinfo.DeviceModelPreferenceController";
    private static final String SOFTWARE_CLASS = "com.android.settings.deviceinfo.BuildNumberPreferenceController";

    private final PreferenceHelper preferences = PreferenceHelper.getInstance();

    @Override
    public String getModuleName() {
        return "about_device_info";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSummaryIfEnabled(lpparam, CPU_CLASS, PREF_KEY_CPU_ENABLED, PREF_KEY_CPU, "CPU");
        hookSummaryIfEnabled(lpparam, RAM_CLASS, PREF_KEY_RAM_ENABLED, PREF_KEY_RAM, "RAM");
        hookSummaryIfEnabled(lpparam, ROM_CLASS, PREF_KEY_ROM_ENABLED, PREF_KEY_ROM, "ROM");
        hookSummaryIfEnabled(lpparam, MODEL_CLASS, PREF_KEY_MODEL_ENABLED, PREF_KEY_MODEL, "model");
        hookSummaryIfEnabled(lpparam, SOFTWARE_CLASS, PREF_KEY_SOFTWARE_ENABLED, PREF_KEY_SOFTWARE, "software");
        hookHeaderImageAndText(lpparam);
    }

    private void hookSummaryIfEnabled(
            XC_LoadPackage.LoadPackageParam lpparam,
            String targetClass,
            String enabledKey,
            String valueKey,
            String fieldName
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                    targetClass,
                    lpparam.classLoader,
                    "getSummary",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!preferences.getBoolean(enabledKey, false)) {
                                return;
                            }
                            String value = preferences.getString(valueKey, "");
                            if (value != null && !value.trim().isEmpty()) {
                                param.setResult(value);
                                if (DEBUG) {
                                    log(fieldName + " summary -> " + value);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook " + fieldName + " summary", t);
        }
    }

    private void hookHeaderImageAndText(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    HEADER_VIEW_CLASS,
                    lpparam.classLoader,
                    "setImage",
                    ImageView.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!preferences.getBoolean(PREF_KEY_HEADER_ENABLED, false)) {
                                return;
                            }
                            Bitmap bitmap = decodeHeaderBitmap();
                            if (bitmap == null) {
                                log("Header image file missing: " + DEVICE_IMAGE_PATH);
                                return;
                            }
                            ((ImageView) param.args[0]).setImageBitmap(bitmap);
                            log("Header image loaded from " + DEVICE_IMAGE_PATH);
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook header image", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    HEADER_VIEW_CLASS,
                    lpparam.classLoader,
                    "updateText",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!preferences.getBoolean(PREF_KEY_MODEL_ENABLED, false)) {
                                return;
                            }
                            String model = preferences.getString(PREF_KEY_MODEL, "");
                            if (model != null && !model.trim().isEmpty()) {
                                Object view = XposedHelpers.getObjectField(param.thisObject, "tvPad");
                                if (view instanceof TextView) {
                                    ((TextView) view).setText(model);
                                    log("Header model text -> " + model);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook header text", t);
        }
    }

    private Bitmap decodeHeaderBitmap() {
        File imageFile = new File(DEVICE_IMAGE_PATH);
        if (!imageFile.exists() || !imageFile.isFile()) {
            return null;
        }
        try (FileInputStream inputStream = new FileInputStream(imageFile)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            logError("Failed to decode header image", e);
            return null;
        }
    }
}
