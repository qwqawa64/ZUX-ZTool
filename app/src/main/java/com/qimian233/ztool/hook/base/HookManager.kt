package com.qimian233.ztool.hook.base

import com.qimian233.ztool.hook.modules.documentsui.DocumentsUIBypass
import com.qimian233.ztool.hook.modules.gametool.AutoMistakeTouchHook
import com.qimian233.ztool.hook.modules.gametool.CpuFrequencyFix
import com.qimian233.ztool.hook.modules.gametool.DeviceModelDisguiseHook
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudioApp
import com.qimian233.ztool.hook.modules.gametool.SocTemperatureFix
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableDockBar
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableRecentAppsDisplay
import com.qimian233.ztool.hook.modules.launcher.dockbar.ZuiLauncherHotseatHook
import com.qimian233.ztool.hook.modules.launcher.grid.BluePointRemovalHook
import com.qimian233.ztool.hook.modules.launcher.grid.CustomGridSize
import com.qimian233.ztool.hook.modules.launcher.grid.DismissCloudFolderConfirmation
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherDrawerNoLabelMode
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherNoLabelMode
import com.qimian233.ztool.hook.modules.launcher.misc.CleanGlobalSearch
import com.qimian233.ztool.hook.modules.launcher.misc.DisableForceStop
import com.qimian233.ztool.hook.modules.launcher.misc.RecentTaskMemoryViewHook
import com.qimian233.ztool.hook.modules.mobiledesktop.AutoAcceptFileTransferHook
import com.qimian233.ztool.hook.modules.mobiledesktop.BypassShareWarningHook
import com.qimian233.ztool.hook.modules.mobiledesktop.DisableNearbyShareAutoOffHook
import com.qimian233.ztool.hook.modules.ota.BlockOtaInstallDialog
import com.qimian233.ztool.hook.modules.ota.DisableOtaCheck
import com.qimian233.ztool.hook.modules.ota.HideOtaNotifications
import com.qimian233.ztool.hook.modules.ota.LenovoOTAHook
import com.qimian233.ztool.hook.modules.ota.NoAutoOtaInstall
import com.qimian233.ztool.hook.modules.packageinstaller.DisableInstallerAdvertisement
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerHookScan
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerNoDeleteModule
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerPermissionHook
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerStyleHook
import com.qimian233.ztool.hook.modules.packageinstaller.SkipInstallWarnPage
import com.qimian233.ztool.hook.modules.safecenter.DisableAllVirusScans
import com.qimian233.ztool.hook.modules.safecenter.EnableAutorunByDefault
import com.qimian233.ztool.hook.modules.setting.AllowDisplayDolbyHook
import com.qimian233.ztool.hook.modules.setting.AppInfoHeaderDetailsHook
import com.qimian233.ztool.hook.modules.setting.CustomizeAboutDeviceInfo
import com.qimian233.ztool.hook.modules.setting.HideOtaUpdateHint
import com.qimian233.ztool.hook.modules.setting.LocaleListEditorHook
import com.qimian233.ztool.hook.modules.setting.OneVisionCompletion
import com.qimian233.ztool.hook.modules.setting.OwnerInfoSettingsHook
import com.qimian233.ztool.hook.modules.setting.PermissionControllerHook
import com.qimian233.ztool.hook.modules.setting.SplitScreenMandatory as SettingSplitScreenMandatory
import com.qimian233.ztool.hook.modules.setting.ZToolSettingsEntryHook
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
import com.qimian233.ztool.hook.modules.systemframework.NoMorePasswordPer24H
import com.qimian233.ztool.hook.modules.systemframework.SplitScreenMandatory as SystemSplitScreenMandatory
import com.qimian233.ztool.hook.modules.systemui.keyguard.ForceLenovoAOD
import com.qimian233.ztool.hook.modules.systemui.keyguard.ForceNativeAod
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIChargeWattsHook
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIRealWatts
import com.qimian233.ztool.hook.modules.systemui.misc.CustomChargeAnimation
import com.qimian233.ztool.hook.modules.systemui.misc.CustomControlCenterDate
import com.qimian233.ztool.hook.modules.systemui.misc.DisableBiometricErrorVibration
import com.qimian233.ztool.hook.modules.systemui.misc.ForceImmersiveMode
import com.qimian233.ztool.hook.modules.systemui.misc.GuestModeController
import com.qimian233.ztool.hook.modules.systemui.misc.NoChargeAnimation
import com.qimian233.ztool.hook.modules.systemui.misc.NotificationCenterTransparency
import com.qimian233.ztool.hook.modules.systemui.qs.BrightnessSliderPercentageHook
import com.qimian233.ztool.hook.modules.systemui.qs.ControlCenterNoTileLabelsHook
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsColor
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsRoundCorner
import com.qimian233.ztool.hook.modules.systemui.qs.QsPanelWidthHook
import com.qimian233.ztool.hook.modules.systemui.qs.SliderStyleHook
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
 * Hook 模块管理器（libxposed 版，Kotlin）。
 * <p>
 * 按进程类型将模块分为 systemServerModules 和 appModules，
 * 由 [com.qimian233.ztool.hook.HookInit] 在对应的生命周期回调中调度。
 * </p>
 */
