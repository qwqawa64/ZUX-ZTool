package com.qimian233.ztool.hook.modules.wallpaper

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 充电动画修复模块
 * 修复ZUI系统壁纸设置中的充电动画显示问题，强制显示全部充电动画选项
 * 通过修改Utilities类的关键方法，确保系统使用包含全部充电动画的资源数组
 */
class ChargeAnimationFixModule : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.CHARGE_ANIMATION_FIX.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.WALLPAPER_SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            hookChargeAnimationUtils(classLoader)
        } catch (t: Throwable) {
            logger.error("Failed to hook charge animation utilities", t)
        }
    }

    /**
     * Hook Utilities类的关键方法，修复充电动画显示
     */
    private fun hookChargeAnimationUtils(classLoader: ClassLoader) {
        try {
            val utilsClass = classLoader.loadClass(UTILS_CLASS)

            // 修改Utilities.isLegiony()返回true
            // 原逻辑：(!Utilities.isLegiony() || Utilities.isOversea) ? "chargeStyle_row" : "chargeStyle"
            // 通过强制isLegiony返回true，确保使用"chargeStyle"数组
            val isLegionyMethod = utilsClass.getDeclaredMethod("isLegiony")
            hookWithId(
                isLegionyMethod,
                "is_legiony"
            ) { true }

            // 修改Utilities.isOversea()返回false
            val isOverseaMethod = utilsClass.getDeclaredMethod("isOversea")
            hookWithId(
                isOverseaMethod,
                "is_oversea"
            ) { false }

            // 修复平板设备的充电动画显示问题
            val isPadMethod = utilsClass.getDeclaredMethod("isPad")
            hookWithId(isPadMethod, "is_pad") { false }

            logger.info("Successfully enabled all charge animations")
            logger.debug("Now showing: default, particle, turbo, triangle, girl")
        } catch (t: Throwable) {
            logger.error("Failed to hook Utilities class", t)
        }
    }

    companion object {
        private const val UTILS_CLASS = "com.zui.wallpapersetting.util.Utilities"
    }
}
