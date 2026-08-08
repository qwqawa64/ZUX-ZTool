package com.qimian233.ztool.data.gametool

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.GameToolSettingsUiState
import com.qimian233.ztool.viewmodel.MistakeTouchMode

class GameToolSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): GameToolSettingsUiState {
        val autoMistakeTouch = prefsUtils.loadBooleanSetting(KEY_AUTO_MISTAKE_TOUCH, false)
        val mistakeTouchWhiteList = prefsUtils.loadBooleanSetting(KEY_MISTAKE_TOUCH_WHITE_LIST, false)
        val mistakeTouchMode = when {
            autoMistakeTouch && mistakeTouchWhiteList -> MistakeTouchMode.Whitelist
            autoMistakeTouch -> MistakeTouchMode.AllGames
            else -> MistakeTouchMode.Default
        }

        return GameToolSettingsUiState(
            disableGameAudio = prefsUtils.loadBooleanSetting(KEY_DISABLE_GAME_AUDIO, false),
            disguiseDevice = prefsUtils.loadBooleanSetting(KEY_DISGUISE_DEVICE, false),
            fixCpuFrequency = prefsUtils.loadBooleanSetting(KEY_FIX_CPU_FREQUENCY, false),
            fixSocTemperature = prefsUtils.loadBooleanSetting(KEY_FIX_SOC_TEMPERATURE, false),
            mistakeTouchMode = mistakeTouchMode,
            targetGamePackages = loadWhitelistPackages()
        )
    }

    fun saveDisableGameAudio(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_GAME_AUDIO, enabled)
        prefsUtils.saveBooleanSetting(KEY_DISABLE_GAME_AUDIO_APP, enabled)
    }

    fun saveDisguiseDevice(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISGUISE_DEVICE, enabled)
    }

    fun saveFixCpuFrequency(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FIX_CPU_FREQUENCY, enabled)
    }

    fun saveFixSocTemperature(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FIX_SOC_TEMPERATURE, enabled)
    }

    fun saveMistakeTouchMode(mode: MistakeTouchMode) {
        when (mode) {
            MistakeTouchMode.Default -> {
                prefsUtils.saveBooleanSetting(KEY_AUTO_MISTAKE_TOUCH, false)
                prefsUtils.saveBooleanSetting(KEY_MISTAKE_TOUCH_WHITE_LIST, false)
            }
            MistakeTouchMode.AllGames -> {
                prefsUtils.saveBooleanSetting(KEY_AUTO_MISTAKE_TOUCH, true)
                prefsUtils.saveBooleanSetting(KEY_MISTAKE_TOUCH_WHITE_LIST, false)
            }
            MistakeTouchMode.Whitelist -> {
                prefsUtils.saveBooleanSetting(KEY_AUTO_MISTAKE_TOUCH, true)
                prefsUtils.saveBooleanSetting(KEY_MISTAKE_TOUCH_WHITE_LIST, true)
            }
        }
    }

    fun saveWhitelistPackages(packageNames: List<String>) {
        prefsUtils.saveStringSetting(
            KEY_MISTAKE_TOUCH_WHITE_LIST_GAME,
            packageNames.joinToString(separator = ",", postfix = ",")
        )
    }

    fun loadManagedGamePackages(): List<String> {
        val result = shellExecutor.executeRootCommand("ls /data/system_ce/0/managed_apps/")
        if (!result.isSuccess) {
            return emptyList()
        }

        return result.output
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    fun forceStopPackage(): GameToolRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.GameTool)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> GameToolRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> GameToolRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> GameToolRestartResult.Failure(result.message)
        }
    }

    private fun loadWhitelistPackages(): List<String> {
        return prefsUtils.loadStringSetting(KEY_MISTAKE_TOUCH_WHITE_LIST_GAME, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        private val KEY_DISABLE_GAME_AUDIO = PreferenceKeys.DISABLE_GAME_AUDIO.name
        private val KEY_DISABLE_GAME_AUDIO_APP = PreferenceKeys.DISABLE_GAME_AUDIO_APP.name
        private val KEY_DISGUISE_DEVICE = PreferenceKeys.DISGUISE_TB322FC.name
        private val KEY_FIX_CPU_FREQUENCY = PreferenceKeys.FIX_CPU_CLOCK.name
        private val KEY_FIX_SOC_TEMPERATURE = PreferenceKeys.FIX_SOC_TEMP.name
        private val KEY_AUTO_MISTAKE_TOUCH = PreferenceKeys.AUTO_MISTAKE_TOUCH.name
        private val KEY_MISTAKE_TOUCH_WHITE_LIST = PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST.name
        private val KEY_MISTAKE_TOUCH_WHITE_LIST_GAME = PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST_GAME.name
    }
}

sealed interface GameToolRestartResult {
    data object Success : GameToolRestartResult
    data class Failure(val error: String) : GameToolRestartResult
}
