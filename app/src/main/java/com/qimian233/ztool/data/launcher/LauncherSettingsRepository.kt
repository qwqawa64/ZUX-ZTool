package com.qimian233.ztool.data.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.ForceStopMode
import com.qimian233.ztool.viewmodel.LauncherSettingsUiState
import androidx.core.content.edit

class LauncherSettingsRepository(
    private val context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): LauncherSettingsUiState {
        val disableForceStop = prefsUtils.loadBooleanSetting(KEY_DISABLE_FORCE_STOP, false)
        val whitelistEnabled = prefsUtils.loadBooleanSetting(KEY_FORCE_STOP_WHITE_LIST_ENABLE, false)
        val forceStopMode = when {
            disableForceStop && whitelistEnabled -> ForceStopMode.Whitelist
            disableForceStop -> ForceStopMode.AllApps
            else -> ForceStopMode.Default
        }

        return LauncherSettingsUiState(
            forceStopMode = forceStopMode,
            forceStopWhitelist = loadForceStopWhitelist(),
            moreBigDock = prefsUtils.loadBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT, false),
            customGridSize = prefsUtils.loadBooleanSetting(KEY_CUSTOM_GRID_SIZE, false),
            customGridRow = prefsUtils.loadIntegerSetting(KEY_CUSTOM_LAUNCHER_ROW, DEFAULT_ROW)
                .coerceIn(GRID_MIN, GRID_MAX),
            customGridColumn = prefsUtils.loadIntegerSetting(KEY_CUSTOM_LAUNCHER_COLUMN, DEFAULT_COLUMN)
                .coerceIn(GRID_MIN, GRID_MAX),
            cleanGlobalSearch = prefsUtils.loadBooleanSetting(KEY_CLEAN_GLOBAL_SEARCH, false),
            removeSearchRecommend = prefsUtils.loadBooleanSetting(KEY_REMOVE_HOT_WORD_IN_SEARCH_BOX, false),
            removeHotWordView = prefsUtils.loadBooleanSetting(KEY_REMOVE_HOT_WORD_VIEW, false),
            showRamInfo = prefsUtils.loadBooleanSetting(KEY_SHOW_RAM_INFO, false),
            beautifyRamInfo = prefsUtils.loadBooleanSetting(KEY_BEAUTIFY_RAM_INFO, false),
            disableDockBar = prefsUtils.loadBooleanSetting(KEY_DISABLE_DOCK_BAR, false),
            noLabelMode = prefsUtils.loadBooleanSetting(KEY_LAUNCHER_NO_LABEL_MODE, false),
            hideBluePoint = prefsUtils.loadBooleanSetting(KEY_LAUNCHER_HIDE_BLUE_POINT, false),
            cloudFolderDismiss = prefsUtils.loadBooleanSetting(KEY_CLOUD_FOLDER_DISMISS, false),
            disableRecentAppDisplay = prefsUtils.loadBooleanSetting(KEY_DISABLE_RECENT_APP_DISPLAY, false)
        )
    }

    fun saveForceStopMode(mode: ForceStopMode) {
        when (mode) {
            ForceStopMode.Default -> {
                prefsUtils.saveBooleanSetting(KEY_DISABLE_FORCE_STOP, false)
                prefsUtils.saveBooleanSetting(KEY_FORCE_STOP_WHITE_LIST_ENABLE, false)
            }
            ForceStopMode.AllApps -> {
                prefsUtils.saveBooleanSetting(KEY_DISABLE_FORCE_STOP, true)
                prefsUtils.saveBooleanSetting(KEY_FORCE_STOP_WHITE_LIST_ENABLE, false)
            }
            ForceStopMode.Whitelist -> {
                prefsUtils.saveBooleanSetting(KEY_DISABLE_FORCE_STOP, true)
                prefsUtils.saveBooleanSetting(KEY_FORCE_STOP_WHITE_LIST_ENABLE, true)
            }
        }
    }

    fun saveForceStopWhitelist(packageNames: List<String>) {
        prefsUtils.saveStringSetting(
            KEY_FORCE_STOP_WHITE_LIST,
            packageNames.joinToString(separator = ",", postfix = ",")
        )
    }

    fun saveMoreBigDock(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT, enabled)
    }

    fun saveCustomGridSize(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_GRID_SIZE, enabled)
    }

    fun saveGridValues(row: Int, column: Int) {
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_LAUNCHER_ROW, row.coerceIn(GRID_MIN, GRID_MAX))
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_LAUNCHER_COLUMN, column.coerceIn(GRID_MIN, GRID_MAX))
    }

    fun saveCleanSearch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLEAN_GLOBAL_SEARCH, enabled)
    }

    fun saveRemoveSearchRecommend(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_REMOVE_HOT_WORD_IN_SEARCH_BOX, enabled)
    }

    fun saveRemoveHotWordView(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_REMOVE_HOT_WORD_VIEW, enabled)
    }

    fun saveShowRamInfo(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SHOW_RAM_INFO, enabled)
    }

    fun saveBeautifyRamInfo(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_BEAUTIFY_RAM_INFO, enabled)
    }

    fun saveLauncherNoLabelMode(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LAUNCHER_NO_LABEL_MODE, enabled)
    }

    fun saveLauncherHideBluePoint(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LAUNCHER_HIDE_BLUE_POINT, enabled)
    }

    fun saveCloudFolderAutoDismiss(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLOUD_FOLDER_DISMISS, enabled)
    }

    fun saveDisableRecentAppDisplay(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_RECENT_APP_DISPLAY,enabled)
    }

    fun saveDisableDockBar(enabled: Boolean): Boolean {
        val previousMoreBigDock = prefsUtils.loadBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT, false)
        prefsUtils.saveBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP, previousMoreBigDock)
        if (enabled) {
            prefsUtils.saveBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT, false)
        } else {
            prefsUtils.saveBooleanSetting(
                KEY_ZUI_LAUNCHER_HOTSEAT,
                prefsUtils.loadBooleanSetting(KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP, false)
            )
            prefsUtils.modulePreferences.edit(commit = true) {
                remove(KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP)
            }
        }
        prefsUtils.saveBooleanSetting(KEY_DISABLE_DOCK_BAR, enabled)
        return enabled && !prefsUtils.loadBooleanSetting(KEY_DISABLE_DOCK_WARNING_CONFIRMED, false)
    }

    fun saveDisableDockWarningConfirmed() {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_DOCK_WARNING_CONFIRMED, true)
    }

    fun loadUserInstalledPackageNames(): List<String> {
        val packageManager = context.packageManager
        return packageManager.getInstalledPackages(0)
            .filter { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@filter false
                val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdatedSystemApp =
                    appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                !isSystemApp || isUpdatedSystemApp
            }
            .map { it.packageName }
    }

    fun forceStopPackage(): LauncherRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.Launcher)
        return when (val result = ScopeUtils.restartScope(scopes)) {
            is ScopeUtils.RestartResult.Success -> LauncherRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> LauncherRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> LauncherRestartResult.Failure(result.message)
        }
    }

    private fun loadForceStopWhitelist(): List<String> {
        return prefsUtils.loadStringSetting(KEY_FORCE_STOP_WHITE_LIST, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        const val GRID_MIN = 3
        const val GRID_MAX = 10
        private const val DEFAULT_ROW = 4
        private const val DEFAULT_COLUMN = 6

        private val KEY_DISABLE_FORCE_STOP = PreferenceKeys.DISABLE_FORCE_STOP.name
        private val KEY_FORCE_STOP_WHITE_LIST_ENABLE = PreferenceKeys.FORCE_STOP_WHITE_LIST_ENABLE.name
        private val KEY_FORCE_STOP_WHITE_LIST = PreferenceKeys.FORCE_STOP_WHITE_LIST.name
        private val KEY_ZUI_LAUNCHER_HOTSEAT = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name
        private val KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT_BACKUP.name
        private val KEY_CUSTOM_GRID_SIZE = PreferenceKeys.CUSTOM_GRID_SIZE.name
        private val KEY_CUSTOM_LAUNCHER_ROW = PreferenceKeys.CUSTOM_LAUNCHER_ROW.name
        private val KEY_CUSTOM_LAUNCHER_COLUMN = PreferenceKeys.CUSTOM_LAUNCHER_COLUMN.name
        private val KEY_CLEAN_GLOBAL_SEARCH = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name
        private val KEY_REMOVE_HOT_WORD_IN_SEARCH_BOX = PreferenceKeys.REMOVE_SEARCH_RECOMMEND.name
        private val KEY_REMOVE_HOT_WORD_VIEW = PreferenceKeys.REMOVE_HOT_WORD_VIEW.name
        private val KEY_SHOW_RAM_INFO = PreferenceKeys.LAUNCHER_RECENT_TASK_MEMORY_VIEW.name
        private val KEY_BEAUTIFY_RAM_INFO = PreferenceKeys.BEAUTIFY_RAM_INFO.name
        private val KEY_DISABLE_DOCK_BAR = PreferenceKeys.DISABLE_DOCK_BAR.name
        private val KEY_DISABLE_DOCK_WARNING_CONFIRMED = PreferenceKeys.DISABLE_DOCK_WARNING_CONFIRMED.name
        private val KEY_LAUNCHER_NO_LABEL_MODE = PreferenceKeys.LAUNCHER_NO_LABEL_MODE.name
        private val KEY_LAUNCHER_HIDE_BLUE_POINT = PreferenceKeys.LAUNCHER_HIDE_BLUE_POINT.name
        private val KEY_CLOUD_FOLDER_DISMISS = PreferenceKeys.DISMISS_CLOUD_FOLDER_CONFIRMATION.name
        private val KEY_DISABLE_RECENT_APP_DISPLAY = PreferenceKeys.DISABLE_RECENT_APPS_DISPLAY.name
    }
}

sealed interface LauncherRestartResult {
    data object Success : LauncherRestartResult
    data class Failure(val error: String) : LauncherRestartResult
}
