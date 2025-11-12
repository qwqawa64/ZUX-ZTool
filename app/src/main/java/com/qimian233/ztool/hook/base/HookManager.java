package com.qimian233.ztool.hook.base;

import com.qimian233.ztool.hook.modules.gametool.CpuFrequencyFix;
import com.qimian233.ztool.hook.modules.gametool.DeviceModelDisguiseHook;
import com.qimian233.ztool.hook.modules.gametool.DisableGameAudio;
import com.qimian233.ztool.hook.modules.gametool.SocTemperatureFix;
import com.qimian233.ztool.hook.modules.ota.DisableOtaCheck;
import com.qimian233.ztool.hook.modules.otherhook.KeepRotation;
import com.qimian233.ztool.hook.modules.packageinstaller.Hook_Skip_WarnPage;
import com.qimian233.ztool.hook.modules.packageinstaller.Hook_disable_installerAD;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerHookScan;
import com.qimian233.ztool.hook.modules.packageinstaller.PackageInstallerPermissionHook;
import com.qimian233.ztool.hook.modules.packageinstaller.packageInstallerStyleHook;
import com.qimian233.ztool.hook.modules.setting.AllowDisplayDolbyHook;
import com.qimian233.ztool.hook.modules.setting.OwnerInfoHook;
import com.qimian233.ztool.hook.modules.setting.PermissionControllerHook;
import com.qimian233.ztool.hook.modules.setting.SplitScreenMandatory;
import com.qimian233.ztool.hook.modules.setting.yishijiecompletion;
import com.qimian233.ztool.hook.modules.systemui.CustomControlCenterDate;
import com.qimian233.ztool.hook.modules.systemui.CustomStatusBarClock;
import com.qimian233.ztool.hook.modules.systemui.NativeNotificationIcon;
import com.qimian233.ztool.hook.modules.systemui.NoChargeAnimation;
import com.qimian233.ztool.hook.modules.systemui.NotificationIconHook;
import com.qimian233.ztool.hook.modules.systemui.StatusBarClockSecondsHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUIChargeWattsHook;
import com.qimian233.ztool.hook.modules.systemui.SystemUIRealWatts;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.List;

/**
 * Hook模块管理器
 * 负责注册、管理和执行所有Hook模块
 */
public class HookManager {
    private static final List<BaseHookModule> hookModules = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * 初始化所有Hook模块
     */
    public static void initialize() {
        if (initialized) return;

        // 注册所有Hook模块

        // 注册模块：一视界黑名单屏蔽
        registerHookModule(new yishijiecompletion());
        // 注册模块：禁用游戏音频优化
        registerHookModule(new DisableGameAudio());
        // 注册模块：禁用OTA检查
        registerHookModule(new DisableOtaCheck());
        // 注册模块：机型伪装Y700四代
        registerHookModule(new DeviceModelDisguiseHook());
        // 注册模块：cpu频率修复
        registerHookModule(new CpuFrequencyFix());
        // 注册模块：SOC温度修复
        registerHookModule(new SocTemperatureFix());
        // 注册模块：跳过APK扫描
        registerHookModule(new PackageInstallerHookScan());
        // 注册模块：包安装器权限Hook模块
        registerHookModule(new PackageInstallerPermissionHook());
        // 注册模块：包安装器跳过警告页面模块
        registerHookModule(new Hook_Skip_WarnPage());
        // 注册模块：PackageInstaller广告屏蔽模块
        registerHookModule(new Hook_disable_installerAD());
        // 注册模块：状态栏时间显秒
        registerHookModule(new StatusBarClockSecondsHook());
        // 注册模块：自定义状态栏时钟
        registerHookModule(new CustomStatusBarClock());
        // 注册模块：移除分屏黑名单
        registerHookModule(new SplitScreenMandatory());
        // 注册模块：自定义锁屏一言
        registerHookModule(new OwnerInfoHook());
        // 注册模块：SystemUI充电瓦数显示模块
        registerHookModule(new SystemUIChargeWattsHook());
        // 注册模块：SystemUI实际充电瓦数显示模块
        registerHookModule(new SystemUIRealWatts());
        // 注册模块：自定义状态栏图标数量
        registerHookModule(new NotificationIconHook());
        // 注册模块：自定义控制中心月份时钟
        registerHookModule(new CustomControlCenterDate());
        // 注册模块：移除充电动画
        registerHookModule(new NoChargeAnimation());
        // 注册模块：启用原生安装器
        registerHookModule(new packageInstallerStyleHook());
        // 注册模块：允许关闭Dolby
        registerHookModule(new AllowDisplayDolbyHook());
        // 注册模块：权限控制器样式Hook
        registerHookModule(new PermissionControllerHook());
        // 注册模块，使用原生通知图标
        registerHookModule(new NativeNotificationIcon());
        // 注册模块，重启后保持屏幕方向不变
        registerHookModule(new KeepRotation());
        initialized = true;
    }

    /**
     * 注册Hook模块
     */
    public static void registerHookModule(BaseHookModule module) {
        if (module != null && !hookModules.contains(module)) {
            hookModules.add(module);
        }
    }

    /**
     * 注销Hook模块
     */
    public static void unregisterHookModule(BaseHookModule module) {
        hookModules.remove(module);
    }

    /**
     * 获取所有注册的模块
     */
    public static List<BaseHookModule> getHookModules() {
        return new ArrayList<>(hookModules);
    }

    /**
     * 执行所有适用的Hook模块
     */
    public static void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        for (BaseHookModule module : hookModules) {
            module.safeHandleLoadPackage(lpparam);
        }
    }

    /**
     * 根据名称获取模块
     */
    public static BaseHookModule getModuleByName(String name) {
        for (BaseHookModule module : hookModules) {
            if (module.getModuleName().equals(name)) {
                return module;
            }
        }
        return null;
    }
}
