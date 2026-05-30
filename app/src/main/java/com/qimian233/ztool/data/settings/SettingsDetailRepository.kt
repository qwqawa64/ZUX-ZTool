package com.qimian233.ztool.data.settings

import android.content.Context
import android.os.Build
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.utils.MagiskModuleManager
import com.qimian233.ztool.viewmodel.SettingsDetailUiState

class SettingsDetailRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
    private val magiskManager: MagiskModuleManager = MagiskModuleManager()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SettingsDetailUiState {
        return SettingsDetailUiState(
            removeBlacklist = prefsUtils.loadBooleanSetting(KEY_REMOVE_BLACKLIST, false),
            moduleEnabled = magiskManager.isModuleEnabled,
            floatMandatory = isForceResizableActivitiesEnabled(),
            splitScreenMandatory = prefsUtils.loadBooleanSetting(KEY_SPLIT_SCREEN_MANDATORY, false),
            allowDisableDolby = prefsUtils.loadBooleanSetting(KEY_ALLOW_DISPLAY_DOLBY, false),
            allowNativePermissionController = prefsUtils.loadBooleanSetting(KEY_PERMISSION_CONTROLLER_HOOK, false),
            alwaysDisplaySuggestions = prefsUtils.loadBooleanSetting(KEY_ALWAYS_DISPLAY_SUGGESTION, false),
            showZuiForceConfig = Build.VERSION.SDK_INT >= 36
        )
    }

    fun saveRemoveBlacklist(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_REMOVE_BLACKLIST, enabled)
    }

    fun saveForceResizableActivities(enabled: Boolean) {
        shellExecutor.executeCommand(
            "su -c settings put global force_resizable_activities " + if (enabled) "1" else "0"
        )
    }

    fun saveSplitScreenMandatory(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SPLIT_SCREEN_MANDATORY, enabled)
    }

    fun saveAllowNativePermissionController(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PERMISSION_CONTROLLER_HOOK, enabled)
    }

    fun saveAllowDisableDolby(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ALLOW_DISPLAY_DOLBY, enabled)
    }

    fun saveAlwaysDisplaySuggestions(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ALWAYS_DISPLAY_SUGGESTION, enabled)
    }

    fun forceStopScope(packageName: String) {
        if (packageName.isEmpty()) return
        shellExecutor.executeRootCommand("am force-stop $packageName")
        shellExecutor.executeRootCommand("am force-stop com.android.permissioncontroller")
        shellExecutor.executeRootCommand("am force-stop com.zui.safecenter")
    }

    private fun isForceResizableActivitiesEnabled(): Boolean {
        val result = shellExecutor.executeRootCommand("settings get global force_resizable_activities", 2)
        return result.isSuccess && result.output == "1"
    }

    companion object {
        private const val KEY_REMOVE_BLACKLIST = "remove_blacklist"
        private const val KEY_SPLIT_SCREEN_MANDATORY = "Split_Screen_mandatory"
        private const val KEY_ALLOW_DISPLAY_DOLBY = "allow_display_dolby"
        private const val KEY_PERMISSION_CONTROLLER_HOOK = "PermissionControllerHook"
        private const val KEY_ALWAYS_DISPLAY_SUGGESTION = "AlwaysDisplaySuggestion"
    }
}
