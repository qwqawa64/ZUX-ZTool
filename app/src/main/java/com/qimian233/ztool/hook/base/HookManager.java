package com.qimian233.ztool.hook.base;

import com.qimian233.ztool.hook.modules.documentsui.DocumentsUIBypass;
import com.qimian233.ztool.hook.modules.gametool.AutoMistakeTouchHook;
import com.qimian233.ztool.hook.modules.gametool.CpuFrequencyFix;
import com.qimian233.ztool.hook.modules.gametool.DeviceModelDisguiseHook;
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudio;
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudioApp;
import com.qimian233.ztool.hook.modules.gametool.SocTemperatureFix;
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableDockBar;
import com.qimian233.ztool.hook.modules.launcher.dockbar.DisableRecentAppsDisplay;
import com.qimian233.ztool.hook.modules.launcher.dockbar.ZuiLauncherHotseatHook;
import com.qimian233.ztool.hook.modules.launcher.grid.BluePointRemovalHook;
import com.qimian233.ztool.hook.modules.launcher.grid.CustomGridSize;
import com.qimian233.ztool.hook.modules.launcher.grid.DismissCloudFolderConfirmation;
import com.qimian233.ztool.hook.modules.launcher.grid.LauncherNoLabelMode;
import com.qimian233.ztool.hook.modules.launcher.misc.CleanGlobalSearch;
import com.qimian233.ztool.hook.modules.launcher.misc.DisableForceStop;
import com.qimian233.ztool.hook.modules.launcher.misc.RecentTaskMemoryViewHook;
import com.qimian233.ztool.hook.modules.mobiledesktop.AutoAcceptFileTransferHook;
import com.qimian233.ztool.hook.modules.mobiledesktop.BypassShareWarningHook;
import com.qimian233.ztool.hook.modules.mobiledesktop.DisableNearbyShareAutoOffHook;
import com.qimian233.ztool.hook.modules.ota.BlockOtaInstallDialog;
import com.qimian233.ztool.hook.modules.ota.DisableOtaCheck;
import com.qimian233.ztool.hook.modules.ota.HideOtaNotifications;
import com.qimian233.ztool.hook.modules.ota.LenovoOTAHook;
import com.qimian233.ztool.hook.modules.ota.NoAutoOtaInstall;
import com.qimian233.ztool.hook.modules.packageinstaller.DisableInstallerAdvertisement;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerHookScan;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerNoDeleteModule;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerPermissionHook;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerStyleHook;
import com.qimian233.ztool.hook.modules.packageinstaller.SkipInstallWarnPage;
import com.qimian233.ztool.hook.modules.safecenter.DisableAllVirusScans;
import com.qimian233.ztool.hook.modules.safecenter.EnableAutorunByDefault;
import com.qimian233.ztool.hook.modules.setting.AllowDisplayDolbyHook;
import com.qimian233.ztool.hook.modules.setting.AppInfoHeaderDetailsHook;
import com.qimian233.ztool.hook.modules.setting.CustomizeAboutDeviceInfo;
import com.qimian233.ztool.hook.modules.setting.HideOtaUpdateHint;
import com.qimian233.ztool.hook.modules.setting.OneVisionCompletion;
import com.qimian233.ztool.hook.modules.setting.OwnerInfoHook;
import com.qimian233.ztool.hook.modules.setting.PermissionControllerHook;
import com.qimian233.ztool.hook.modules.setting.SplitScreenMandatory;
import com.qimian233.ztool.hook.modules.setting.ZToolSettingsEntryHook;
import com.qimian233.ztool.hook.modules.systemframework.AiInputExpand;
import com.qimian233.ztool.hook.modules.systemframework.AllowGetPackages;
import com.qimian233.ztool.hook.modules.systemframework.AllowRelativeAppLaunch;
import com.qimian233.ztool.hook.modules.systemframework.AllowUntrustedTouch;
import com.qimian233.ztool.hook.modules.systemframework.DisableFlagSecure;
import com.qimian233.ztool.hook.modules.systemframework.ForceScreenOnOffAnimation;
import com.qimian233.ztool.hook.modules.systemframework.ForceRelativeAppFreeform;
import com.qimian233.ztool.hook.modules.systemframework.KeepRotation;
import com.qimian233.ztool.hook.modules.systemframework.NoMorePasswordPer24H;
import com.qimian233.ztool.hook.modules.systemui.keyguard.ForceLenovoAOD;
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIChargeWattsHook;
import com.qimian233.ztool.hook.modules.systemui.keyguard.SystemUIRealWatts;
import com.qimian233.ztool.hook.modules.systemui.misc.CustomChargeAnimation;
import com.qimian233.ztool.hook.modules.systemui.misc.CustomControlCenterDate;
import com.qimian233.ztool.hook.modules.systemui.misc.DisableBiometricErrorVibration;
import com.qimian233.ztool.hook.modules.systemui.misc.ForceImmersiveMode;
import com.qimian233.ztool.hook.modules.systemui.misc.GuestModeController;
import com.qimian233.ztool.hook.modules.systemui.misc.NoChargeAnimation;
import com.qimian233.ztool.hook.modules.systemui.misc.NotificationCenterTransparency;
import com.qimian233.ztool.hook.modules.systemui.qs.BrightnessSliderPercentageHook;
import com.qimian233.ztool.hook.modules.systemui.qs.ControlCenterNoTileLabelsHook;
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsColor;
import com.qimian233.ztool.hook.modules.systemui.qs.CustomQsRoundCorner;
import com.qimian233.ztool.hook.modules.systemui.qs.QsPanelWidthHook;
import com.qimian233.ztool.hook.modules.systemui.qs.SliderStyleHook;
import com.qimian233.ztool.hook.modules.systemui.qs.VolumeSliderPercentageHook;
import com.qimian233.ztool.hook.modules.systemui.statusbar.CustomStatusBarClock;
import com.qimian233.ztool.hook.modules.systemui.statusbar.NativeNotificationIcon;
import com.qimian233.ztool.hook.modules.systemui.statusbar.NetworkSpeedRefresh;
import com.qimian233.ztool.hook.modules.systemui.statusbar.NotificationIconHook;
import com.qimian233.ztool.hook.modules.systemui.statusbar.StatusBarClockSecondsHook;
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUIBatteryHook;
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUINetworkSpeedSizeHook;
import com.qimian233.ztool.hook.modules.systemui.statusbar.SystemUINetworkSpeeddoublelayerHook;
import com.qimian233.ztool.hook.modules.wallpaper.ChargeAnimationFixModule;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Hook 模块管理器（libxposed 版）。
 * <p>
 * 按进程类型将模块分为 systemServerModules 和 appModules，
 * 由 {@link com.qimian233.ztool.hook.HookInit} 在对应的生命周期回调中调度。
 */