object HookManager {

    private val hookModules: MutableList<BaseHookModule> = ArrayList()
    private var initialized = false

    // 热重载：缓存首次加载时的生命周期参数，用于热重载后回放
    private val savedPackageParams: MutableList<XposedModuleInterface.PackageLoadedParam> =
            ArrayList()
    private var savedSystemServerParam: XposedModuleInterface.SystemServerStartingParam? = null

    @JvmStatic
    fun initialize(xposed: XposedInterface) {
        if (initialized) return
        registerAllModules(xposed)
    }

    /**
     * 注册全部 Hook 模块并注入 XposedInterface。
     * 由 [initialize] 和 [reinitializeForHotReload] 共用。
     */
    private fun registerAllModules(xposed: XposedInterface) {
        // ── 系统框架 (target: system — 由 onSystemServerStarting 调度) ──
        registerHookModule(DisableFlagSecure())
        registerHookModule(NoMorePasswordPer24H())
        registerHookModule(AllowGetPackages())
        registerHookModule(AllowUntrustedTouch())
        registerHookModule(ForceScreenOnOffAnimation())
        registerHookModule(AiInputExpand())
        registerHookModule(KeepRotation())
        registerHookModule(AllowRelativeAppLaunch())
        registerHookModule(ForceRelativeAppFreeform())
        registerHookModule(DisableHbmThermalLimit())
        registerHookModule(SystemSplitScreenMandatory()) // 看看 setting 包的注册模块你就知道这一行为什么要这么写了

        // ── SystemUI (target: com.android.systemui) ──
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
        registerHookModule(ForceLenovoAOD())
        registerHookModule(ForceNativeAod())
        registerHookModule(CustomQsRoundCorner())
        registerHookModule(BrightnessSliderPercentageHook())
        registerHookModule(VolumeSliderPercentageHook())
        registerHookModule(CustomQsColor())
        registerHookModule(NotificationCenterTransparency())
        registerHookModule(GuestModeController())
        registerHookModule(QsPanelWidthHook())
        registerHookModule(SliderStyleHook())
        registerHookModule(CustomChargeAnimation())
        registerHookModule(DisableBiometricErrorVibration())

        // ── Desktop Live Wallpaper PoC (test_hook) ──
        registerHookModule(DesktopLiveWallpaperHook())

        // ── Settings (target: com.android.settings) ──
        registerHookModule(OneVisionCompletion())
        registerHookModule(AllowDisplayDolbyHook())
        registerHookModule(PermissionControllerHook())
        registerHookModule(OwnerInfoSettingsHook())
        registerHookModule(OwnerInfoSystemHook())
        registerHookModule(SettingSplitScreenMandatory()) // 你别笑，为了防止重名冲突必须用全限定名
        registerHookModule(AppInfoHeaderDetailsHook())
        registerHookModule(CustomizeAboutDeviceInfo())
        registerHookModule(ZToolSettingsEntryHook())
        registerHookModule(HideOtaUpdateHint())
        registerHookModule(LocaleListEditorHook()) // test_hook: 拦截 LenovoUtils 区域判断

        // ── PackageInstaller (target: com.android.packageinstaller) ──
        registerHookModule(PackageInstallerHookScan())
        registerHookModule(PackageInstallerPermissionHook())
        registerHookModule(SkipInstallWarnPage())
        registerHookModule(DisableInstallerAdvertisement())
        registerHookModule(PackageInstallerStyleHook())
        registerHookModule(PackageInstallerNoDeleteModule())

        // ── Launcher (target: com.zui.launcher) ──
        registerHookModule(DisableForceStop())
        registerHookModule(ZuiLauncherHotseatHook())
        registerHookModule(CustomGridSize())
        registerHookModule(CleanGlobalSearch())
        registerHookModule(DisableDockBar())
        registerHookModule(RecentTaskMemoryViewHook())
        registerHookModule(LauncherNoLabelMode())
        registerHookModule(LauncherDrawerNoLabelMode())
        registerHookModule(BluePointRemovalHook())
        registerHookModule(DismissCloudFolderConfirmation())
        registerHookModule(DisableRecentAppsDisplay())

        // ── GameTool (target: com.zui.game.service) ──
        registerHookModule(AutoMistakeTouchHook())
        registerHookModule(DisableGameAudio())
        registerHookModule(DisableGameAudioApp())
        registerHookModule(DeviceModelDisguiseHook())
        registerHookModule(CpuFrequencyFix())
        registerHookModule(SocTemperatureFix())

        // ── OTA (target: com.lenovo.ota) ──
        registerHookModule(DisableOtaCheck())
        registerHookModule(LenovoOTAHook())
        registerHookModule(NoAutoOtaInstall())
        registerHookModule(BlockOtaInstallDialog())
        registerHookModule(HideOtaNotifications())

        // ── Wallpaper (target: com.zui.wallpapersetting) ──
        registerHookModule(ChargeAnimationFixModule())

        // ── DocumentsUI (target: com.android.documentsui) ──
        registerHookModule(DocumentsUIBypass())

        // ── SafeCenter (target: com.zui.safecenter) ──
        registerHookModule(DisableAllVirusScans())
        registerHookModule(EnableAutorunByDefault())

        // ── MobileDesktop (target: com.motorola.mobiledesktop) ──
        registerHookModule(AutoAcceptFileTransferHook())
        registerHookModule(BypassShareWarningHook())
        registerHookModule(DisableNearbyShareAutoOffHook())

        // 注入 XposedInterface
        for (module in hookModules) {
            module.setXposedInterface(xposed)
        }

        initialized = true
    }

