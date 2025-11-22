package com.qimian233.ztool.hook.modules.wallpaper;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 充电动画修复模块
 * 修复ZUI系统壁纸设置中的充电动画显示问题，强制显示全部充电动画选项
 * 通过修改Utilities类的关键方法，确保系统使用包含全部充电动画的资源数组
 */
public class ChargeAnimationFixModule extends BaseHookModule {
    private static final String UTILS_CLASS = "com.zui.wallpapersetting.util.Utilities";

    @Override
    public String getModuleName() {
        return "charge_animation_fix";  // 模块唯一标识
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.zui.wallpapersetting"  // ZUI壁纸设置应用
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.zui.wallpapersetting".equals(lpparam.packageName)) {
            return;  // 提前返回，避免不必要的处理
        }

        // 检查模块是否已启用
        if (!isEnabled()) {
            log("Module is disabled, skipping hook");
            return;
        }

        try {
            hookChargeAnimationUtils(lpparam);
        } catch (Throwable t) {
            logError("Failed to hook charge animation utilities", t);
        }
    }

    /**
     * Hook Utilities类的关键方法，修复充电动画显示
     */
    private void hookChargeAnimationUtils(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 修改Utilities.isLegiony()返回true
            // 原逻辑：(!Utilities.isLegiony() || Utilities.isOversea) ? "chargeStyle_row" : "chargeStyle"
            // 通过强制isLegiony返回true，确保使用"chargeStyle"数组
            XposedHelpers.findAndHookMethod(
                    UTILS_CLASS,
                    lpparam.classLoader,
                    "isLegiony",
                    XC_MethodReplacement.returnConstant(true)
            );

            // 修改Utilities.isOversea()返回false
            XposedHelpers.findAndHookMethod(
                    UTILS_CLASS,
                    lpparam.classLoader,
                    "isOversea",
                    XC_MethodReplacement.returnConstant(false)
            );

            // 修复平板设备的充电动画显示问题
            XposedHelpers.findAndHookMethod(
                    UTILS_CLASS,
                    lpparam.classLoader,
                    "isPad",
                    XC_MethodReplacement.returnConstant(false)
            );

            log("Successfully enabled all charge animations");
            log("Now showing: default, particle, turbo, triangle, girl");
        } catch (Throwable t) {
            logError("Failed to hook Utilities class", t);
        }
    }
}