public class HookManager {

    private static final List<BaseHookModule> hookModules = new ArrayList<>();
    private static boolean initialized = false;

    // 热重载：缓存首次加载时的生命周期参数，用于热重载后回放
    private static final List<XposedModuleInterface.PackageLoadedParam> savedPackageParams =
            new ArrayList<>();
    private static XposedModuleInterface.SystemServerStartingParam savedSystemServerParam = null;

    public static void initialize(XposedInterface xposed) {
        if (initialized) return;
        registerAllModules(xposed);
    }

    /**
     * 注册全部 Hook 模块并注入 XposedInterface。
     * 由 {@link #initialize} 和 {@link #reinitializeForHotReload} 共用。
     */
    private static void registerAllModules(XposedInterface xposed) {
        // ── 系统框架 (target: system — 由 onSystemServerStarting 调度) ──
        registerHookModule(new DisableFlagSecure());
        registerHookModule(new NoMorePasswordPer24H());
        registerHookModule(new AllowGetPackages());
        registerHookModule(new AllowUntrustedTouch());
        registerHookModule(new ForceScreenOnOffAnimation());
        registerHookModule(new AiInputExpand());
        registerHookModule(new KeepRotation());
        registerHookModule(new AllowRelativeAppLaunch());
        registerHookModule(new ForceRelativeAppFreeform());

        // ── SystemUI (target: com.android.systemui) ──
        registerHookModule(new StatusBarClockSecondsHook());
        registerHookModule(new CustomStatusBarClock());
        registerHookModule(new SystemUIChargeWattsHook());
        registerHookModule(new SystemUIRealWatts());
        registerHookModule(new NotificationIconHook());
        registerHookModule(new CustomControlCenterDate());
        registerHookModule(new ControlCenterNoTileLabelsHook());
        registerHookModule(new NoChargeAnimation());
        registerHookModule(new NativeNotificationIcon());
        registerHookModule(new SystemUINetworkSpeedSizeHook());
        registerHookModule(new SystemUINetworkSpeeddoublelayerHook());
        registerHookModule(new NetworkSpeedRefresh());
        registerHookModule(new SystemUIBatteryHook());
        registerHookModule(new ForceImmersiveMode());
        registerHookModule(new ForceLenovoAOD());
        registerHookModule(new CustomQsRoundCorner());
        registerHookModule(new BrightnessSliderPercentageHook());
        registerHookModule(new VolumeSliderPercentageHook());
        registerHookModule(new CustomQsColor());
        registerHookModule(new NotificationCenterTransparency());
        registerHookModule(new GuestModeController());
        registerHookModule(new QsPanelWidthHook());
        registerHookModule(new SliderStyleHook());
        registerHookModule(new CustomChargeAnimation());
        registerHookModule(new DisableBiometricErrorVibration());

        // ── Settings (target: com.android.settings) ──
        registerHookModule(new OneVisionCompletion());
        registerHookModule(new AllowDisplayDolbyHook());
        registerHookModule(new PermissionControllerHook());
        registerHookModule(new OwnerInfoHook());
        registerHookModule(new SplitScreenMandatory());
        registerHookModule(new AppInfoHeaderDetailsHook());
        registerHookModule(new CustomizeAboutDeviceInfo());
        registerHookModule(new ZToolSettingsEntryHook());
        registerHookModule(new HideOtaUpdateHint());

        // ── PackageInstaller (target: com.android.packageinstaller) ──
        registerHookModule(new PackageInstallerHookScan());
        registerHookModule(new PackageInstallerPermissionHook());
        registerHookModule(new SkipInstallWarnPage());
        registerHookModule(new DisableInstallerAdvertisement());
        registerHookModule(new PackageInstallerStyleHook());
        registerHookModule(new PackageInstallerNoDeleteModule());

        // ── Launcher (target: com.zui.launcher) ──
        registerHookModule(new DisableForceStop());
        registerHookModule(new ZuiLauncherHotseatHook());
        registerHookModule(new CustomGridSize());
        registerHookModule(new CleanGlobalSearch());
        registerHookModule(new DisableDockBar());
        registerHookModule(new RecentTaskMemoryViewHook());
        registerHookModule(new LauncherNoLabelMode());
        registerHookModule(new BluePointRemovalHook());
        registerHookModule(new DismissCloudFolderConfirmation());
        registerHookModule(new DisableRecentAppsDisplay());

        // ── GameTool (target: com.zui.game.service) ──
        registerHookModule(new AutoMistakeTouchHook());
        registerHookModule(new DisableGameAudio());
        registerHookModule(new DisableGameAudioApp());
        registerHookModule(new DeviceModelDisguiseHook());
        registerHookModule(new CpuFrequencyFix());
        registerHookModule(new SocTemperatureFix());

        // ── OTA (target: com.lenovo.ota) ──
        registerHookModule(new DisableOtaCheck());
        registerHookModule(new LenovoOTAHook());
        registerHookModule(new NoAutoOtaInstall());
        registerHookModule(new BlockOtaInstallDialog());
        registerHookModule(new HideOtaNotifications());

        // ── Wallpaper (target: com.zui.wallpapersetting) ──
        registerHookModule(new ChargeAnimationFixModule());

        // ── DocumentsUI (target: com.android.documentsui) ──
        registerHookModule(new DocumentsUIBypass());

        // ── SafeCenter (target: com.zui.safecenter) ──
        registerHookModule(new DisableAllVirusScans());
        registerHookModule(new EnableAutorunByDefault());

        // ── MobileDesktop (target: com.motorola.mobiledesktop) ──
        registerHookModule(new AutoAcceptFileTransferHook());
        registerHookModule(new BypassShareWarningHook());
        registerHookModule(new DisableNearbyShareAutoOffHook());

        // 注入 XposedInterface
        for (BaseHookModule module : hookModules) {
            module.setXposedInterface(xposed);
        }

        initialized = true;
    }

