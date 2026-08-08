package com.qimian233.ztool.hook.modules.wallpaper;

import com.qimian233.ztool.data.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 充电动画修复模块
 * 修复ZUI系统壁纸设置中的充电动画显示问题，强制显示全部充电动画选项
 * 通过修改Utilities类的关键方法，确保系统使用包含全部充电动画的资源数组
 */
public class ChargeAnimationFixModule extends AppHookModule {
    private static final String UTILS_CLASS = "com.zui.wallpapersetting.util.Utilities";

    public ChargeAnimationFixModule() {}

    @Override
    public String getModuleName() {
        return "charge_animation_fix";  // 模块唯一标识
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.WALLPAPER_SETTINGS.packageName  // ZUI壁纸设置应用
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!ScopeKeys.WALLPAPER_SETTINGS.packageName.equals(packageName)) {
            return;  // 提前返回，避免不必要的处理
        }

        // 检查模块是否已启用
        if (!isEnabled()) {
            logger.info("Module is disabled, skipping hook");
            return;
        }

        try {
            hookChargeAnimationUtils(classLoader);
        } catch (Throwable t) {
            logger.error("Failed to hook charge animation utilities", t);
        }
    }

    /**
     * Hook Utilities类的关键方法，修复充电动画显示
     */
    private void hookChargeAnimationUtils(ClassLoader classLoader) {
        try {
            Class<?> utilsClass = classLoader.loadClass(UTILS_CLASS);

            // 修改Utilities.isLegiony()返回true
            // 原逻辑：(!Utilities.isLegiony() || Utilities.isOversea) ? "chargeStyle_row" : "chargeStyle"
            // 通过强制isLegiony返回true，确保使用"chargeStyle"数组
            Method isLegionyMethod = utilsClass.getDeclaredMethod("isLegiony");
            hookWithId(isLegionyMethod, "is_legiony", chain -> true);

            // 修改Utilities.isOversea()返回false
            Method isOverseaMethod = utilsClass.getDeclaredMethod("isOversea");
            hookWithId(isOverseaMethod, "is_oversea", chain -> false);

            // 修复平板设备的充电动画显示问题
            Method isPadMethod = utilsClass.getDeclaredMethod("isPad");
            hookWithId(isPadMethod, "is_pad", chain -> false);

            logger.info("Successfully enabled all charge animations");
            logger.debug("Now showing: default, particle, turbo, triangle, girl");
        } catch (Throwable t) {
            logger.error("Failed to hook Utilities class", t);
        }
    }
}