    @JvmStatic
    fun registerHookModule(module: BaseHookModule) {
        if (!hookModules.contains(module)) {
            hookModules.add(module)
        }
    }

    @JvmStatic
    fun handlePackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        savedPackageParams.add(param)
        for (module in hookModules) {
            module.safeHandleLoadPackage(param)
        }
    }

    @JvmStatic
    fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        savedSystemServerParam = param
        for (module in hookModules) {
            module.safeHandleSystemServerStarting(param)
        }
    }

    // ── 热重载支持 ─────────────────────────────────────────────

    /**
     * 获取已保存的包加载生命周期参数（热重载时由旧代码传给新代码）。
     * <p>
     * 热重载会创建新一代模块代码（新 classloader），静态字段不跨代共享，
     * 因此这些参数必须由旧代码在 [onHotReloading][XposedModuleInterface.HotReloadingParam] 中通过
     * [XposedModuleInterface.HotReloadingParam.setSavedInstanceState] 显式传递，
     * 再由新代码在 [onHotReloaded][XposedModuleInterface.HotReloadedParam] 中经 [restoreLifecycleParams] 恢复。
     * </p>
     */
    @JvmStatic
    fun getSavedPackageParams(): List<XposedModuleInterface.PackageLoadedParam> =
            savedPackageParams

    /**
     * 获取已保存的系统服务器启动参数（热重载时由旧代码传给新代码）。
     *
     * @see getSavedPackageParams
     */
    @JvmStatic
    fun getSavedSystemServerParam(): XposedModuleInterface.SystemServerStartingParam? =
            savedSystemServerParam

    /**
     * 恢复上一代代码传递过来的生命周期参数，供 [replayAllHooks] 重放使用。
     * <p>
     * 必须在热重载后的 [onHotReloaded][XposedModuleInterface.HotReloadedParam]（新代码）中、
     * 调用 [replayAllHooks] 之前执行；否则新 classloader 下 [savedPackageParams] /
     * [savedSystemServerParam] 为空，重放将不会安装任何 Hook。
     * </p>
     */
    @JvmStatic
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
     * 热重载后重新初始化：清空旧模块列表，用新的 XposedInterface 重新注册全部模块。
     * <p>
     * 生命周期参数（[savedPackageParams] / [savedSystemServerParam]）
     * 由旧代码在 [onHotReloading][XposedModuleInterface.HotReloadingParam] 中经 savedInstanceState 传递，
     * 新代码需先调用 [restoreLifecycleParams] 恢复，再执行重放。
     * </p>
     */
    @JvmStatic
    fun reinitializeForHotReload(xposed: XposedInterface) {
        hookModules.clear()
        registerAllModules(xposed)
    }

    /**
     * 热重载后回放已保存的生命周期参数，让新模块重新安装 Hook。
     * <p>
     * 重放前必须先调用 [restoreLifecycleParams] 恢复旧代码传递的参数。
     * 每个模块调用由 try-catch 包裹，单个模块失败不影响其他模块。
     * </p>
     */
    @JvmStatic
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
