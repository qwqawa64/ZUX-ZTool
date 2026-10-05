package com.qimian233.ztool.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils

/**
 * Backing preference files the editor can address. The xposed module config is
 * read/written through [ModulePreferencesUtils] (remote LSPosed preferences with
 * a local fallback); the theme file is plain local SharedPreferences.
 */
enum class PreferenceEditorTarget(val prefsFileName: String) {
    XPOSED_MODULE_CONFIG("xposed_module_config"),
    UI_THEME_PREFERENCES("ztool_ui_theme_preferences")
}

enum class PreferenceEditorFailureReason { TYPE_ERROR, KEY_NOT_FOUND, OTHER }

/** Result of one editor operation, rendered verbatim by the result card. */
sealed interface PreferenceEditOutcome {
    /** Read: the raw stored value with its actual Java type. */
    data class Read(
        val key: String,
        val value: Any?,
        val typeName: String,
        val viaRemote: Boolean?
    ) : PreferenceEditOutcome

    data class Write(
        val key: String,
        val writtenTypeName: String,
        val viaRemote: Boolean?
    ) : PreferenceEditOutcome

    data class Delete(
        val key: String,
        val viaRemote: Boolean?
    ) : PreferenceEditOutcome

    data class ListAll(val entries: Map<String, Any>) : PreferenceEditOutcome

    data class Failure(
        val reason: PreferenceEditorFailureReason,
        val message: String?
    ) : PreferenceEditOutcome
}

/**
 * Read/write/list/delete access to the editor target preference files.
 *
 * Type discipline:
 * - xposed_module_config: registered [PreferenceKeys] names are clamped to their
 *   declared type; unregistered keys are written as plain Strings (the caller
 *   shows a warning first).
 * - ztool_ui_theme_preferences: a local shim maps every existing theme key to
 *   its stored type (stand-in for a systematic key-type registry); unknown
 *   theme keys fall back to String after caller confirmation.
 */
class PreferenceEditorRepository(context: Context) {

    private val appContext = context.applicationContext
    private val moduleUtils = ModulePreferencesUtils(appContext)

    /** For [PreferenceEditorTarget.XPOSED_MODULE_CONFIG]: remote service vs local fallback. */
    val xposedViaRemote: Boolean
        get() = moduleUtils.isUsingRemotePreferences

    fun isRegisteredKey(target: PreferenceEditorTarget, key: String): Boolean = when (target) {
        PreferenceEditorTarget.XPOSED_MODULE_CONFIG -> isXposedKeyRegistered(key)
        PreferenceEditorTarget.UI_THEME_PREFERENCES -> THEME_KEY_TYPES.containsKey(key)
    }

    fun read(target: PreferenceEditorTarget, key: String): PreferenceEditOutcome {
        val prefs = prefsFor(target) ?: return storageFailure(target)
        return try {
            if (!prefs.all.containsKey(key)) {
                PreferenceEditOutcome.Failure(
                    PreferenceEditorFailureReason.KEY_NOT_FOUND,
                    "key \"$key\" does not exist"
                )
            } else {
                val value = prefs.all[key]
                PreferenceEditOutcome.Read(
                    key = key,
                    value = value,
                    typeName = value?.javaClass?.simpleName ?: "null",
                    viaRemote = viaRemote(target)
                )
            }
        } catch (e: Exception) {
            PreferenceEditOutcome.Failure(PreferenceEditorFailureReason.OTHER, e.message)
        }
    }

    /**
     * Write with type clamping. [allowUnregisteredAsString] must only be true
     * after the caller has confirmed the unregistered-key warning with the user.
     */
    fun write(
        target: PreferenceEditorTarget,
        key: String,
        rawValue: String,
        allowUnregisteredAsString: Boolean
    ): PreferenceEditOutcome {
        val prefs = prefsFor(target) ?: return storageFailure(target)
        return try {
            val writtenType = when (target) {
                PreferenceEditorTarget.XPOSED_MODULE_CONFIG ->
                    writeXposed(prefs, key, rawValue, allowUnregisteredAsString)
                PreferenceEditorTarget.UI_THEME_PREFERENCES ->
                    writeTheme(prefs, key, rawValue, allowUnregisteredAsString)
            } ?: return PreferenceEditOutcome.Failure(
                PreferenceEditorFailureReason.TYPE_ERROR,
                "value \"$rawValue\" does not match the registered type of \"$key\""
            )
            if (!prefs.contains(key)) {
                return PreferenceEditOutcome.Failure(
                    PreferenceEditorFailureReason.OTHER,
                    "commit did not persist the key"
                )
            }
            PreferenceEditOutcome.Write(
                key = key,
                writtenTypeName = writtenType,
                viaRemote = viaRemote(target)
            )
        } catch (e: Exception) {
            PreferenceEditOutcome.Failure(PreferenceEditorFailureReason.OTHER, e.message)
        }
    }

    fun delete(target: PreferenceEditorTarget, key: String): PreferenceEditOutcome {
        val prefs = prefsFor(target) ?: return storageFailure(target)
        return try {
            if (!prefs.all.containsKey(key)) {
                PreferenceEditOutcome.Failure(
                    PreferenceEditorFailureReason.KEY_NOT_FOUND,
                    "key \"$key\" does not exist"
                )
            } else {
                val success = when (target) {
                    PreferenceEditorTarget.XPOSED_MODULE_CONFIG ->
                        moduleUtils.removeSetting(key)
                    PreferenceEditorTarget.UI_THEME_PREFERENCES ->
                        @Suppress("ApplySharedPref")
                        prefs.edit().remove(key).commit()
                }
                if (success) {
                    PreferenceEditOutcome.Delete(key = key, viaRemote = viaRemote(target))
                } else {
                    PreferenceEditOutcome.Failure(
                        PreferenceEditorFailureReason.OTHER,
                        "commit returned false"
                    )
                }
            }
        } catch (e: Exception) {
            PreferenceEditOutcome.Failure(PreferenceEditorFailureReason.OTHER, e.message)
        }
    }

