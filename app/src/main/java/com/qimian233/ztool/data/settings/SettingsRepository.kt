package com.qimian233.ztool.data.settings

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qimian233.ztool.BuildConfig
import com.qimian233.ztool.R
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.keys.LogLevel
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.utils.LogUtils
import com.qimian233.ztool.viewmodel.SettingsUiState

class SettingsRepository(
    private val context: Context
) {
    private val themePreferences = ThemePreferencesRepository(context)
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SettingsUiState {
        val themeSettings = themePreferences.loadSettings()
        return SettingsUiState(
            logLevel = LogLevel.fromPriority(
                prefsUtils.loadIntegerSetting(KEY_LOG_LEVEL, LogLevel.DEFAULT.priority)
            ),
            isEntryDisplayedInSettings = prefsUtils.loadBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, false),
            isShowAllAppsEnabled = prefsUtils.loadBooleanSetting(KEY_SHOW_ALL_APPS, false),
            isHideFromRecentsEnabled = prefsUtils.loadBooleanSetting(KEY_HIDE_FROM_RECENTS, false),
            isLauncherIconHidden = isLauncherIconHidden(),
            versionName = getVersionName(),
            commitCount = BuildConfig.GIT_COMMIT_COUNT,
            commitHash = BuildConfig.GIT_COMMIT_HASH,
            themeSettings = themeSettings,
            manualSeedColorText = formatSeedColor(themeSettings.manualSeedColor),
            isAutoCheckUpdateEnabled = prefsUtils.loadBooleanSetting(KEY_AUTO_CHECK_UPDATE, true),
        )
    }

    fun backupConfig(uri: Uri): Boolean {
        return FileManager.saveConfigWithSAF(
            context,
            uri,
            FileManager.generateBackupFileName(),
            buildBackupPayload()
        )
    }

    fun restoreConfig(uri: Uri): Boolean {
        val content = FileManager.readConfigWithSAF(context, uri) ?: return false
        Log.d(TAG, "Read config content: $content")
        restoreFromJson(content)
        return true
    }

    /**
     * Backup JSON layout (schemaVersion 1): a wrapper object holding the
     * module config (xposed_module_config) and the app-local theme settings
     * as two separate sections. Backups without the wrapper are legacy flat
     * module-config-only files and restore through the legacy path.
     */
    private fun buildBackupPayload(): String? {
        return try {
            val root = JsonObject()
            root.addProperty(KEY_SCHEMA_VERSION, BACKUP_SCHEMA_VERSION)
            root.add(
                KEY_MODULE_CONFIG,
                JsonParser.parseString(
                    ModulePreferencesUtils.getAllSettingsAsJSON(context) ?: "{}"
                )
            )
            root.add(KEY_THEME_SETTINGS, JsonParser.parseString(themePreferences.exportSettingsJson()))
            GsonBuilder().setPrettyPrinting().create().toJson(root)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build backup payload", e)
            null
        }
    }

    private fun restoreFromJson(content: String) {
        val root = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
        if (root != null && root.has(KEY_MODULE_CONFIG)) {
            root.get(KEY_MODULE_CONFIG)?.let {
                ModulePreferencesUtils.restoreConfig(context, it.toString())
            }
            root.get(KEY_THEME_SETTINGS)?.let {
                themePreferences.importSettingsJson(it.toString())
            }
            // The imported icon choice must be reflected in the launcher alias immediately.
            applyLauncherIconAliasState()
        } else {
            // Legacy flat backup: module config keys only, no theme section.
            ModulePreferencesUtils.restoreConfig(context, content)
        }
    }

    fun restoreDefaultConfig() {
        prefsUtils.clearAllSettings()
        themePreferences.deleteAll()
        applyLauncherIconAliasState()
    }

    fun setLogLevel(level: LogLevel) {
        prefsUtils.saveIntegerSetting(KEY_LOG_LEVEL, level.priority)
    }

    fun setEntryInSettingsEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, isEnabled)
    }

    fun setShowAllAppsEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SHOW_ALL_APPS, isEnabled)
    }

    fun setHideFromRecentsEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_HIDE_FROM_RECENTS, isEnabled)
        setRecentsExclusion(isEnabled)
    }

    /**
     * Re-apply the persisted recents-exclusion choice. A task launched from the
     * launcher icon is not excluded by default, so this must run on every app
     * start before the task is snapshotted into the overview.
     */
    fun applyHideFromRecents() {
        setRecentsExclusion(prefsUtils.loadBooleanSetting(KEY_HIDE_FROM_RECENTS, false))
    }

    private fun setRecentsExclusion(hidden: Boolean) {
        try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            activityManager.getAppTasks().forEach { task ->
                task.setExcludeFromRecents(hidden)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set recents exclusion: ${e.message}")
        }
    }

    fun setAutoCheckUpdateEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_AUTO_CHECK_UPDATE, enabled)
    }

    /**
     * Launcher icon visibility is stored as the enabled state of the manifest
     * activity-alias [.LAUNCHER_ALIAS_CLASS] — PackageManager persists it across
     * reboots and app updates, so no preference entry is needed.
     *
     * Two aliases exist: the default-icon one and [.LAUNCHER_ALIAS_ALT_CLASS]
     * (the "使用替代图标" artwork). Exactly one of them is enabled while the icon
     * is visible, so "hidden" means *both* are disabled.
     */
    fun isLauncherIconHidden(): Boolean {
        return !isAliasActive(launcherAliasComponent(), manifestDefault = true) &&
            !isAliasActive(launcherAliasAltComponent(), manifestDefault = false)
    }

    fun setLauncherIconHidden(hidden: Boolean) {
        if (hidden) {
            setAliasEnabled(launcherAliasComponent(), false)
            setAliasEnabled(launcherAliasAltComponent(), false)
        } else {
            applyLauncherIconAlias()
        }
        syncLeakCanaryAlias(hidden)
    }

    /**
     * Show exactly the alias matching the persisted icon choice (or hide both
     * when the launcher icon is hidden).
     */
    private fun applyLauncherIconAlias() {
        applyLauncherIconAlias(isLauncherIconHidden())
    }

    /**
     * Overload taking an already-read [isLauncherIconHidden] result. Every such
     * read is two PackageManager transactions, so callers that need the answer
     * anyway must not let this re-read it.
     */
    private fun applyLauncherIconAlias(hidden: Boolean) {
        if (hidden) {
            setAliasEnabled(launcherAliasComponent(), false)
            setAliasEnabled(launcherAliasAltComponent(), false)
            return
        }
        val useAlternativeIcon = themePreferences.loadSettings().useAlternativeIcon
        setAliasEnabled(launcherAliasComponent(), !useAlternativeIcon)
        setAliasEnabled(launcherAliasAltComponent(), useAlternativeIcon)
    }

    /**
     * Self-heal the alias pair on app start: component states survive app
     * updates and restore-from-backup can change the icon choice without the
     * toggle handler running, so re-assert the persisted selection.
     */
    fun applyLauncherIconAliasState() {
        if (isLauncherIconHidden()) {
            setAliasEnabled(launcherAliasComponent(), false)
            setAliasEnabled(launcherAliasAltComponent(), false)
        } else {
            applyLauncherIconAlias()
        }
    }

    private fun isAliasActive(component: ComponentName, manifestDefault: Boolean): Boolean {
        return try {
            when (context.packageManager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> manifestDefault
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read component state for ${component.className}: ${e.message}")
            manifestDefault
        }
    }

    private fun setAliasEnabled(component: ComponentName, enabled: Boolean) {
        setComponentState(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )
    }

    /**
     * Debug builds also declare LeakCanary's own launcher alias (the "Leaks" icon)
     * inside this package; leaving it enabled would keep the app visible after
     * hiding and let LSPosed's CATEGORY_LAUNCHER fallback resolve to LeakCanary.
     * On show, restore DEFAULT so LeakCanary's leak_canary_add_launcher_icon flag
     * regains control. The component only exists in debug builds, hence the
     * declaration check.
     */
    private fun syncLeakCanaryAlias(hidden: Boolean) {
        val leakCanaryAlias = ComponentName(context, LEAKCANARY_LAUNCHER_ALIAS_CLASS)
        if (!isComponentDeclared(leakCanaryAlias)) {
            return
        }
        setComponentState(
            leakCanaryAlias,
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        )
    }

    /**
     * One-pass launcher-alias self-heal for app start.
     *
     * Folds what used to be two back-to-back calls ([applyLauncherIconAliasState]
     * and [applyLeakCanaryAliasState]) into a single [isLauncherIconHidden] read:
     * each read costs two PackageManager transactions and both paths need the
     * same answer.
     *
     * - component states survive app updates and restore-from-backup can change
     *   the icon choice without the toggle handler running, so the persisted
     *   selection is re-asserted; and
     * - a hidden choice made on an older build may leave the debug-only LeakCanary
     *   alias (unknown to that build's toggle code) still enabled, so it is
     *   re-asserted whenever the icon is hidden.
     */
    fun healLauncherAliasState() {
        if (isLauncherIconHidden()) {
            setAliasEnabled(launcherAliasComponent(), false)
            setAliasEnabled(launcherAliasAltComponent(), false)
            syncLeakCanaryAlias(hidden = true)
            return
        }
        applyLauncherIconAlias(hidden = false)
    }

    private fun setComponentState(component: ComponentName, state: Int) {
        try {
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle component ${component.className}: ${e.message}")
        }
    }

    private fun isComponentDeclared(component: ComponentName): Boolean {
        return try {
            context.packageManager.getActivityInfo(
                component,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun launcherAliasComponent(): ComponentName {
        return ComponentName(context, LAUNCHER_ALIAS_CLASS)
    }

    private fun launcherAliasAltComponent(): ComponentName {
        return ComponentName(context, LAUNCHER_ALIAS_ALT_CLASS)
    }

    fun setFrontendStyle(style: FrontendStyle) {
        themePreferences.saveFrontendStyle(style)
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.saveThemeMode(mode)
    }

    fun setMaterialColorSpec(spec: MaterialColorSpec) {
        themePreferences.saveMaterialColorSpec(spec)
    }

    fun setMaterialPalette(palette: MaterialPalette) {
        themePreferences.saveMaterialPalette(palette)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        themePreferences.saveDynamicColorEnabled(enabled)
    }

    fun setAmoledBlackEnabled(enabled: Boolean) {
        themePreferences.saveAmoledBlackEnabled(enabled)
    }

    /**
     * Persist the alternative-icon choice and re-point the launcher alias at the
     * matching adaptive icon. The in-app logo follows the preference through
     * [com.qimian233.ztool.ui.theme.LocalUseAlternativeIcon].
     */
    fun setUseAlternativeIcon(enabled: Boolean) {
        themePreferences.saveUseAlternativeIcon(enabled)
        applyLauncherIconAlias()
    }

    fun setPredictiveBackGestureEnabled(enabled: Boolean) {
        themePreferences.savePredictiveBackGestureEnabled(enabled)
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        themePreferences.saveEnableFloatingBottomBar(enabled)
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        themePreferences.saveEnableFloatingBottomBarBlur(enabled)
    }

    fun setManualColorEnabled(enabled: Boolean) {
        themePreferences.saveManualColorEnabled(enabled)
    }

    fun setManualSeedColor(color: Long) {
        themePreferences.saveManualSeedColor(color)
    }
    fun backupFileName(): String = FileManager.generateBackupFileName()

    fun formatSeedColor(color: Long): String {
        return "#%08X".format(color)
    }

    fun parseSeedColor(input: String): Long? {
        val trimmed = input.trim()
        val normalized = when {
            trimmed.startsWith("#") -> trimmed.drop(1)
            trimmed.startsWith("0x", ignoreCase = true) -> trimmed.drop(2)
            else -> trimmed
        }
        if (normalized.length != 6 && normalized.length != 8) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val argb = if (normalized.length == 6) {
            "FF$normalized"
        } else {
            normalized
        }
        return argb.toLong(16)
    }

    fun exportFileName(): String = LogUtils.exportFileName()

    fun exportLogsToUri(uri: Uri): Boolean = LogUtils.exportLogsToUri(context, uri)

    fun cleanupAppLogsIfNeeded() = LogUtils.cleanupAppLogsIfNeeded(context)

    fun syncLsposedLogs() = LogUtils.syncLsposedLogs(context)

    fun deleteAllLogs() = LogUtils.deleteAllLogs(context)

    companion object {
        private const val TAG = "SettingsRepository"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val BACKUP_SCHEMA_VERSION = 1
        private const val KEY_MODULE_CONFIG = "moduleConfig"
        private const val KEY_THEME_SETTINGS = "themeSettings"
        private val KEY_LOG_LEVEL = PreferenceKeys.LOG_LEVEL.name
        private val KEY_DISPLAY_ENTRY_IN_SETTINGS = PreferenceKeys.ZTOOL_SETTINGS_ENTRY.name
        private val KEY_SHOW_ALL_APPS = PreferenceKeys.ZTOOL_SETTINGS_SHOW_ALL_APPS.name
        private val KEY_AUTO_CHECK_UPDATE = PreferenceKeys.AUTO_CHECK_UPDATE.name
        private val KEY_HIDE_FROM_RECENTS = PreferenceKeys.HIDE_FROM_RECENTS.name
        private const val LAUNCHER_ALIAS_CLASS = "com.qimian233.ztool.LauncherAlias"
        private const val LAUNCHER_ALIAS_ALT_CLASS = "com.qimian233.ztool.LauncherAliasAlt"
        // LeakCanary (debugImplementation) adds this launcher activity-alias to the
        // merged manifest; the class is @InternalApi so the name must be hardcoded.
        private const val LEAKCANARY_LAUNCHER_ALIAS_CLASS =
            "leakcanary.internal.activity.LeakLauncherActivity"
    }

    private fun getVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Unable to get module version: ${e.message}")
            context.getString(R.string.common_unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module status: ${e.message}")
            context.getString(R.string.common_unknown)
        }
    }
}

