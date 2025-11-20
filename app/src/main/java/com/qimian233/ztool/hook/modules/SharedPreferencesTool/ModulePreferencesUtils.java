package com.qimian233.ztool.hook.modules.SharedPreferencesTool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
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
    private static final String PREFIX_ENABLED = "module_enabled_";
    // 存储直接使用SharedPreferences存储的设置项名称
    // 直接使用SharedPreferences存储设置项是不正确的，所有模块都应当使用本工具类进行配置存取
    private static final String[] otherPrefsName = {"ControlCenter_Date", "StatusBar_Clock"};

    private Context mContext;
    private String mModulePackageName;

    public ModulePreferencesUtils(Context context) {
        this(context, "com.qimian233.ztool");
    }

    public ModulePreferencesUtils(Context context, String modulePackageName) {
        this.mContext = context;
        this.mModulePackageName = modulePackageName;
    }

    /**
     * 获取模块的SharedPreferences实例
     * @noinspection deprecation
     */
    @SuppressLint("WorldReadableFiles")
    public SharedPreferences  getModulePreferences() {
        try {
            Context moduleContext = mContext.createPackageContext(mModulePackageName, Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            // 降级方案：使用当前Context
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
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
        boolean value = prefs.getBoolean(PREFIX_ENABLED + featureName, defaultValue);
        Log.d("ModulePreferences", "Loading " + featureName + ": " + value);
        return value;
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
                .putBoolean(PREFIX_ENABLED + featureName, value)
                .commit();
        Log.d("ModulePreferences", "Saved " + featureName + ": " + value + ", success: " + success);
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
        return prefs.getString(PREFIX_ENABLED + featureName, defaultValue);
    }

    /**
     * 保存字符串设置
     * @param featureName 功能名称
     * @param value 要保存的值
     * @return 是否保存成功
     */
    public boolean saveStringSetting(String featureName, String value) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .putString(PREFIX_ENABLED + featureName, value)
                .commit();
    }

    public boolean saveIntegerSetting(String featureName, int value) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .putInt(PREFIX_ENABLED + featureName, value)
                .commit();
    }

    public int loadIntegerSetting(String featureName, int defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.getInt(PREFIX_ENABLED + featureName, defaultValue);
    }

    public boolean saveFloatSetting(String featureName, float value) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .putFloat(PREFIX_ENABLED + featureName, value)
                .commit();
    }

    public float loadFloatSetting(String featureName, float defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.getFloat(PREFIX_ENABLED + featureName, defaultValue);
    }

    /**
     * 删除指定设置
     * @param featureName 功能名称
     * @return 是否删除成功
     */
    public boolean removeSetting(String featureName) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .remove(PREFIX_ENABLED + featureName)
                .commit();
    }

    /**
     * 清除所有设置
     * @return 是否清除成功
     * @noinspection deprecation
     */
    @SuppressLint("WorldReadableFiles")
    public boolean clearAllSettings() {
        SharedPreferences prefs = getModulePreferences();
        for (String key : otherPrefsName){
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(key, Context.MODE_WORLD_READABLE);
            sharedPreferences.edit().clear().commit();
        }
        return prefs.edit().clear().commit();
    }

    /**
     * 获取所有设置
     * @return 包含所有键值对的Map对象
     * @noinspection deprecation
     */
    @SuppressLint("WorldReadableFiles")
    public Map<String, Object> getAllSettings() {
        try {

            SharedPreferences prefs = getModulePreferences();
            Map<String, Object> allEntries = new HashMap<>(prefs.getAll());
            // 读取其他SharedPreferences文件中的设置，例如自定义状态栏和控制中心时间的配置
            // 所有模块都应当使用ModulePreferencesUtils来保存设置，而非SharedPreferences
            for (String name : otherPrefsName) {
                SharedPreferences otherPrefs = mContext.getSharedPreferences(name, Context.MODE_WORLD_READABLE);
                allEntries.putAll(otherPrefs.getAll());
            }
            Log.d("ModulePreferences", "成功读取所有设置，条目数：" + allEntries.size());
            return allEntries;
        } catch (Exception e) {
            Log.e("ModulePreferences", "读取所有设置失败", e);
            return Collections.emptyMap();
        }
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
            Log.d("ModulePreferences", "Successfully converted sharedprefs to json string");
            return result;
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to convert sharedprefs to json string" + e);
            return null;
        }
    }

    // 配置还原功能的辅助方法，初步将JSON字符串转换为HashMap
    public static HashMap<String, Object> jsonToHashMap(String jsonString) {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
            HashMap<String, Object> map = gson.fromJson(jsonString, type);

            // 处理Gson将数字自动转换的问题，确保Boolean值正确，并增强对Int、String和Float类型的支持
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

            // 处理Boolean值（Gson可能会将boolean解析为Double）
            if (value instanceof Double) {
                double doubleValue = (Double) value;
                // 检查是否为布尔值的数字表示（0.0或1.0）
                if (doubleValue == 0.0 || doubleValue == 1.0) {
                    processedMap.put(entry.getKey(), doubleValue == 1.0);
                } else if (doubleValue % 1 == 0) {
                    // 处理整型值（如42.0）
                    processedMap.put(entry.getKey(), (int) doubleValue);
                } else {
                    // 处理浮点型值（如3.14）
                    processedMap.put(entry.getKey(), doubleValue);
                }
            } else if (value instanceof Number) {
                // 处理其他Number类型（如Integer、Long、Float等）
                Number numberValue = (Number) value;
                // 如果值是整数，则转为Integer类型
                if (numberValue.doubleValue() % 1 == 0) {
                    processedMap.put(entry.getKey(), numberValue.intValue());
                } else {
                    // 否则转为Float类型（如果需要更高精度则使用Double）
                    processedMap.put(entry.getKey(), numberValue.floatValue());
                }
            } else {
                // 其他类型（包括String）直接处理
                processedMap.put(entry.getKey(), value);
            }
        }
        return processedMap;
    }
    /** @noinspection deprecation*/
    @SuppressLint("WorldReadableFiles")
    public void writeJSONToSharedPrefs(String jsonString) {
        Map<String, Object> mapToWrite = jsonToHashMap(jsonString);
        for (Map.Entry<String, Object> entry : mapToWrite.entrySet()) {
            try {
                String key = entry.getKey();
                Object value = entry.getValue();
                Log.d("ModulePreferencesUtils", "Processing key: " + key + ", value: " + value + ", type: " + (value != null ? value.getClass().getSimpleName() : "null"));

                // 处理先前开发中遗留的直接使用SharedPreferences的键名
                if (Arrays.asList(otherPrefsName).contains(key)) {
                    SharedPreferences sharedPreferences = mContext.getSharedPreferences(key, Context.MODE_WORLD_READABLE);
                    if (value instanceof String) {
                        sharedPreferences.edit().putString(key, (String) value).apply();
                    } else if (value instanceof Integer) {
                        if ((Integer) value == 0 || (Integer) value == 1) {
                            sharedPreferences.edit().putBoolean(key, (Integer) value == 1).apply();
                        } else {
                            sharedPreferences.edit().putInt(key, (Integer) value).apply();
                        }
                    } else if (value instanceof Float) {
                        sharedPreferences.edit().putFloat(key, (Float) value).apply();
                    } else if (value instanceof Boolean) {
                        sharedPreferences.edit().putBoolean(key, (Boolean) value).apply();
                    } else {
                        // 默认处理为字符串
                        assert value != null;
                        sharedPreferences.edit().putString(key, value.toString()).apply();
                    }
                } else {
                    // 使用工具类提供的设置保存方法
                    if (value instanceof String) {
                        Log.d("ModulePreferencesUtils", "Saving string key: " + key);
                        saveStringSetting(key.replace(PREFIX_ENABLED, ""), (String) value);
                    } else if (value instanceof Integer) {
                        Log.d("ModulePreferencesUtils", "Saving integer key: " + key);
                        if ((Integer) value == 0 || (Integer) value == 1) {
                            saveBooleanSetting(key.replace(PREFIX_ENABLED, ""), (Integer) value == 1);
                        } else {
                            saveIntegerSetting(key.replace(PREFIX_ENABLED, ""), (Integer) value);
                        }
                    } else if (value instanceof Boolean) {
                        Log.d("ModulePreferencesUtils", "Saving boolean key: " + key);
                        saveBooleanSetting(key.replace(PREFIX_ENABLED, ""), (Boolean) value);
                    } else if (value instanceof Float) {
                        Log.d("ModulePreferencesUtils", "Saving float key: " + key);
                        saveFloatSetting(key.replace(PREFIX_ENABLED, ""), (Float) value);
                    } else {
                        Log.d("ModulePreferencesUtils", "Saving unknown type key (as string): " + key);
                        assert value != null;
                        saveStringSetting(key.replace(PREFIX_ENABLED, ""), value.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ModulePreferences", "Failed to save key: " + entry.getKey() + ", value: " + entry.getValue());
            }
        }
    }

    public static void restoreConfig(Context context, String jsonToRestore){
        try{
            ModulePreferencesUtils utils = new ModulePreferencesUtils(context);
            utils.writeJSONToSharedPrefs(jsonToRestore);
            Log.d("ModulePreferences", "Successfully restored config from file.");
        }catch (Exception e){
            Log.e("ModulePreferences", "Failed to restore config from file, " + e);
        }
    }
}
