package com.qimian233.ztool.hook.base

import com.qimian233.ztool.hook.modules.documentsui.DocumentsUIBypass
import com.qimian233.ztool.hook.modules.gametool.AutoMistakeTouchHook
import com.qimian233.ztool.hook.modules.gametool.CpuFrequencyFix
import com.qimian233.ztool.hook.modules.gametool.DeviceModelDisguiseHook
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudioApp
import com.qimian233.ztool.hook.modules.gametool.SocTemperatureFix
import com.qimian233.ztool.hook.modules.launcher.LauncherFreeformEntryHook
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableDockBar
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableRecentAppsDisplay
import com.qimian233.ztool.hook.modules.launcher.dockbar.ZuiLauncherHotseatHook
import com.qimian233.ztool.hook.modules.launcher.grid.BluePointRemovalHook
import com.qimian233.ztool.hook.modules.launcher.grid.BigFolderAlignHook
import com.qimian233.ztool.hook.modules.launcher.grid.CustomGridSize
import com.qimian233.ztool.hook.modules.launcher.grid.DismissCloudFolderConfirmation
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherDrawerNoLabelMode
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherNoLabelMode
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherWideGridHook
import com.qimian233.ztool.hook.modules.launcher.grid.IconScaleOverrideHook
import com.qimian233.ztool.hook.modules.launcher.misc.BatchUninstall
import com.qimian233.ztool.hook.modules.launcher.misc.CleanGlobalSearch
import com.qimian233.ztool.hook.modules.launcher.misc.DisableForceStop
import com.qimian233.ztool.hook.modules.launcher.LauncherAppIconUnmaskHook
import com.qimian233.ztool.hook.modules.launcher.misc.RecentTaskMemoryViewHook
import com.qimian233.ztool.hook.modules.mobiledesktop.AutoAcceptFileTransferHook
import com.qimian233.ztool.hook.modules.mobiledesktop.BypassShareWarningHook
import com.qimian233.ztool.hook.modules.mobiledesktop.DisableNearbyShareAutoOffHook
import com.qimian233.ztool.hook.modules.ota.BlockOtaInstallDialog
import com.qimian233.ztool.hook.modules.ota.DisableOtaCheck
import com.qimian233.ztool.hook.modules.ota.HideOtaNotifications
import com.qimian233.ztool.hook.modules.tbengine.LenovoOTAHook
import com.qimian233.ztool.hook.modules.ota.NoAutoOtaInstall
import com.qimian233.ztool.hook.modules.packageinstaller.DisableInstallerAdvertisement
import com.qimian233.ztool.hook.modules.pp.BlockGamePolicyUpdate
import com.qimian233.ztool.hook.modules.pp.BlockPowerPolicySync
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerHookScan
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerNoDeleteModule
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerPermissionHook
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerStyleHook
import com.qimian233.ztool.hook.modules.packageinstaller.SkipInstallWarnPage
import com.qimian233.ztool.hook.modules.safecenter.DisableAllVirusScans
import com.qimian233.ztool.hook.modules.safecenter.EnableAutorunByDefault
import com.qimian233.ztool.hook.modules.setting.AllowDisplayDolbyHook
import com.qimian233.ztool.hook.modules.tbengine.DisableTbEngineAppUpdate
import com.qimian233.ztool.hook.modules.tbengine.DisableTbEngineAutoDownload
import com.qimian233.ztool.hook.modules.tbengine.DisableTbEngineAutoInstall
import com.qimian233.ztool.hook.modules.tbengine.DisableTbEnginePush
import com.qimian233.ztool.hook.modules.tbengine.DisableTbEngineReporting
import com.qimian233.ztool.hook.modules.tbengine.SignTbEngineLocalOta
import com.qimian233.ztool.hook.modules.setting.AppInfoHeaderDetailsHook
import com.qimian233.ztool.hook.modules.setting.CustomizeAboutDeviceInfo
import com.qimian233.ztool.hook.modules.setting.HideOtaUpdateHint
import com.qimian233.ztool.hook.modules.setting.LocaleListEditorHook
import com.qimian233.ztool.hook.modules.setting.OneVisionCompletion
import com.qimian233.ztool.hook.modules.setting.OwnerInfoSettingsHook
import com.qimian233.ztool.hook.modules.setting.PermissionControllerHook
import com.qimian233.ztool.hook.modules.setting.SettingsAppIconUnmaskHook
import com.qimian233.ztool.hook.modules.setting.SplitScreenMandatory as SettingSplitScreenMandatory
import com.qimian233.ztool.hook.modules.setting.ZToolSettingsEntryHook
import com.qimian233.ztool.hook.modules.sogouime.HalfWidthPunctHook
import com.qimian233.ztool.hook.modules.systemframework.AiInputExpand
import com.qimian233.ztool.hook.modules.systemframework.AllowGetPackages
import com.qimian233.ztool.hook.modules.systemframework.AllowRelativeAppLaunch
import com.qimian233.ztool.hook.modules.systemframework.AllowUntrustedTouch
import com.qimian233.ztool.hook.modules.systemframework.DisableFlagSecure
import com.qimian233.ztool.hook.modules.systemframework.DisableGameAudio
import com.qimian233.ztool.hook.modules.systemframework.DisableHbmThermalLimit
import com.qimian233.ztool.hook.modules.systemframework.ForceRelativeAppFreeform
import com.qimian233.ztool.hook.modules.systemframework.ForceScreenOnOffAnimation
import com.qimian233.ztool.hook.modules.systemframework.KeepRotation
import com.qimian233.ztool.hook.modules.systemframework.OwnerInfoSystemHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerArscBypassHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerDigestBypassHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerDowngradeHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerExactSigMatchBypassHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerHiddenApiHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerSharedUserBypassHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerSignatureBypassHook
import com.qimian233.ztool.hook.modules.systemframework.PackageManagerVerificationAgentHook
import com.qimian233.ztool.hook.modules.systemframework.SplitScreenMandatory as SystemSplitScreenMandatory
import com.qimian233.ztool.hook.modules.systemui.keyguard.ChargeAnimationDurationHook
import com.qimian233.ztool.hook.modules.systemui.keyguard.BypassFaceAuthTimeout
import com.qimian233.ztool.hook.modules.systemui.keyguard.LockScreenClockColorHook
import com.qimian233.ztool.hook.modules.systemui.keyguard.ForceLenovoAOD
import com.qimian233.ztool.hook.modules.systemui.keyguard.ForceNativeAod
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIChargeWattsHook
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIRealWatts
import com.qimian233.ztool.hook.modules.systemui.misc.AospScrollCaptureHook
import com.qimian233.ztool.hook.modules.systemui.misc.CustomChargeAnimation
import com.qimian233.ztool.hook.modules.systemui.misc.CustomControlCenterDate
import com.qimian233.ztool.hook.modules.systemui.misc.DisableBiometricErrorVibration
import com.qimian233.ztool.hook.modules.systemui.misc.ForceImmersiveMode
import com.qimian233.ztool.hook.modules.systemui.misc.GuestModeController
import com.qimian233.ztool.hook.modules.systemui.misc.NoChargeAnimation
import com.qimian233.ztool.hook.modules.systemui.misc.NotificationCenterTransparency
import com.qimian233.ztool.hook.modules.systemui.misc.ShadeReboundFix
import com.qimian233.ztool.hook.modules.systemui.qs.BrightnessSliderPercentageHook
import com.qimian233.ztool.hook.modules.systemui.qs.ControlCenterNoTileLabelsHook
import com.qimian233.ztool.hook.modules.systemui.qs.MediaOutputDialogCenterHook
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsColor
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsRoundCorner
import com.qimian233.ztool.hook.modules.systemui.qs.QsPanelWidthHook
import com.qimian233.ztool.hook.modules.systemui.qs.ControlCenterLongPressHook
import com.qimian233.ztool.hook.modules.systemui.qs.HideDetailIndicatorHook
import com.qimian233.ztool.hook.modules.systemui.qs.SliderStyleHook
import com.qimian233.ztool.hook.modules.systemui.qs.VolumeSliderLongPressHook
import com.qimian233.ztool.hook.modules.systemui.qs.VolumeSliderPercentageHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.CustomStatusBarClock
import com.qimian233.ztool.hook.modules.systemui.statusbar.NativeNotificationIcon
import com.qimian233.ztool.hook.modules.systemui.statusbar.NetworkSpeedHideSlowHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.NetworkSpeedRefresh
import com.qimian233.ztool.hook.modules.systemui.statusbar.NotificationIconHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.StatusBarClockSecondsHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUIBatteryHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUINetworkSpeedSizeHook
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUINetworkSpeeddoublelayerHook
import com.qimian233.ztool.hook.modules.systemui.wallpaper.DesktopLiveWallpaperHook
import com.qimian233.ztool.hook.modules.wallpaper.ChargeAnimationFixModule

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Hook module manager (libxposed version, Kotlin).
 * <p>
 * Modules are split by process type into systemServerModules and appModules,
 * dispatched by [com.qimian233.ztool.hook.HookInit] in the corresponding
 * lifecycle callbacks.
 * </p>
 */
