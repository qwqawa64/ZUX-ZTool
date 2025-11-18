package com.qimian233.ztool.hook.modules.SharedPreferencesTool;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
     */
    public SharedPreferences getModulePreferences() {
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
     */
    public boolean clearAllSettings() {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .clear()
                .commit();
    }

    /**
     * 获取所有设置
     * @return 包含所有键值对的Map对象
     */
    public Map<String, Object> getAllSettings() {
        try {
            SharedPreferences prefs = getModulePreferences();
            Map<String, Object> allEntries = new HashMap<>();
            allEntries.putAll(prefs.getAll());
            Log.d("ModulePreferences", "成功读取所有设置，条目数：" + allEntries.size());
            return allEntries;
        } catch (Exception e) {
            Log.e("ModulePreferences", "读取所有设置失败", e);
            return Collections.emptyMap();
        }
    }

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

    public static HashMap<String, Object> jsonToHashMap(String jsonString) {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
            HashMap<String, Object> map = gson.fromJson(jsonString, type);

            // 处理Gson将数字自动转换的问题，确保Boolean值正确
            return processMapValues(map);

        } catch (Exception e) {
            Log.e("JsonToMapConverter", "JSON转换失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 处理Map中的值，确保Boolean类型正确
     */
    private static HashMap<String, Object> processMapValues(HashMap<String, Object> map) {
        HashMap<String, Object> processedMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();

            // 处理Boolean值（Gson可能会将boolean解析为Double）
            if (value instanceof Double) {
                Double doubleValue = (Double) value;
                // 检查是否为布尔值的数字表示（0.0或1.0）
                if (doubleValue == 0.0 || doubleValue == 1.0) {
                    processedMap.put(entry.getKey(), doubleValue == 1.0);
                } else {
                    processedMap.put(entry.getKey(), value);
                }
            } else {
                processedMap.put(entry.getKey(), value);
            }
        }
        return processedMap;
    }

    public void writeJSONToSharedPrefs(String jsonString){
        Map <String, Object> mapToWrite = jsonToHashMap(jsonString);
        for (Map.Entry<String, Object> entry : mapToWrite.entrySet()){
            try {
                String key = entry.getKey();
                Log.d("ModulePreferences", "Processing key: " + key);
                if (entry.getValue() instanceof String) {
                    Log.d("ModulePreferences", "Saving string key: " + key);
                    String value = entry.getValue().toString();
                    saveStringSetting(key, value);
                } else {
                    Log.d("ModulePreferences", "Saving boolean key: " + key);
                    Boolean value = (Boolean) entry.getValue();
                    // 修改这里：去掉前缀，因为saveBooleanSetting会自动添加
                    saveBooleanSetting(key.replace(PREFIX_ENABLED, ""), value);
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
