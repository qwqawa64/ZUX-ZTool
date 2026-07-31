package com.qimian233.ztool.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.qimian233.ztool.ModuleActivationProbe
import com.qimian233.ztool.XposedServiceBridge
import com.qimian233.ztool.data.PreferenceKeys
import java.io.File

/**
 * SharedPreferences 工具类，封装 Xposed 模块配置的读写操作。
 *
 * 类型推断使用 [PreferenceKeys] 中的列表循环匹配，替代硬编码的 || + equals 链。
 */
class ModulePreferencesUtils @JvmOverloads constructor(
    private val context: Context,
    private val modulePackageName: String = "com.qimian233.ztool"
) {

    // ═══════════════════════════════════════════════════════════
    // SharedPreferences 实例获取
    // ═══════════════════════════════════════════════════════════

    val modulePreferences: SharedPreferences
        get() {
            try {
                if (ModuleActivationProbe.isModuleActive()
                    && XposedServiceBridge.currentService != null
                ) {
                    return XposedServiceBridge.currentService!!
                        .getRemotePreferences(PREFS_NAME)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get remote preferences", e)
            }
            try {
                val moduleContext =
                    context.createPackageContext(modulePackageName, Context.CONTEXT_IGNORE_SECURITY)
                return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get module preferences, using fallback", e)
                return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

    // ═══════════════════════════════════════════════════════════
    // Boolean
    // ═══════════════════════════════════════════════════════════

    fun loadBooleanSetting(featureName: String, defaultValue: Boolean): Boolean {
        val prefs = modulePreferences
        return try {
            val value = prefs.getBoolean(featureName, defaultValue)
            Log.d(TAG, "Loading $featureName: $value")
            value
        } catch (e: ClassCastException) {
            val storedValue = prefs.all[featureName]
            val repaired = coerceBooleanValue(storedValue)
            if (repaired != null) {
                Log.w(
                    TAG, "Repairing illegal boolean setting type for $featureName: " +
                            storedValue?.javaClass?.simpleName
                )
                saveBooleanSetting(featureName, repaired)
                repaired
            } else {
                Log.e(
                    TAG, "Illegal boolean setting value for $featureName" +
                            ", using default: $defaultValue", e
                )
                defaultValue
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    fun saveBooleanSetting(featureName: String, value: Boolean): Boolean {
        val prefs = modulePreferences
        val success = prefs.edit()
            .putBoolean(featureName, value)
            .commit()
        Log.d(TAG, "Saved $featureName: $value, success: $success")
        return success
    }

    // ═══════════════════════════════════════════════════════════
    // String
    // ═══════════════════════════════════════════════════════════

    fun loadStringSetting(featureName: String, defaultValue: String): String {
        val prefs = modulePreferences
        return try {
            prefs.getString(featureName, defaultValue) ?: defaultValue
        } catch (e: ClassCastException) {
            val storedValue = prefs.all[featureName]
            val repaired = coerceStringValue(storedValue)
            if (repaired != null) {
                Log.w(
                    TAG, "Repairing illegal string setting type for $featureName: " +
                            storedValue?.javaClass?.simpleName
                )
                saveStringSetting(featureName, repaired)
                repaired
            } else {
                Log.e(
                    TAG, "Illegal string setting value for $featureName" +
                            ", using default", e
                )
                defaultValue
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    fun saveStringSetting(featureName: String, value: String) {
        modulePreferences.edit()
            .putString(featureName, value)
            .commit()
    }

    // ═══════════════════════════════════════════════════════════
    // Int
    // ═══════════════════════════════════════════════════════════

    @SuppressLint("ApplySharedPref")
    fun saveIntegerSetting(featureName: String, value: Int) {
        modulePreferences.edit()
            .putInt(featureName, value)
            .commit()
    }

    fun loadIntegerSetting(featureName: String, defaultValue: Int): Int {
        val prefs = modulePreferences
        return try {
            prefs.getInt(featureName, defaultValue)
        } catch (e: ClassCastException) {
            val storedValue = prefs.all[featureName]
            val repaired = coerceIntegerValue(storedValue)
            if (repaired != null) {
                Log.w(
                    TAG, "Repairing illegal integer setting type for $featureName: " +
                            storedValue?.javaClass?.simpleName
                )
                saveIntegerSetting(featureName, repaired)
                repaired
            } else {
                Log.e(
                    TAG, "Illegal integer setting value for $featureName" +
                            ", using default: $defaultValue", e
                )
                defaultValue
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Float
    // ═══════════════════════════════════════════════════════════

    @SuppressLint("ApplySharedPref")
    fun saveFloatSetting(featureName: String, value: Float) {
        modulePreferences.edit()
            .putFloat(featureName, value)
            .commit()
    }

    fun loadFloatSetting(featureName: String, defaultValue: Float): Float {
        val prefs = modulePreferences
        return try {
            prefs.getFloat(featureName, defaultValue)
        } catch (e: ClassCastException) {
            val storedValue = prefs.all[featureName]
            val repaired = coerceFloatValue(storedValue)
            if (repaired != null) {
                Log.w(
                    TAG, "Repairing illegal float setting type for $featureName: " +
                            storedValue?.javaClass?.simpleName
                )
                saveFloatSetting(featureName, repaired)
                repaired
            } else {
                Log.e(
                    TAG, "Illegal float setting value for $featureName" +
                            ", using default: $defaultValue", e
                )
                defaultValue
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 批量操作
    // ═══════════════════════════════════════════════════════════

    @SuppressLint("WorldReadableFiles", "ApplySharedPref")
    fun clearAllSettings() {
        modulePreferences.edit().clear().commit()
    }

    @SuppressLint("WorldReadableFiles")
    fun getAllSettings(): Map<String, Any> {
        return try {
            val prefs = modulePreferences
            @Suppress("UNCHECKED_CAST")
            val allEntries = HashMap(prefs.all) as HashMap<String, Any>
            Log.d(TAG, "成功读取所有设置，条目数：" + allEntries.size)
            allEntries
        } catch (e: Exception) {
            Log.e(TAG, "读取所有设置失败", e)
            emptyMap()
        }
    }

    fun getAllSettingsFromLocal(): Map<String, Any> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            @Suppress("UNCHECKED_CAST")
            val allEntries = HashMap(prefs.all) as HashMap<String, Any>
            Log.d(TAG, "Successfully read local settings, entries: " + allEntries.size)
            allEntries
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local settings", e)
            emptyMap()
        }
    }

    @SuppressLint("ApplySharedPref")
    fun deleteLocalModulePreferences() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleared = prefs.edit().clear().commit()
        if (cleared) {
            val prefsFile = File(
                context.filesDir.parentFile,
                "shared_prefs/$PREFS_NAME.xml"
            )
            if (prefsFile.exists()) {
                val deleted = prefsFile.delete()
                Log.d(TAG, "Deleted local prefs file: $deleted")
            }
        }
        Log.d(TAG, "Cleared local module preferences, success: $cleared")
    }

    // ═══════════════════════════════════════════════════════════
    // JSON 序列化
    // ═══════════════════════════════════════════════════════════

    fun getAllSettingsAsJSON(): String? {
        return try {
            val gson = GsonBuilder()
                .setPrettyPrinting()
                .create()
            gson.toJson(getAllSettings())
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 配置还原
    // ═══════════════════════════════════════════════════════════

    fun writeJSONToSharedPrefs(jsonString: String) {
        val mapToWrite = jsonToHashMap(jsonString)
        writeConfigToSharedPrefs(mapToWrite)
    }

    /**
     * 将 Map 写入 SharedPreferences。
     *
     * 使用 [PreferenceKeys] 的列表循环匹配来推断每个键的数据类型，
     * 替代以前的大量 || + equals 硬编码链。
     */
    fun writeConfigToSharedPrefs(mapToWrite: Map<String, Any>) {
        for ((key, value) in mapToWrite) {
            try {
                // 处理旧版本配置遗留的多余前缀 module_enabled_
                val cleanKey = key.replace("module_enabled_", "")
                Log.d(
                    TAG, "Processing key: $cleanKey, value: $value, type: " +
                            value?.javaClass?.simpleName
                )
                when {
                    PreferenceKeys.isFloatKey(cleanKey) -> {
                        val floatValue = coerceFloatValue(value)
                        if (floatValue != null) {
                            Log.d(TAG, "Saving float key: $cleanKey")
                            saveFloatSetting(cleanKey, floatValue)
                        } else {
                            Log.w(TAG, "Invalid float key value, skip: $cleanKey = $value")
                        }
                    }
                    PreferenceKeys.isIntKey(cleanKey) -> {
                        val intValue = coerceIntegerValue(value)
                        if (intValue != null) {
                            Log.d(TAG, "Saving integer key: $cleanKey")
                            saveIntegerSetting(cleanKey, intValue)
                        } else {
                            Log.w(TAG, "Invalid integer key value, skip: $cleanKey = $value")
                        }
                    }
                    PreferenceKeys.isBooleanKey(cleanKey) -> {
                        val booleanValue = coerceBooleanValue(value)
                        if (booleanValue != null) {
                            Log.d(TAG, "Saving boolean key: $cleanKey")
                            saveBooleanSetting(cleanKey, booleanValue)
                        } else {
                            Log.w(TAG, "Invalid boolean key value, skip: $cleanKey = $value")
                        }
                    }
                    value is String -> {
                        Log.d(TAG, "Saving string key: $cleanKey")
                        saveStringSetting(cleanKey, value)
                    }
                    value is Int -> {
                        Log.d(TAG, "Saving integer key: $cleanKey")
                        saveIntegerSetting(cleanKey, value)
                    }
                    value is Boolean -> {
                        Log.d(TAG, "Saving boolean key: $cleanKey")
                        saveBooleanSetting(cleanKey, value)
                    }
                    value is Float -> {
                        Log.d(TAG, "Saving single precision FP key: $cleanKey")
                        saveFloatSetting(cleanKey, value)
                    }
                    value is Double -> {
                        Log.d(TAG, "Saving double precision FP key: $cleanKey")
                        saveFloatSetting(cleanKey, value.toFloat())
                    }
                    value != null -> {
                        Log.d(TAG, "Saving unknown type key (as string): $cleanKey")
                        saveStringSetting(cleanKey, value.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save key: $key, value: $value, error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 类型强制转换辅助方法
    // ═══════════════════════════════════════════════════════════

    companion object {
        private const val PREFS_NAME = "xposed_module_config"
        private const val TAG = "ZToolXposedModulePrefsUtils"

        @JvmStatic
        fun getAllSettingsAsJSON(context: Context): String? {
            return try {
                val utils = ModulePreferencesUtils(context)
                val result = utils.getAllSettingsAsJSON()
                Log.d(TAG, "Successfully converted sharedprefs to json string")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert sharedprefs to json string$e")
                null
            }
        }

        @JvmStatic
        fun restoreConfig(context: Context, jsonToRestore: String) {
            try {
                val utils = ModulePreferencesUtils(context)
                utils.writeJSONToSharedPrefs(jsonToRestore)
                Log.d(TAG, "Successfully restored config from file.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore config from file, $e")
            }
        }

        @JvmStatic
        fun jsonToHashMap(jsonString: String): HashMap<String, Any> {
            return try {
                val gson = Gson()
                val type = object : TypeToken<HashMap<String, Any>>() {}.type
                val map: HashMap<String, Any> = gson.fromJson(jsonString, type)
                processMapValues(map)
            } catch (e: Exception) {
                Log.e("JsonToMapConverter", "JSON转换失败", e)
                HashMap()
            }
        }

        private fun processMapValues(map: HashMap<String, Any>): HashMap<String, Any> {
            val processedMap = HashMap<String, Any>()
            for ((key, value) in map) {
                when (value) {
                    is Double -> {
                        processedMap[key] = if (value % 1 == 0.0) value.toInt() else value
                    }
                    is Number -> {
                        processedMap[key] = if (value.toDouble() % 1 == 0.0)
                            value.toInt() else value.toFloat()
                    }
                    else -> processedMap[key] = value
                }
            }
            return processedMap
        }

        // ── 值强制转换 ──

        @JvmStatic
        fun coerceFloatValue(value: Any?): Float? {
            return when (value) {
                is Number -> value.toFloat()
                is Boolean -> if (value) 1.0f else 0.0f
                is String -> value.toFloatOrNull()
                else -> null
            }
        }

        @JvmStatic
        fun coerceIntegerValue(value: Any?): Int? {
            return when (value) {
                is Number -> {
                    val doubleValue = value.toDouble()
                    if (doubleValue % 1 == 0.0) value.toInt() else null
                }
                is Boolean -> if (value) 1 else 0
                is String -> {
                    value.toIntOrNull()
                        ?: value.toFloatOrNull()?.let {
                            if (it % 1 == 0.0f) it.toInt() else null
                        }
                }
                else -> null
            }
        }

        @JvmStatic
        fun coerceBooleanValue(value: Any?): Boolean? {
            return when (value) {
                is Boolean -> value
                is Number -> {
                    val doubleValue = value.toDouble()
                    if (doubleValue == 0.0 || doubleValue == 1.0) doubleValue == 1.0 else null
                }
                is String -> {
                    val trimmed = value.trim()
                    when {
                        trimmed.equals("true", ignoreCase = true) || trimmed == "1" -> true
                        trimmed.equals("false", ignoreCase = true) || trimmed == "0" -> false
                        else -> null
                    }
                }
                else -> null
            }
        }

        @JvmStatic
        fun coerceStringValue(value: Any?): String? = value?.toString()
    }
}
