package com.qimian233.ztool.data.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.ForceStopMode
import com.qimian233.ztool.viewmodel.LauncherSettingsUiState

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
            prefsUtils.getModulePreferences().edit()
                .remove(KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP)
                .commit()
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

    fun forceStopPackage(packageName: String): LauncherRestartResult {
        if (packageName.isEmpty()) {
            return LauncherRestartResult.EmptyPackageName
        }

        return try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            process.waitFor()
            LauncherRestartResult.Success
        } catch (e: Exception) {
            LauncherRestartResult.Failure(e.message.orEmpty())
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

        private const val KEY_DISABLE_FORCE_STOP = "disable_force_stop"
        private const val KEY_FORCE_STOP_WHITE_LIST_ENABLE = "ForceStopWhiteListEnable"
        private const val KEY_FORCE_STOP_WHITE_LIST = "ForceStopWhiteList"
        private const val KEY_ZUI_LAUNCHER_HOTSEAT = "zui_launcher_hotseat"
        private const val KEY_ZUI_LAUNCHER_HOTSEAT_BACKUP = "zui_launcher_hotseat_backup"
        private const val KEY_CUSTOM_GRID_SIZE = "CustomGridSize"
        private const val KEY_CUSTOM_LAUNCHER_ROW = "CustomLauncherRow"
        private const val KEY_CUSTOM_LAUNCHER_COLUMN = "CustomLauncherColumn"
        private const val KEY_CLEAN_GLOBAL_SEARCH = "clean_global_search"
        private const val KEY_REMOVE_HOT_WORD_IN_SEARCH_BOX = "remove_search_recommend"
        private const val KEY_REMOVE_HOT_WORD_VIEW = "remove_hot_word_view"
        private const val KEY_SHOW_RAM_INFO = "launcher_recent_task_memory_view"
        private const val KEY_BEAUTIFY_RAM_INFO = "beautify_ram_info"
        private const val KEY_DISABLE_DOCK_BAR = "disable_dock_bar"
        private const val KEY_DISABLE_DOCK_WARNING_CONFIRMED = "disable_dock_warning_confirmed"
    }
}

sealed interface LauncherRestartResult {
    data object Success : LauncherRestartResult
    data object EmptyPackageName : LauncherRestartResult
    data class Failure(val error: String) : LauncherRestartResult
}