object HookManager {

    private val hookModules: MutableList<BaseHookModule> = ArrayList()
    private var initialized = false

    // Hot reload: cache lifecycle params captured on first load, for replay after hot reload
    private val savedPackageParams: MutableList<XposedModuleInterface.PackageLoadedParam> =
            ArrayList()
    private var savedSystemServerParam: XposedModuleInterface.SystemServerStartingParam? = null

    fun initialize(xposed: XposedInterface) {
        if (initialized) return
        registerAllModules(xposed)
    }

    /**
     * Registers all Hook modules and injects the XposedInterface.
     * Shared by [initialize] and [reinitializeForHotReload].
     */
    private fun registerAllModules(xposed: XposedInterface) {
        registerHookModule(DisableFlagSecure())
        registerHookModule(AllowGetPackages())
        registerHookModule(AllowUntrustedTouch())
        registerHookModule(ForceScreenOnOffAnimation())
        registerHookModule(AiInputExpand())
        registerHookModule(HalfWidthPunctHook())
        registerHookModule(KeepRotation())
        registerHookModule(AllowRelativeAppLaunch())
        registerHookModule(ForceRelativeAppFreeform())
        registerHookModule(DisableHbmThermalLimit())
        registerHookModule(SystemSplitScreenMandatory())

        registerHookModule(PackageManagerDowngradeHook())
        registerHookModule(PackageManagerSignatureBypassHook())
        registerHookModule(PackageManagerVerificationAgentHook())
        registerHookModule(PackageManagerDigestBypassHook())
        registerHookModule(PackageManagerExactSigMatchBypassHook())
        registerHookModule(PackageManagerSharedUserBypassHook())
        registerHookModule(PackageManagerHiddenApiHook())
        registerHookModule(PackageManagerArscBypassHook())

        registerHookModule(StatusBarClockSecondsHook())
        registerHookModule(CustomStatusBarClock())
        registerHookModule(SystemUIChargeWattsHook())
        registerHookModule(SystemUIRealWatts())
        registerHookModule(NotificationIconHook())
        registerHookModule(CustomControlCenterDate())
        registerHookModule(ControlCenterNoTileLabelsHook())
        registerHookModule(NoChargeAnimation())
        registerHookModule(NativeNotificationIcon())
        registerHookModule(SystemUINetworkSpeedSizeHook())
        registerHookModule(SystemUINetworkSpeeddoublelayerHook())
        registerHookModule(NetworkSpeedRefresh())
        registerHookModule(NetworkSpeedHideSlowHook())
        registerHookModule(SystemUIBatteryHook())
        registerHookModule(ForceImmersiveMode())
        registerHookModule(AospScrollCaptureHook())
        registerHookModule(ShadeReboundFix())
        registerHookModule(ChargeAnimationDurationHook())
        registerHookModule(ForceLenovoAOD())
        registerHookModule(ForceNativeAod())
        registerHookModule(BypassFaceAuthTimeout())
        registerHookModule(LockScreenClockColorHook())
        registerHookModule(CustomQsRoundCorner())
        registerHookModule(BrightnessSliderPercentageHook())
        registerHookModule(VolumeSliderPercentageHook())
        registerHookModule(ControlCenterLongPressHook())
        registerHookModule(VolumeSliderLongPressHook())
        registerHookModule(HideDetailIndicatorHook())
        registerHookModule(CustomQsColor())
        registerHookModule(NotificationCenterTransparency())
        registerHookModule(GuestModeController())
        registerHookModule(QsPanelWidthHook())
        registerHookModule(SliderStyleHook())
        registerHookModule(CustomChargeAnimation())
        registerHookModule(DisableBiometricErrorVibration())
        registerHookModule(MediaOutputDialogCenterHook())

        registerHookModule(DesktopLiveWallpaperHook())

        registerHookModule(OneVisionCompletion())
        registerHookModule(AllowDisplayDolbyHook())
        registerHookModule(PermissionControllerHook())
        registerHookModule(OwnerInfoSettingsHook())
        registerHookModule(OwnerInfoSystemHook())
        registerHookModule(SettingSplitScreenMandatory())
        registerHookModule(AppInfoHeaderDetailsHook())
        registerHookModule(CustomizeAboutDeviceInfo())
        registerHookModule(ZToolSettingsEntryHook())
        registerHookModule(HideOtaUpdateHint())
        registerHookModule(LocaleListEditorHook())
        registerHookModule(SettingsAppIconUnmaskHook())

        registerHookModule(PackageInstallerHookScan())
        registerHookModule(PackageInstallerPermissionHook())
        registerHookModule(SkipInstallWarnPage())
        registerHookModule(DisableInstallerAdvertisement())
        registerHookModule(PackageInstallerStyleHook())
        registerHookModule(PackageInstallerNoDeleteModule())

        registerHookModule(DisableForceStop())
        registerHookModule(ZuiLauncherHotseatHook())
        registerHookModule(LauncherFreeformEntryHook())
        registerHookModule(LauncherAppIconUnmaskHook())
        registerHookModule(CustomGridSize())
        registerHookModule(CleanGlobalSearch())
        registerHookModule(DisableDockBar())
        registerHookModule(RecentTaskMemoryViewHook())
        registerHookModule(LauncherNoLabelMode())
        registerHookModule(LauncherDrawerNoLabelMode())
        registerHookModule(BluePointRemovalHook())
        registerHookModule(DismissCloudFolderConfirmation())
        registerHookModule(BigFolderAlignHook())
        registerHookModule(DisableRecentAppsDisplay())
        registerHookModule(BatchUninstall())
        registerHookModule(LauncherWideGridHook())
        registerHookModule(IconScaleOverrideHook())

        registerHookModule(AutoMistakeTouchHook())
        registerHookModule(DisableGameAudio())
        registerHookModule(DisableGameAudioApp())
        registerHookModule(DeviceModelDisguiseHook())
        registerHookModule(CpuFrequencyFix())
        registerHookModule(SocTemperatureFix())

        registerHookModule(DisableOtaCheck())
        registerHookModule(LenovoOTAHook())
        registerHookModule(NoAutoOtaInstall())
        registerHookModule(BlockOtaInstallDialog())
        registerHookModule(HideOtaNotifications())

        registerHookModule(ChargeAnimationFixModule())

        registerHookModule(DocumentsUIBypass())

        registerHookModule(DisableAllVirusScans())
        registerHookModule(EnableAutorunByDefault())

        registerHookModule(AutoAcceptFileTransferHook())
        registerHookModule(BypassShareWarningHook())
        registerHookModule(DisableNearbyShareAutoOffHook())

        registerHookModule(DisableTbEngineAutoDownload())
        registerHookModule(DisableTbEngineAutoInstall())
        registerHookModule(DisableTbEngineAppUpdate())
        registerHookModule(DisableTbEnginePush())
        registerHookModule(DisableTbEngineReporting())
        registerHookModule(SignTbEngineLocalOta())

        registerHookModule(BlockPowerPolicySync())
        registerHookModule(BlockGamePolicyUpdate())

        // Inject the XposedInterface
        for (module in hookModules) {
            module.setXposedInterface(xposed)
        }

        initialized = true
    }

