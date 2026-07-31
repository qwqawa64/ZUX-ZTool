package com.qimian233.ztool.data.systemframework

import android.content.Context
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.viewmodel.FrameworkSettingsUiState

class FrameworkSettingsRepository(
    private val context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): FrameworkSettingsUiState {
        val aiInputSigns = prefsUtils.loadStringSetting(KEY_AI_INPUT_EXPAND_SIGNS, "")
        return FrameworkSettingsUiState(
            allowGetPackages = prefsUtils.loadBooleanSetting(KEY_ALLOW_GET_PACKAGES, false),
            keepRotation = prefsUtils.loadBooleanSetting(KEY_KEEP_ROTATION, false),
            disableFlagSecure = prefsUtils.loadBooleanSetting(KEY_DISABLE_FLAG_SECURE, false),
            aiInputExpand = prefsUtils.loadBooleanSetting(KEY_AI_INPUT_EXPAND, false),
            forceOnOffAnimation = prefsUtils.loadBooleanSetting(KEY_FORCE_ON_OFF_ANIMATION, false),
            forceOnOffAnimationDuration = normalizeScreenOnOffAnimationDuration(
                prefsUtils.loadIntegerSetting(KEY_SCREEN_ON_OFF_ANIMATION_DURATION, 400)
            ),
            noPasswordPer24H = prefsUtils.loadBooleanSetting(NO_PASSWORD_PER_24H, false),
            allowUntrustedTouch = prefsUtils.loadBooleanSetting(ALLOW_UNTRUSTED_TOUCH, false),
            allowRelativeAppLaunch = prefsUtils.loadBooleanSetting(ALLOW_RELATIVE_APP_LAUNCH, false),
            aiInputSigns = aiInputSigns,
            aiInputSignsError = validateAiInputSigns(aiInputSigns),
        )
    }

    fun saveKeepRotation(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_KEEP_ROTATION, enabled)
    }

    fun saveAllowGetPackages(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ALLOW_GET_PACKAGES, enabled)
    }

    fun saveDisableFlagSecure(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_FLAG_SECURE, enabled)
    }

    fun saveAiInputExpand(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_AI_INPUT_EXPAND, enabled)
    }

    fun saveAiInputSigns(value: String) {
        prefsUtils.saveStringSetting(KEY_AI_INPUT_EXPAND_SIGNS, value)
    }

    fun saveForceScreenOnOffAnimation (value: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FORCE_ON_OFF_ANIMATION, value)
    }

    fun saveScreenOnOffAnimationDuration(value: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_SCREEN_ON_OFF_ANIMATION_DURATION,
            normalizeScreenOnOffAnimationDuration(value)
        )
    }

    fun saveNoPasswordPer24H(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(NO_PASSWORD_PER_24H, enabled)
    }

    fun saveAllowUntrustedTouch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(ALLOW_UNTRUSTED_TOUCH, enabled)
    }

    fun saveAllowRelativeAppLaunch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(ALLOW_RELATIVE_APP_LAUNCH, enabled)
    }

    fun normalizeScreenOnOffAnimationDuration(value: Int): Int {
        val clampedValue = value.coerceIn(
            SCREEN_ON_OFF_ANIMATION_MIN_MS,
            SCREEN_ON_OFF_ANIMATION_MAX_MS
        )
        return ((clampedValue + SCREEN_ON_OFF_ANIMATION_STEP_MS / 2) /
            SCREEN_ON_OFF_ANIMATION_STEP_MS) * SCREEN_ON_OFF_ANIMATION_STEP_MS
    }

    fun validateAiInputSigns(input: String): String? {
        if (input.isEmpty()) return null
        if (input.contains("\uFF0C")) return context.getString(R.string.custom_detector_err)
        return if (input.split(",").any { it.trim().isEmpty() }) {
            context.getString(R.string.custom_detector_err)
        } else {
            null
        }
    }

    fun restartSystem(): RestartSystemResult {
        return try {
            val process = Runtime.getRuntime().exec("su -c reboot")
            process.waitFor()
            RestartSystemResult(success = true, error = "")
        } catch (e: Exception) {
            RestartSystemResult(success = false, error = e.message.orEmpty())
        }
    }

    companion object {
        private val KEY_KEEP_ROTATION = PreferenceKeys.KEEP_ROTATION.name
        private val KEY_ALLOW_GET_PACKAGES = PreferenceKeys.ALLOW_GET_PACKAGES.name
        private val KEY_DISABLE_FLAG_SECURE = PreferenceKeys.DISABLE_FLAG_SECURE.name
        private val KEY_AI_INPUT_EXPAND = PreferenceKeys.AI_INPUT_EXPAND.name
        private val KEY_AI_INPUT_EXPAND_SIGNS = PreferenceKeys.AI_INPUT_EXPAND_SIGNS.name
        private val KEY_FORCE_ON_OFF_ANIMATION = PreferenceKeys.FORCE_SCREEN_ON_OFF_ANIMATION.name
        private val KEY_SCREEN_ON_OFF_ANIMATION_DURATION = PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS.name
        private const val SCREEN_ON_OFF_ANIMATION_MIN_MS = 0
        private const val SCREEN_ON_OFF_ANIMATION_MAX_MS = 1000
        private const val SCREEN_ON_OFF_ANIMATION_STEP_MS = 50
        private val NO_PASSWORD_PER_24H = PreferenceKeys.NO_MORE_PASSWORD_PER_24H.name
        private val ALLOW_UNTRUSTED_TOUCH = PreferenceKeys.ALLOW_UNTRUSTED_TOUCH.name
        private val ALLOW_RELATIVE_APP_LAUNCH = PreferenceKeys.ALLOW_RELATIVE_APP_LAUNCH.name
    }
}

data class RestartSystemResult(
    val success: Boolean,
    val error: String
)