    public static void registerHookModule(BaseHookModule module) {
        if (module != null && !hookModules.contains(module)) {
            hookModules.add(module);
        }
    }

    public static void handlePackageLoaded(
            XposedModuleInterface.PackageLoadedParam param) {
        savedPackageParams.add(param);
        for (BaseHookModule module : hookModules) {
            module.safeHandleLoadPackage(param);
        }
    }

    public static void handleSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        savedSystemServerParam = param;
        for (BaseHookModule module : hookModules) {
            module.safeHandleSystemServerStarting(param);
        }
    }

    // ── 热重载支持 ─────────────────────────────────────────────

    /**
     * 热重载后重新初始化：清空旧模块列表，用新的 XposedInterface 重新注册全部模块。
     * <p>
     * 不清理 {@link #savedPackageParams} / {@link #savedSystemServerParam}，
     * 因为回放需要它们。
     * </p>
     */
    public static void reinitializeForHotReload(XposedInterface xposed) {
        hookModules.clear();
        registerAllModules(xposed);
    }

    /**
     * 热重载后回放已保存的生命周期参数，让新模块重新安装 Hook。
     * <p>
     * 每个模块调用由 try-catch 包裹，单个模块失败不影响其他模块。
     * </p>
     */
    public static void replayAllHooks() {
        for (BaseHookModule module : hookModules) {
            if (savedSystemServerParam != null) {
                try {
                    module.safeHandleSystemServerStarting(savedSystemServerParam);
                } catch (Throwable ignored) {
                }
            }
            for (XposedModuleInterface.PackageLoadedParam param : savedPackageParams) {
                try {
                    module.safeHandleLoadPackage(param);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
