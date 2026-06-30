package com.qimian233.ztool.hook.modules.setting;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.TextView;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
    private static final String DEVICE_IMAGE_PATH = "/Download/ZTool/device_info.jpg";
    private static final String HEADER_VIEW_CLASS = "com.lenovo.settings.deviceinfo.aboutphone.PadTopImgPreference";
    private static final String CPU_CLASS = "com.lenovo.settings.deviceinfo.controller.CpuInfoDisplayPreferenceController";
    private static final String RAM_CLASS = "com.lenovo.settings.deviceinfo.controller.RamSizePreferenceController";
    private static final String ROM_CLASS = "com.lenovo.settings.deviceinfo.controller.RomSizePreferenceController";
    private static final String MODEL_CLASS = "com.android.settings.deviceinfo.hardwareinfo.DeviceModelPreferenceController";
    private static final String SOFTWARE_CLASS = "com.android.settings.deviceinfo.BuildNumberPreferenceController";

    public CustomizeAboutDeviceInfo() {}

    @Override
    public String getModuleName() {
        return "about_device_info";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookSummaryIfEnabled(classLoader, CPU_CLASS, PREF_KEY_CPU_ENABLED, PREF_KEY_CPU, "CPU");
        hookSummaryIfEnabled(classLoader, RAM_CLASS, PREF_KEY_RAM_ENABLED, PREF_KEY_RAM, "RAM");
        hookSummaryIfEnabled(classLoader, ROM_CLASS, PREF_KEY_ROM_ENABLED, PREF_KEY_ROM, "ROM");
        hookSummaryIfEnabled(classLoader, MODEL_CLASS, PREF_KEY_MODEL_ENABLED, PREF_KEY_MODEL, "model");
        hookSummaryIfEnabled(classLoader, SOFTWARE_CLASS, PREF_KEY_SOFTWARE_ENABLED, PREF_KEY_SOFTWARE, "software");
        hookHeaderImageAndText(classLoader);
    }

    private void hookSummaryIfEnabled(
            ClassLoader classLoader,
            String targetClass,
            String enabledKey,
            String valueKey,
            String fieldName
    ) {
        try {
            Method m = classLoader.loadClass(targetClass).getDeclaredMethod("getSummary");
            this.xposed.hook(m).intercept(chain -> {
                boolean prefEnabled;
                try {
                    prefEnabled = this.xposed.getRemotePreferences("xposed_module_config").getBoolean(enabledKey, false);
                } catch (Throwable ignored) {
                    prefEnabled = false;
                }
                if (!prefEnabled) {
                    return chain.proceed();
                }
                String value;
                try {
                    value = this.xposed.getRemotePreferences("xposed_module_config").getString(valueKey, "");
                } catch (Throwable ignored) {
                    value = "";
                }
                if (value != null && !value.trim().isEmpty()) {
                    if (DEBUG) {
                        log(fieldName + " summary -> " + value);
                    }
                    return value;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            logError("Failed to hook " + fieldName + " summary", t);
        }
    }

    private void hookHeaderImageAndText(ClassLoader classLoader) {
        try {
            Method setImageMethod = classLoader
                    .loadClass(HEADER_VIEW_CLASS)
                    .getDeclaredMethod("setImage", ImageView.class);
            this.xposed.hook(setImageMethod).intercept(chain -> {
                Object result = chain.proceed();
                boolean headerEnabled;
                try {
                    headerEnabled = this.xposed.getRemotePreferences("xposed_module_config").getBoolean(PREF_KEY_HEADER_ENABLED, false);
                } catch (Throwable ignored) {
                    headerEnabled = false;
                }
                if (!headerEnabled) {
                    return result;
                }
                Bitmap bitmap = decodeHeaderBitmap();
                if (bitmap == null) {
                    log("Header image file missing: " + Environment.getExternalStorageDirectory().getPath() + DEVICE_IMAGE_PATH);
                    return result;
                }
                ((ImageView) chain.getArg(0)).setImageBitmap(bitmap);
                log("Header image loaded from " + Environment.getExternalStorageDirectory().getPath() + DEVICE_IMAGE_PATH);
                return result;
            });
        } catch (Throwable t) {
            logError("Failed to hook header image", t);
        }

        try {
            Method updateTextMethod = classLoader
                    .loadClass(HEADER_VIEW_CLASS)
                    .getDeclaredMethod("updateText");
            this.xposed.hook(updateTextMethod).intercept(chain -> {
                Object result = chain.proceed();
                boolean modelEnabled;
                try {
                    modelEnabled = this.xposed.getRemotePreferences("xposed_module_config").getBoolean(PREF_KEY_MODEL_ENABLED, false);
                } catch (Throwable ignored) {
                    modelEnabled = false;
                }
                if (!modelEnabled) {
                    return result;
                }
                String model;
                try {
                    model = this.xposed.getRemotePreferences("xposed_module_config").getString(PREF_KEY_MODEL, "");
                } catch (Throwable ignored) {
                    model = "";
                }
                if (model != null && !model.trim().isEmpty()) {
                    Field tvPadField = chain.getThisObject().getClass().getDeclaredField("tvPad");
                    tvPadField.setAccessible(true);
                    Object view = tvPadField.get(chain.getThisObject());
                    if (view instanceof TextView) {
                        ((TextView) view).setText(model);
                        log("Header model text -> " + model);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            logError("Failed to hook header text", t);
        }
    }

    private Bitmap decodeHeaderBitmap() {
        File imageFile = new File(Environment.getExternalStorageDirectory().getPath() + DEVICE_IMAGE_PATH);
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