    fun listAll(target: PreferenceEditorTarget): PreferenceEditOutcome {
        val prefs = prefsFor(target) ?: return storageFailure(target)
        return try {
            PreferenceEditOutcome.ListAll(java.util.TreeMap(prefs.all))
        } catch (e: Exception) {
            PreferenceEditOutcome.Failure(PreferenceEditorFailureReason.OTHER, e.message)
        }
    }

    // Xposed module config (PreferenceKeys clamping)

    private fun isXposedKeyRegistered(key: String): Boolean =
        PreferenceKeys.isBooleanKey(key) || PreferenceKeys.isIntKey(key) ||
                PreferenceKeys.isFloatKey(key) ||
                PreferenceKeys.findStringKey(key) != null

    /** Returns the written type name, or null on a type-clamp failure. */
    private fun writeXposed(
        prefs: SharedPreferences,
        key: String,
        rawValue: String,
        allowUnregisteredAsString: Boolean
    ): String? = when {
        PreferenceKeys.isBooleanKey(key) -> {
            val coerced = ModulePreferencesUtils.coerceBooleanValue(rawValue) ?: return null
            moduleUtils.saveBooleanSetting(key, coerced)
            "Boolean"
        }
        PreferenceKeys.isIntKey(key) -> {
            val coerced = ModulePreferencesUtils.coerceIntegerValue(rawValue) ?: return null
            moduleUtils.saveIntegerSetting(key, coerced)
            "Int"
        }
        PreferenceKeys.isFloatKey(key) -> {
            val coerced = ModulePreferencesUtils.coerceFloatValue(rawValue) ?: return null
            moduleUtils.saveFloatSetting(key, coerced)
            "Float"
        }
        isXposedKeyRegistered(key) -> {
            moduleUtils.saveStringSetting(key, rawValue)
            "String"
        }
        allowUnregisteredAsString -> {
            moduleUtils.saveStringSetting(key, rawValue)
            "String"
        }
        else -> null
    }

    // Theme local prefs (shim type map)

    /** Returns the written type name, or null on a type-clamp failure. */
    private fun writeTheme(
        prefs: SharedPreferences,
        key: String,
        rawValue: String,
        allowUnregisteredAsString: Boolean
    ): String? {
        val declaredType = THEME_KEY_TYPES[key]
            ?: return if (allowUnregisteredAsString) {
                prefs.edit(commit = true) { putString(key, rawValue) }
                "String"
            } else {
                null
            }
        return when (declaredType) {
            ThemeKeyType.STRING -> {
                prefs.edit(commit = true) { putString(key, rawValue) }
                "String"
            }
            ThemeKeyType.BOOLEAN -> {
                val coerced = ModulePreferencesUtils.coerceBooleanValue(rawValue) ?: return null
                prefs.edit(commit = true) { putBoolean(key, coerced) }
                "Boolean"
            }
            ThemeKeyType.LONG -> {
                val coerced = ModulePreferencesUtils.coerceIntegerValue(rawValue) ?: return null
                prefs.edit(commit = true) { putLong(key, coerced.toLong()) }
                "Long"
            }
        }
    }

    // Common plumbing

    private fun prefsFor(target: PreferenceEditorTarget): SharedPreferences? = try {
        when (target) {
            PreferenceEditorTarget.XPOSED_MODULE_CONFIG -> moduleUtils.modulePreferences
            PreferenceEditorTarget.UI_THEME_PREFERENCES -> appContext.getSharedPreferences(
                target.prefsFileName, Context.MODE_PRIVATE
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun viaRemote(target: PreferenceEditorTarget): Boolean? = when (target) {
        PreferenceEditorTarget.XPOSED_MODULE_CONFIG -> moduleUtils.isUsingRemotePreferences
        PreferenceEditorTarget.UI_THEME_PREFERENCES -> null
    }

    private fun storageFailure(target: PreferenceEditorTarget): PreferenceEditOutcome =
        PreferenceEditOutcome.Failure(
            PreferenceEditorFailureReason.OTHER,
            "cannot open preference file \"${target.prefsFileName}\""
        )

    private enum class ThemeKeyType { STRING, BOOLEAN, LONG }

    companion object {
        /**
         * Shim registry of every theme key [com.qimian233.ztool.data.theme.ThemePreferencesRepository]
         * writes, mapped to its stored type. Temporary stand-in for a systematic
         * key-type registry; keep in sync when theme settings gain new fields.
         */
        private val THEME_KEY_TYPES: Map<String, ThemeKeyType> = mapOf(
            "frontend_style" to ThemeKeyType.STRING,
            "theme_mode" to ThemeKeyType.STRING,
            "material_color_spec" to ThemeKeyType.STRING,
            "material_palette" to ThemeKeyType.STRING,
            "material_palette_mode" to ThemeKeyType.STRING,
            "dynamic_color_enabled" to ThemeKeyType.BOOLEAN,
            "amoled_black_enabled" to ThemeKeyType.BOOLEAN,
            "use_alternative_icon" to ThemeKeyType.BOOLEAN,
            "predictive_back_gesture_enabled" to ThemeKeyType.BOOLEAN,
            "manual_color_enabled" to ThemeKeyType.BOOLEAN,
            "manual_seed_color" to ThemeKeyType.LONG,
            "enable_floating_bottom_bar" to ThemeKeyType.BOOLEAN,
            "enable_floating_bottom_bar_blur" to ThemeKeyType.BOOLEAN
        )
    }
}
