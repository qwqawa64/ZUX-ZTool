package com.qimian233.ztool.data.freeform

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import com.qimian233.ztool.data.settings.OvConfigSelection
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import com.qimian233.ztool.viewmodel.FreeformHubUiState

/**
 * Aggregates the small-window settings owned by the settings, SystemUI, launcher and
 * framework repositories for the consolidated freeform hub.
 *
 * The hub only composes existing persistence; every key and default stays owned by
 * its original repository so hook-side reads are unaffected.
 */
class FreeformHubRepository(
    context: Context,
    private val settingsDetailRepository: SettingsDetailRepository = SettingsDetailRepository(context),
    private val systemUiMiscSettingsRepository: SystemUiMiscSettingsRepository =
        SystemUiMiscSettingsRepository(context),
    private val launcherSettingsRepository: LauncherSettingsRepository =
        LauncherSettingsRepository(context),
    private val frameworkSettingsRepository: FrameworkSettingsRepository =
        FrameworkSettingsRepository(context),
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    fun loadState(): FreeformHubUiState {
        val detail = settingsDetailRepository.loadState()
        val misc = systemUiMiscSettingsRepository.loadState()
        val launcher = launcherSettingsRepository.loadState()
        val framework = frameworkSettingsRepository.loadState()
        return FreeformHubUiState(
            removeBlacklist = detail.removeBlacklist,
            moduleEnabled = detail.moduleEnabled,
            showZuiForceConfig = detail.showZuiForceConfig,
            forceFreeformEntryHookEnabled = detail.forceFreeformEntryHookEnabled,
            edgeBubbleEnabled = detail.edgeBubbleEnabled,
            edgeBubbleTriggerPercent = detail.edgeBubbleTriggerPercent,
            floatMandatory = detail.floatMandatory,
            splitScreenMandatory = detail.splitScreenMandatory,
            freeformFullscreenThreshold = misc.freeformFullscreenThreshold,
            freeformFullscreenWidthPercent = misc.freeformFullscreenWidthPercent,
            freeformFullscreenHeightPercent = misc.freeformFullscreenHeightPercent,
            freeformBottomEdgeResize = misc.freeformBottomEdgeResize,
            cornerHandleAutoHide = misc.cornerHandleAutoHide,
            freeformNoForehead = misc.freeformNoForehead,
            forceFreeformEntry = launcher.forceFreeformEntry,
            freeformKeepAliveEnabled = launcher.freeformKeepAliveEnabled,
            freeformKeepAlivePackages = launcher.freeformKeepAlivePackages,
            forceRelativeAppFreeform = framework.forceRelativeAppFreeform
        )
    }

    fun saveRemoveBlacklist(enabled: Boolean) =
        settingsDetailRepository.saveRemoveBlacklist(enabled)

    fun isModuleEnabled(): Boolean = settingsDetailRepository.isModuleEnabled()

    fun setModuleEnabled(enabled: Boolean): String =
        settingsDetailRepository.setModuleEnabled(enabled)

    fun restoreOriginalModule(): String = settingsDetailRepository.restoreOriginalModule()

    fun loadFlashedConfigs(): HashSet<String> = settingsDetailRepository.loadFlashedConfigs()

    fun loadEmbeddingConfigFiles(): List<EmbeddingConfigManager.ConfigFileInfo> =
        settingsDetailRepository.loadEmbeddingConfigFiles()

    fun deleteEmbeddingConfigs(
        configs: List<EmbeddingConfigManager.ConfigFileInfo>,
        flashed: Set<String>
    ): Int = settingsDetailRepository.deleteEmbeddingConfigs(configs, flashed)

    fun flashEmbeddingConfigs(configs: List<EmbeddingConfigManager.ConfigFileInfo>) =
        settingsDetailRepository.flashEmbeddingConfigs(configs)

    fun loadOvConfigSelection(mode: Int): OvConfigSelection =
        settingsDetailRepository.loadOvConfigSelection(mode)

    fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedPackages: List<String>,
        mode: Int
    ): String = settingsDetailRepository.saveOvConfig(configMap, selectedPackages, mode)

    fun saveFloatMandatory(enabled: Boolean) =
        settingsDetailRepository.saveForceResizableActivities(enabled)

    fun saveSplitScreenMandatory(enabled: Boolean) =
        settingsDetailRepository.saveSplitScreenMandatory(enabled)

    fun saveEdgeBubbleEnabled(enabled: Boolean) =
        settingsDetailRepository.saveEdgeBubbleEnabled(enabled)

    fun saveEdgeBubbleTriggerPercent(percent: Int) =
        settingsDetailRepository.saveEdgeBubbleTriggerPercent(percent)

    fun saveFreeformFullscreenThreshold(enabled: Boolean) =
        systemUiMiscSettingsRepository.saveFreeformFullscreenThreshold(enabled)

    fun saveFreeformFullscreenWidthPercent(percent: Int) =
        systemUiMiscSettingsRepository.saveFreeformFullscreenWidthPercent(percent)

    fun saveFreeformFullscreenHeightPercent(percent: Int) =
        systemUiMiscSettingsRepository.saveFreeformFullscreenHeightPercent(percent)

    fun saveFreeformBottomEdgeResize(enabled: Boolean) =
        systemUiMiscSettingsRepository.saveFreeformBottomEdgeResize(enabled)

    fun saveCornerHandleAutoHide(enabled: Boolean) =
        systemUiMiscSettingsRepository.saveCornerHandleAutoHide(enabled)

    fun saveFreeformNoForehead(enabled: Boolean) =
        systemUiMiscSettingsRepository.saveFreeformNoForehead(enabled)

    fun saveForceFreeformEntry(enabled: Boolean) =
        launcherSettingsRepository.saveForceFreeformEntry(enabled)

    fun saveFreeformKeepAliveEnabled(enabled: Boolean) =
        launcherSettingsRepository.saveFreeformKeepAliveEnabled(enabled)

    fun saveFreeformKeepAlivePackages(packageNames: List<String>) =
        launcherSettingsRepository.saveFreeformKeepAlivePackages(packageNames)

    fun loadUserInstalledPackageNames(): List<String> =
        launcherSettingsRepository.loadUserInstalledPackageNames()

    fun saveForceRelativeAppFreeform(enabled: Boolean) =
        frameworkSettingsRepository.saveForceRelativeAppFreeform(enabled)

    /** Triggers a full reboot; required because the hub also touches framework scopes. */
    fun restartSystem(): EnhancedShellExecutor.ShellResult =
        shellExecutor.executeRootCommand("reboot", RESTART_TIMEOUT_SECONDS)

    private companion object {
        const val RESTART_TIMEOUT_SECONDS = 5
    }
}
