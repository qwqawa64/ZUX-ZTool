package com.qimian233.ztool.hook.base;

import com.qimian233.ztool.hook.modules.HookTestModule;
import com.qimian233.ztool.hook.modules.documentsui.DocumentsUIBypass;
import com.qimian233.ztool.hook.modules.gametool.AutoMistakeTouchHook;
import com.qimian233.ztool.hook.modules.gametool.CpuFrequencyFix;
import com.qimian233.ztool.hook.modules.gametool.DeviceModelDisguiseHook;
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudio;
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudioApp;
import com.qimian233.ztool.hook.modules.gametool.SocTemperatureFix;
import com.qimian233.ztool.hook.modules.launcher.CleanGlobalSearch;
import com.qimian233.ztool.hook.modules.launcher.CustomGridSize;
import com.qimian233.ztool.hook.modules.launcher.DisableDockBar;
import com.qimian233.ztool.hook.modules.launcher.DisableForceStop;
import com.qimian233.ztool.hook.modules.launcher.RecentTaskMemoryViewHook;
import com.qimian233.ztool.hook.modules.launcher.ZuiLauncherHotseatHook;
import com.qimian233.ztool.hook.modules.mobiledesktop.AutoAcceptFileTransferHook;
import com.qimian233.ztool.hook.modules.mobiledesktop.BypassShareWarningHook;
import com.qimian233.ztool.hook.modules.ota.DisableOtaCheck;
import com.qimian233.ztool.hook.modules.ota.LenovoOTAHook;
import com.qimian233.ztool.hook.modules.packageinstaller.Hook_Skip_WarnPage;
import com.qimian233.ztool.hook.modules.packageinstaller.Hook_disable_installerAD;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerHookScan;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerNoDeleteModule;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerPermissionHook;
import com.qimian233.ztool.hook.modules.packageinstaller.packageInstallerStyleHook;
import com.qimian233.ztool.hook.modules.safecenter.DisableAllVirusScans;
import com.qimian233.ztool.hook.modules.safecenter.EnableAutorunByDefault;
import com.qimian233.ztool.hook.modules.setting.AllowDisplayDolbyHook;
import com.qimian233.ztool.hook.modules.setting.AppInfoHeaderDetailsHook;
import com.qimian233.ztool.hook.modules.setting.CustomizeAboutDeviceInfo;
import com.qimian233.ztool.hook.modules.setting.HideOtaUpdateHint;
import com.qimian233.ztool.hook.modules.systemFramework.KeepRotation;
import com.qimian233.ztool.hook.modules.setting.OwnerInfoHook;
import com.qimian233.ztool.hook.modules.setting.PermissionControllerHook;
import com.qimian233.ztool.hook.modules.setting.SplitScreenMandatory;
import com.qimian233.ztool.hook.modules.setting.ZToolSettingsEntryHook;
import com.qimian233.ztool.hook.modules.setting.yishijiecompletion;
import com.qimian233.ztool.hook.modules.systemFramework.AiInputExpand;
import com.qimian233.ztool.hook.modules.systemFramework.AllowGetPackages;
import com.qimian233.ztool.hook.modules.systemFramework.AllowUntrustedTouch;
import com.qimian233.ztool.hook.modules.systemFramework.DisableFlagSecure;
import com.qimian233.ztool.hook.modules.systemFramework.ForceScreenOnOffAnimation;
import com.qimian233.ztool.hook.modules.systemFramework.LsposedServiceProtector;
import com.qimian233.ztool.hook.modules.systemFramework.NoMorePasswordPer24H;
import com.qimian233.ztool.hook.modules.systemui.BrightnessSliderPercentageHook;
import com.qimian233.ztool.hook.modules.systemui.ControlCenterNoTileLabelsHook;
import com.qimian233.ztool.hook.modules.systemui.CustomControlCenterDate;
import com.qimian233.ztool.hook.modules.systemui.CustomQsColor;
import com.qimian233.ztool.hook.modules.systemui.CustomQsRoundCorner;
import com.qimian233.ztool.hook.modules.systemui.CustomStatusBarClock;
import com.qimian233.ztool.hook.modules.systemui.ForceLenovoAOD;
import com.qimian233.ztool.hook.modules.systemui.GuestModeController;
import com.qimian233.ztool.hook.modules.systemui.NativeNotificationIcon;
import com.qimian233.ztool.hook.modules.systemui.NoChargeAnimation;
import com.qimian233.ztool.hook.modules.systemui.NotificationCenterTransparency;
import com.qimian233.ztool.hook.modules.systemui.NotificationIconHook;
import com.qimian233.ztool.hook.modules.systemui.StatusBarClockSecondsHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUIBatteryHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUIChargeWattsHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUINetworkSpeedSIzeHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUINetworkSpeeddoublelayerHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUIRealWatts;
import com.qimian233.ztool.hook.modules.systemui.VolumeSliderPercentageHook;
import com.qimian233.ztool.hook.modules.wallpaper.ChargeAnimationFixModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook 模块管理器（libxposed 版）。
 * <p>
 * 按进程类型将模块分为 systemServerModules 和 appModules，
 * 由 {@link com.qimian233.ztool.hook.HookInit} 在对应的生命周期回调中调度。
 */
public class HookManager {

    private static final List<BaseHookModule> hookModules = new ArrayList<>();
    private static boolean initialized = false;

    public static void initialize(XposedInterface xposed) {
        if (initialized) return;

        // ── 系统框架 (target: system — 由 onSystemServerStarting 调度) ──
        registerHookModule(new HookTestModule());
        registerHookModule(new DisableFlagSecure());
        registerHookModule(new NoMorePasswordPer24H());
        registerHookModule(new AllowGetPackages());
        registerHookModule(new AllowUntrustedTouch());
        registerHookModule(new ForceScreenOnOffAnimation());
        registerHookModule(new AiInputExpand());
        registerHookModule(new LsposedServiceProtector());
        registerHookModule(new KeepRotation());

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
        registerHookModule(new SystemUINetworkSpeedSIzeHook());
        registerHookModule(new SystemUINetworkSpeeddoublelayerHook());
        registerHookModule(new SystemUIBatteryHook());
        registerHookModule(new ForceLenovoAOD());
        registerHookModule(new CustomQsRoundCorner());
        registerHookModule(new BrightnessSliderPercentageHook());
        registerHookModule(new VolumeSliderPercentageHook());
        registerHookModule(new CustomQsColor());
        registerHookModule(new NotificationCenterTransparency());
        registerHookModule(new GuestModeController());

        // ── Settings (target: com.android.settings) ──
        registerHookModule(new yishijiecompletion());
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
        registerHookModule(new Hook_Skip_WarnPage());
        registerHookModule(new Hook_disable_installerAD());
        registerHookModule(new packageInstallerStyleHook());
        registerHookModule(new PackageInstallerNoDeleteModule());

        // ── Launcher (target: com.zui.launcher) ──
        registerHookModule(new DisableForceStop());
        registerHookModule(new ZuiLauncherHotseatHook());
        registerHookModule(new CustomGridSize());
        registerHookModule(new CleanGlobalSearch());
        registerHookModule(new DisableDockBar());
        registerHookModule(new RecentTaskMemoryViewHook());

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
        for (BaseHookModule module : hookModules) {
            module.safeHandleLoadPackage(param);
        }
    }

    public static void handleSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        for (BaseHookModule module : hookModules) {
            module.safeHandleSystemServerStarting(param);
        }
    }
}