    fun registerHookModule(module: BaseHookModule) {
        if (!hookModules.contains(module)) {
            hookModules.add(module)
        }
    }

    fun handlePackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        savedPackageParams.add(param)
        for (module in hookModules) {
            module.safeHandleLoadPackage(param)
        }
    }

    fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        savedSystemServerParam = param
        for (module in hookModules) {
            module.safeHandleSystemServerStarting(param)
        }
    }

    /**
     * Returns the saved package-load lifecycle params (passed from old code to new code on hot reload).
     * <p>
     * Hot reload creates a new generation of module code (new classloader);
     * static fields are not shared across generations, so these params must be
     * passed explicitly by the old code via
     * [XposedModuleInterface.HotReloadingParam.setSavedInstanceState] in
     * [onHotReloading][XposedModuleInterface.HotReloadingParam], then restored
     * by the new code via [restoreLifecycleParams] in
     * [onHotReloaded][XposedModuleInterface.HotReloadedParam].
     * </p>
     */
    fun getSavedPackageParams(): List<XposedModuleInterface.PackageLoadedParam> =
            savedPackageParams

    /**
     * Returns the saved system server start param (passed from old code to new code on hot reload).
     *
     * @see getSavedPackageParams
     */
    fun getSavedSystemServerParam(): XposedModuleInterface.SystemServerStartingParam? =
            savedSystemServerParam

    /**
     * Restores the lifecycle params passed from the previous code generation, for use by [replayAllHooks].
     * <p>
     * Must be called in [onHotReloaded][XposedModuleInterface.HotReloadedParam] (new code)
     * after hot reload, before calling [replayAllHooks]; otherwise under the new
     * classloader [savedPackageParams] / [savedSystemServerParam] are empty and
     * the replay will install no hooks.
     * </p>
     */
    fun restoreLifecycleParams(
        packageParams: List<XposedModuleInterface.PackageLoadedParam>?,
        systemServerParam: XposedModuleInterface.SystemServerStartingParam?
    ) {
        savedPackageParams.clear()
        if (packageParams != null) {
            savedPackageParams.addAll(packageParams)
        }
        savedSystemServerParam = systemServerParam
    }

    /**
     * Re-initializes after hot reload: clears the old module list and re-registers all modules with the new XposedInterface.
     * <p>
     * Lifecycle params ([savedPackageParams] / [savedSystemServerParam]) are
     * passed by the old code via savedInstanceState in
     * [onHotReloading][XposedModuleInterface.HotReloadingParam]; the new code
     * must call [restoreLifecycleParams] first to restore them, then replay.
     * </p>
     */
    fun reinitializeForHotReload(xposed: XposedInterface) {
        hookModules.clear()
        registerAllModules(xposed)
    }

    /**
     * Replays the saved lifecycle params after hot reload, letting new modules reinstall their hooks.
     * <p>
     * [restoreLifecycleParams] must be called first to restore the params passed
     * from the old code. Each module call is wrapped in try-catch; a single
     * module's failure does not affect the others.
     * </p>
     */
    fun replayAllHooks() {
        for (module in hookModules) {
            val systemServer = savedSystemServerParam
            if (systemServer != null) {
                try {
                    module.safeHandleSystemServerStarting(systemServer)
                } catch (_: Throwable) {
                }
            }
            for (param in savedPackageParams) {
                try {
                    module.safeHandleLoadPackage(param)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
