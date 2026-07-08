package com.qimian233.ztool.hook.modules.SharedPreferencesTool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.qimian233.ztool.ModuleActivationProbe;
import com.qimian233.ztool.XposedServiceBridge;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * SharedPreferences工具类，封装Xposed模块配置的读写操作
 */
public class ModulePreferencesUtils {

    private static final String PREFS_NAME = "xposed_module_config";
    private final Context mContext;
    private final String mModulePackageName;
    private static final String TAG = "ZToolXposedModulePrefsUtils";

    public ModulePreferencesUtils(Context context) {
        this(context, "com.qimian233.ztool");
    }

    public ModulePreferencesUtils(Context context, String modulePackageName) {
        this.mContext = context;
        this.mModulePackageName = modulePackageName;
    }

    /**
     * 获取模块的SharedPreferences实例
     */
    public SharedPreferences  getModulePreferences() {
        try {
            if (ModuleActivationProbe.INSTANCE.isModuleActive()
                    && XposedServiceBridge.INSTANCE.getCurrentService() != null) {
                return XposedServiceBridge.INSTANCE.getCurrentService().getRemotePreferences(PREFS_NAME);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get remote preferences", e);
        }
        try {
            Context moduleContext = mContext.createPackageContext(mModulePackageName, Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get module preferences, using fallback", e);
            // 降级方案：使用当前Context
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * 加载布尔型设置
     * @param featureName 功能名称（如："remove_blacklist"）
     * @param defaultValue 默认值
     * @return 设置值
     */
    public boolean loadBooleanSetting(String featureName, boolean defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        try {
            boolean value = prefs.getBoolean(featureName, defaultValue);
            Log.d(TAG, "Loading " + featureName + ": " + value);
            return value;
        } catch (ClassCastException e) {
            Object storedValue = prefs.getAll().get(featureName);
            Boolean repairedValue = coerceBooleanValue(storedValue);
            if (repairedValue != null) {
                Log.w(TAG, "Repairing illegal boolean setting type for " + featureName
                        + ": " + storedValue.getClass().getSimpleName());
                saveBooleanSetting(featureName, repairedValue);
                return repairedValue;
            }
            Log.e(TAG, "Illegal boolean setting value for " + featureName
                    + ", using default: " + defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 保存布尔型设置
     * @param featureName 功能名称（如："remove_blacklist"）
     * @param value 要保存的值
     * @return 是否保存成功
     */
    public boolean saveBooleanSetting(String featureName, boolean value) {
        SharedPreferences prefs = getModulePreferences();
        boolean success = prefs.edit()
                .putBoolean(featureName, value)
                .commit();
        Log.d(TAG, "Saved " + featureName + ": " + value + ", success: " + success);
        return success;
    }

    /**
     * 加载字符串设置
     * @param featureName 功能名称
     * @param defaultValue 默认值
     * @return 设置值
     */
    public String loadStringSetting(String featureName, String defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        try {
            return prefs.getString(featureName, defaultValue);
        } catch (ClassCastException e) {
            Object storedValue = prefs.getAll().get(featureName);
            String repairedValue = coerceStringValue(storedValue);
            if (repairedValue != null) {
                Log.w(TAG, "Repairing illegal string setting type for " + featureName
                        + ": " + storedValue.getClass().getSimpleName());
                saveStringSetting(featureName, repairedValue);
                return repairedValue;
            }
            Log.e(TAG, "Illegal string setting value for " + featureName
                    + ", using default", e);
            return defaultValue;
        }
    }

    /**
     * 保存字符串设置
     *
     * @param featureName 功能名称
     * @param value       要保存的值
     */
    @SuppressLint("ApplySharedPref")
    public void saveStringSetting(String featureName, String value) {
        SharedPreferences prefs = getModulePreferences();
        prefs.edit()
                .putString(featureName, value)
                .commit();
    }

    @SuppressLint("ApplySharedPref")
    public void saveIntegerSetting(String featureName, int value) {
        SharedPreferences prefs = getModulePreferences();
        prefs.edit()
                .putInt(featureName, value)
                .commit();
    }

    public int loadIntegerSetting(String featureName, int defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        try {
            return prefs.getInt(featureName, defaultValue);
        } catch (ClassCastException e) {
            Object storedValue = prefs.getAll().get(featureName);
            Integer repairedValue = coerceIntegerValue(storedValue);
            if (repairedValue != null) {
                Log.w(TAG, "Repairing illegal integer setting type for " + featureName
                        + ": " + storedValue.getClass().getSimpleName());
                saveIntegerSetting(featureName, repairedValue);
                return repairedValue;
            }
            Log.e(TAG, "Illegal integer setting value for " + featureName
                    + ", using default: " + defaultValue, e);
            return defaultValue;
        }
    }

    @SuppressLint("ApplySharedPref")
    public void saveFloatSetting(String featureName, float value) {
        SharedPreferences prefs = getModulePreferences();
        prefs.edit()
                .putFloat(featureName, value)
                .commit();
    }

    public float loadFloatSetting(String featureName, float defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        try {
            return prefs.getFloat(featureName, defaultValue);
        } catch (ClassCastException e) {
            Object storedValue = prefs.getAll().get(featureName);
            Float repairedValue = coerceFloatValue(storedValue);
            if (repairedValue != null) {
                Log.w(TAG, "Repairing illegal float setting type for " + featureName
                        + ": " + storedValue.getClass().getSimpleName());
                saveFloatSetting(featureName, repairedValue);
                return repairedValue;
            }
            Log.e(TAG, "Illegal float setting value for " + featureName
                    + ", using default: " + defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 清除所有设置
     */
    @SuppressLint({"WorldReadableFiles", "ApplySharedPref"})
    public void clearAllSettings() {
        SharedPreferences prefs = getModulePreferences();
        prefs.edit().clear().commit();
    }

    /**
     * 获取所有设置
     * @return 包含所有键值对的Map对象
     */
    @SuppressLint("WorldReadableFiles")
    public Map<String, Object> getAllSettings() {
        try {
            SharedPreferences prefs = getModulePreferences();
            Map<String, Object> allEntries = new HashMap<>(prefs.getAll());
            // 读取其他SharedPreferences文件中的设置，例如自定义状态栏和控制中心时间的配置
            // 所有模块都应当使用ModulePreferencesUtils来保存设置，而非SharedPreferences
            Log.d(TAG, "成功读取所有设置，条目数：" + allEntries.size());
            return allEntries;
        } catch (Exception e) {
            Log.e(TAG, "读取所有设置失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 从本地 SharedPreferences 加载所有设置，绕过 RemotePreferences
     * 用于旧 XSharedPreferences 配置迁移场景
     * @return 包含所有键值对的Map对象
     */
    public Map<String, Object> getAllSettingsFromLocal() {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Map<String, Object> allEntries = new HashMap<>(prefs.getAll());
            Log.d(TAG, "Successfully read local settings, entries: " + allEntries.size());
            return allEntries;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read local settings", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 删除本地 SharedPreferences 文件
     * 用于配置迁移完成后清理
     * @return 是否删除成功
     */
    @SuppressLint("ApplySharedPref")
    public boolean deleteLocalModulePreferences() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean cleared = prefs.edit().clear().commit();
        if (cleared) {
            File prefsFile = new File(mContext.getFilesDir().getParentFile(),
                "shared_prefs/" + PREFS_NAME + ".xml");
            if (prefsFile.exists()) {
                boolean deleted = prefsFile.delete();
                Log.d(TAG, "Deleted local prefs file: " + deleted);
            }
        }
        Log.d(TAG, "Cleared local module preferences, success: " + cleared);
        return cleared;
    }

    // 处理getAllSettings的返回值，转换为JSON格式
    public String getAllSettingsAsJSON(){
        String result;
        try {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting() // 可选：美化输出，便于阅读
                    .create();
            result = gson.toJson(getAllSettings());
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    /**
     * 静态方法：获取所有设置并以JSON格式返回（需要Context参数）
     * 方便外部调用
     * @param context 上下文对象
     * @return JSON格式的设置数据
     */
    public static String getAllSettingsAsJSON(Context context) {
        try {
            ModulePreferencesUtils utils = new ModulePreferencesUtils(context);
            String result = utils.getAllSettingsAsJSON();
            Log.d(TAG, "Successfully converted sharedprefs to json string");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert sharedprefs to json string" + e);
            return null;
        }
    }

    // 配置还原功能的辅助方法，初步将JSON字符串转换为HashMap
    public static HashMap<String, Object> jsonToHashMap(String jsonString) {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
            HashMap<String, Object> map = gson.fromJson(jsonString, type);

            return processMapValues(map);

        } catch (Exception e) {
            Log.e("JsonToMapConverter", "JSON转换失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 配置还原功能的辅助方法
     * 处理Map中的值，确保Boolean、Int、String和Float类型正确
     */
    private static HashMap<String, Object> processMapValues(HashMap<String, Object> map) {
        HashMap<String, Object> processedMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof Double) {
                double doubleValue = (Double) value;
                if (doubleValue % 1 == 0) {
                    processedMap.put(entry.getKey(), (int) doubleValue);
                } else {
                    processedMap.put(entry.getKey(), doubleValue);
                }
            } else if (value instanceof Number) {
                Number numberValue = (Number) value;
                if (numberValue.doubleValue() % 1 == 0) {
                    processedMap.put(entry.getKey(), numberValue.intValue());
                } else {
                    processedMap.put(entry.getKey(), numberValue.floatValue());
                }
            } else {
                processedMap.put(entry.getKey(), value);
            }
        }
        return processedMap;
    }

    public void writeJSONToSharedPrefs(String jsonString) {
        Map<String, Object> mapToWrite = jsonToHashMap(jsonString);
        writeConfigToSharedPrefs(mapToWrite);
    }

    public void writeConfigToSharedPrefs(Map<String,Object> mapToWrite) {
        for (Map.Entry<String, Object> entry : mapToWrite.entrySet()) {
            try {
                Object value = entry.getValue();
                // 处理旧版本配置遗留的多余前缀module_enabled_
                String cleanKey = entry.getKey().replace("module_enabled_", "");
                Log.d(TAG, "Processing key: "
                        + cleanKey + ", value: " + value + ", type: " +
                        (value != null ? value.getClass().getSimpleName() : "null"));
                if (isFloatSettingKey(cleanKey)) {
                    Float floatValue = coerceFloatValue(value);
                    if (floatValue != null) {
                        Log.d(TAG, "Saving float key: " + cleanKey);
                        saveFloatSetting(cleanKey, floatValue);
                    } else {
                        Log.w(TAG, "Invalid float key value, skip: " + cleanKey + " = " + value);
                    }
                } else if (isIntegerSettingKey(cleanKey)) {
                    Integer intValue = coerceIntegerValue(value);
                    if (intValue != null) {
                        Log.d(TAG, "Saving integer key: " + cleanKey);
                        saveIntegerSetting(cleanKey, intValue);
                    } else {
                        Log.w(TAG, "Invalid integer key value, skip: " + cleanKey + " = " + value);
                    }
                } else if (isBooleanSettingKey(cleanKey)) {
                    Boolean booleanValue = coerceBooleanValue(value);
                    if (booleanValue != null) {
                        Log.d(TAG, "Saving boolean key: " + cleanKey);
                        saveBooleanSetting(cleanKey, booleanValue);
                    } else {
                        Log.w(TAG, "Invalid boolean key value, skip: " + cleanKey + " = " + value);
                    }
                } else if (value instanceof String) {
                    Log.d(TAG, "Saving string key: " + cleanKey);
                    saveStringSetting(cleanKey, (String) value);
                } else if (value instanceof Integer){
                    Log.d(TAG, "Saving integer key: " + cleanKey);
                    saveIntegerSetting(cleanKey, (Integer) value);
                }else if (value instanceof Boolean) {
                    Log.d(TAG, "Saving boolean key: " + cleanKey);
                    saveBooleanSetting(cleanKey, (Boolean) value);
                } else if (value instanceof Float) {
                    Log.d(TAG, "Saving single precision FP key: " + cleanKey);
                    saveFloatSetting(cleanKey, (Float) value);
                } else if (value instanceof Double) {
                    Log.d(TAG, "Saving double precision FP key: " + cleanKey);
                    saveFloatSetting(cleanKey, ((Double) value).floatValue());
                } else if (value != null) {
                    Log.d(TAG, "Saving unknown type key (as string): " + cleanKey);
                    saveStringSetting(cleanKey, value.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save key: " + entry.getKey()
                        + ", value: " + entry.getValue() + ", error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static Float coerceFloatValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1.0f : 0.0f;
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer coerceIntegerValue(Object value) {
        if (value instanceof Number) {
            double doubleValue = ((Number) value).doubleValue();
            if (doubleValue % 1 == 0) {
                return ((Number) value).intValue();
            }
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                try {
                    float floatValue = Float.parseFloat((String) value);
                    if (floatValue % 1 == 0) {
                        return (int) floatValue;
                    }
                } catch (NumberFormatException ignoredAgain) {
                    // Fall through to the shared null return.
                }
            }
        }
        return null;
    }

    private static Boolean coerceBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            double doubleValue = ((Number) value).doubleValue();
            if (doubleValue == 0.0d || doubleValue == 1.0d) {
                return doubleValue == 1.0d;
            }
        }
        if (value instanceof String) {
            String stringValue = ((String) value).trim();
            if ("true".equalsIgnoreCase(stringValue) || "1".equals(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue) || "0".equals(stringValue)) {
                return false;
            }
        }
        return null;
    }

    private static String coerceStringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean isFloatSettingKey(String key) {
        return "systemui_network_speed_refresh_interval".equals(key)
                || "Custom_StatusBarClockTextSize".equals(key)
                || "Custom_StatusBarClockLetterSpacing".equals(key)
                || "Custom_ControlCenterDateTextSize".equals(key)
                || "Custom_ControlCenterDateLetterSpacing".equals(key);
    }

    private static boolean isIntegerSettingKey(String key) {
        return "custom_launcher_row".equals(key)
                || "custom_launcher_column".equals(key)
                || "Custom_StatusBarClockTextColor".equals(key)
                || "notify_num_size".equals(key)
                || "Custom_ControlCenterDateTextColor".equals(key)
                || "tile_round_corner_radius".equals(key)
                || "head_up_round_corner_radius".equals(key)
                || "custom_qs_active_color_val".equals(key)
                || "custom_label_active_color_val".equals(key)
                || "custom_second_label_active_color_val".equals(key)
                || "notification_center_blur_percent".equals(key)
                || "screen_on_off_animation_duration".equals(key);
    }

    private static boolean isBooleanSettingKey(String key) {
        return "disable_game_audio".equals(key)
                || "disable_game_audio_app".equals(key)
                || "disguise_device".equals(key)
                || "fix_cpu_frequency".equals(key)
                || "fix_soc_temperature".equals(key)
                || "auto_mistake_touch".equals(key)
                || "mistake_touch_white_list".equals(key)
                || "disable_force_stop".equals(key)
                || "force_stop_white_list_enable".equals(key)
                || "zui_launcher_hotseat".equals(key)
                || "custom_grid_size".equals(key)
                || "clean_global_search".equals(key)
                || "remove_hot_word_in_search_box".equals(key)
                || "remove_hot_word_view".equals(key)
                || "show_ram_info".equals(key)
                || "beautify_ram_info".equals(key)
                || "disable_dock_bar".equals(key)
                || "launcher_no_label_mode".equals(key)
                || "zui_launcher_hotseat_backup".equals(key)
                || "disable_dock_warning_confirmed".equals(key)
                || "disable_ota_check".equals(key)
                || "hide_ota_update_hint".equals(key)
                || "custom_ota_parameters".equals(key)
                || "auto_check_update".equals(key)
                || "enable_homepage_yiyan".equals(key)
                || "skip_expose_warn".equals(key)
                || "auto_accept_file_transfer".equals(key)
                || "force_native_aod".equals(key)
                || "force_lenovo_aod".equals(key)
                || "no_charge_animation".equals(key)
                || "charge_animation_fix".equals(key)
                || "guest_mode_controller".equals(key)
                || "StatusBarDisplay_Seconds".equals(key)
                || "Custom_StatusBarClock".equals(key)
                || "NativeNotificationIcon".equals(key)
                || "systemui_network_speed_size".equals(key)
                || "systemui_network_speed_doublelayer".equals(key)
                || "systemui_network_speed_refresh_enabled".equals(key)
                || "systemui_battery_percentage".equals(key)
                || "Custom_StatusBarClockTextSizeEnabled".equals(key)
                || "Custom_StatusBarClockLetterSpacingEnabled".equals(key)
                || "Custom_StatusBarClockTextColorEnabled".equals(key)
                || "Custom_StatusBarClockTextBold".equals(key)
                || "notification_icon_limit".equals(key)
                || "auto_owner_info".equals(key)
                || "YiYan".equals(key)
                || "systemui_charge_watts".equals(key)
                || "systemUI_RealWatts".equals(key)
                || "isSystemUIPermissionConfirmed".equals(key)
                || "Custom_ControlCenterDate".equals(key)
                || "Custom_ControlCenterDateTextSizeEnabled".equals(key)
                || "Custom_ControlCenterDateLetterSpacingEnabled".equals(key)
                || "Custom_ControlCenterDateTextColorEnabled".equals(key)
                || "Custom_ControlCenterDateTextBold".equals(key)
                || "qs_round_corner".equals(key)
                || "custom_qs_color".equals(key)
                || "custom_label_color".equals(key)
                || "custom_second_label_color".equals(key)
                || "control_center_no_tile_labels".equals(key)
                || "qs_color".equals(key)
                || "notification_center_blur".equals(key)
                || "volume_slider_percentage".equals(key)
                || "brightness_slider_percentage".equals(key)
                || "default_enable_autorun".equals(key)
                || "disable_all_virus_scans".equals(key)
                || "documentsui_bypass".equals(key)
                || "disable_scan_apk".equals(key)
                || "always_allow_permission".equals(key)
                || "skip_warn_page".equals(key)
                || "disable_installer_ad".equals(key)
                || "package_installer_style_hook".equals(key)
                || "disable_delete_package".equals(key)
                || "detailed_logging".equals(key)
                || "display_entry_in_settings".equals(key)
                || "lsposed_service_protector".equals(key)
                || "remove_blacklist".equals(key)
                || "split_screen_mandatory".equals(key)
                || "permission_controller_hook".equals(key)
                || "allow_display_dolby".equals(key)
                || "app_details".equals(key)
                || "allow_get_packages".equals(key)
                || "keep_rotation".equals(key)
                || "disable_flag_secure".equals(key)
                || "ai_input_expand".equals(key)
                || "force_on_off_animation".equals(key)
                || "no_password_per_24h".equals(key)
                || "allow_untrusted_touch".equals(key)
                || "isConfigUpgraded".equals(key)
                || "about_device_info".equals(key)
                || "about_device_info_model_enabled".equals(key)
                || "about_device_info_cpu_enabled".equals(key)
                || "about_device_info_ram_enabled".equals(key)
                || "about_device_info_rom_enabled".equals(key)
                || "about_device_info_software_enabled".equals(key)
                || "about_device_info_header_enabled".equals(key);
    }

    public static void restoreConfig(Context context, String jsonToRestore){
        try{
            ModulePreferencesUtils utils = new ModulePreferencesUtils(context);
            utils.writeJSONToSharedPrefs(jsonToRestore);
            Log.d(TAG, "Successfully restored config from file.");
        }catch (Exception e){
            Log.e(TAG, "Failed to restore config from file, " + e);
        }
    }
}
