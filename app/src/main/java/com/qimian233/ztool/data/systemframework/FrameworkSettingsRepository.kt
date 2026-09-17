package com.qimian233.ztool.data.systemframework

import android.content.Context
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.keys.PreferenceKeys
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
            allowUntrustedTouch = prefsUtils.loadBooleanSetting(ALLOW_UNTRUSTED_TOUCH, false),
            allowRelativeAppLaunch = prefsUtils.loadBooleanSetting(ALLOW_RELATIVE_APP_LAUNCH, false),
            forceRelativeAppFreeform = prefsUtils.loadBooleanSetting(FORCE_RELATIVE_APP_FREEFORM, false),
            disableHbmThermalLimit = prefsUtils.loadBooleanSetting(KEY_DISABLE_HBM_THERMAL_LIMIT, false),
            pkgMgrAllowDowngrade = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_ALLOW_DOWNGRADE, false),
            pkgMgrBypassVerification = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_BYPASS_VERIFICATION, false),
            pkgMgrDisableVerificationAgent = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_DISABLE_VERIFICATION_AGENT, false),
            pkgMgrBypassDigest = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_BYPASS_DIGEST, false),
            pkgMgrUsePreviousSignatures = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_USE_PREVIOUS_SIGNATURES, false),
            pkgMgrBypassExactSigMatch = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_BYPASS_EXACT_SIG_MATCH, false),
            pkgMgrBypassSharedUser = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_BYPASS_SHARED_USER, false),
            pkgMgrAllowHiddenApisSystemApps = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_ALLOW_HIDDEN_APIS_SYSTEM_APPS, false),
            pkgMgrBypassArscRestriction = prefsUtils.loadBooleanSetting(KEY_PKG_MGR_BYPASS_ARSC_RESTRICTION, false),
            aiInputSigns = aiInputSigns,
            aiInputSignsError = validateAiInputSigns(aiInputSigns),
            halfWidthPunct = prefsUtils.loadBooleanSetting(KEY_HALF_WIDTH_PUNCT, false),
            halfWidthPunctSigns = prefsUtils.loadStringSetting(
                KEY_HALF_WIDTH_PUNCT_SIGNS, DEFAULT_HALF_WIDTH_PUNCT_SIGNS
            ),
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

    fun saveHalfWidthPunct(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_HALF_WIDTH_PUNCT, enabled)
    }

    fun saveHalfWidthPunctSigns(value: String) {
        prefsUtils.saveStringSetting(KEY_HALF_WIDTH_PUNCT_SIGNS, value)
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

    fun saveAllowUntrustedTouch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(ALLOW_UNTRUSTED_TOUCH, enabled)
    }

    fun saveAllowRelativeAppLaunch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(ALLOW_RELATIVE_APP_LAUNCH, enabled)
    }

    fun saveForceRelativeAppFreeform(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(FORCE_RELATIVE_APP_FREEFORM, enabled)
    }

    fun saveDisableHbmThermalLimit(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_HBM_THERMAL_LIMIT, enabled)
    }

    fun savePkgMgrAllowDowngrade(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_ALLOW_DOWNGRADE, enabled)
    }

    fun savePkgMgrBypassVerification(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_BYPASS_VERIFICATION, enabled)
    }

    fun savePkgMgrDisableVerificationAgent(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_DISABLE_VERIFICATION_AGENT, enabled)
    }

    fun savePkgMgrBypassDigest(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_BYPASS_DIGEST, enabled)
    }

    fun savePkgMgrUsePreviousSignatures(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_USE_PREVIOUS_SIGNATURES, enabled)
    }

    fun savePkgMgrBypassExactSigMatch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_BYPASS_EXACT_SIG_MATCH, enabled)
    }

    fun savePkgMgrBypassSharedUser(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_BYPASS_SHARED_USER, enabled)
    }

    fun savePkgMgrAllowHiddenApisSystemApps(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_ALLOW_HIDDEN_APIS_SYSTEM_APPS, enabled)
    }

    fun savePkgMgrBypassArscRestriction(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PKG_MGR_BYPASS_ARSC_RESTRICTION, enabled)
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
        if (input.contains("\uFF0C")) return context.getString(R.string.system_framework_custom_detector_err)
        return if (input.split(",").any { it.trim().isEmpty() }) {
            context.getString(R.string.system_framework_custom_detector_err)
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
        private val KEY_HALF_WIDTH_PUNCT = PreferenceKeys.HALF_WIDTH_PUNCT.name
        private val KEY_HALF_WIDTH_PUNCT_SIGNS = PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.name
        private val DEFAULT_HALF_WIDTH_PUNCT_SIGNS = PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default
        private val KEY_FORCE_ON_OFF_ANIMATION = PreferenceKeys.FORCE_SCREEN_ON_OFF_ANIMATION.name
        private val KEY_SCREEN_ON_OFF_ANIMATION_DURATION = PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS.name
        private const val SCREEN_ON_OFF_ANIMATION_MIN_MS = 0
        private const val SCREEN_ON_OFF_ANIMATION_MAX_MS = 1000
        private const val SCREEN_ON_OFF_ANIMATION_STEP_MS = 50
        private val ALLOW_UNTRUSTED_TOUCH = PreferenceKeys.ALLOW_UNTRUSTED_TOUCH.name
        private val ALLOW_RELATIVE_APP_LAUNCH = PreferenceKeys.ALLOW_RELATIVE_APP_LAUNCH.name
        private val FORCE_RELATIVE_APP_FREEFORM = PreferenceKeys.FORCE_RELATIVE_APP_FREEFORM.name
        private val KEY_DISABLE_HBM_THERMAL_LIMIT = PreferenceKeys.DISABLE_HBM_THERMAL_LIMIT.name

        private val KEY_PKG_MGR_ALLOW_DOWNGRADE = PreferenceKeys.PKG_MGR_ALLOW_DOWNGRADE.name
        private val KEY_PKG_MGR_BYPASS_VERIFICATION = PreferenceKeys.PKG_MGR_BYPASS_VERIFICATION.name
        private val KEY_PKG_MGR_DISABLE_VERIFICATION_AGENT =
            PreferenceKeys.PKG_MGR_DISABLE_VERIFICATION_AGENT.name
        private val KEY_PKG_MGR_BYPASS_DIGEST = PreferenceKeys.PKG_MGR_BYPASS_DIGEST.name
        private val KEY_PKG_MGR_USE_PREVIOUS_SIGNATURES =
            PreferenceKeys.PKG_MGR_USE_PREVIOUS_SIGNATURES.name
        private val KEY_PKG_MGR_BYPASS_EXACT_SIG_MATCH =
            PreferenceKeys.PKG_MGR_BYPASS_EXACT_SIG_MATCH.name
        private val KEY_PKG_MGR_BYPASS_SHARED_USER = PreferenceKeys.PKG_MGR_BYPASS_SHARED_USER.name
        private val KEY_PKG_MGR_ALLOW_HIDDEN_APIS_SYSTEM_APPS =
            PreferenceKeys.PKG_MGR_ALLOW_HIDDEN_APIS_SYSTEM_APPS.name
        private val KEY_PKG_MGR_BYPASS_ARSC_RESTRICTION =
            PreferenceKeys.PKG_MGR_BYPASS_ARSC_RESTRICTION.name
    }
}

data class RestartSystemResult(
    val success: Boolean,
    val error: String
)
