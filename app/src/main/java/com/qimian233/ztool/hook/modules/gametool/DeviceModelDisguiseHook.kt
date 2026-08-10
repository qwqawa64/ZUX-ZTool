package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 游戏服务设备型号伪装Hook模块
 * 将设备型号伪装为TB322FC，用于绕过游戏服务的设备检测
 */
class DeviceModelDisguiseHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISGUISE_TB322FC.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookDeviceUtils(classLoader)
    }

    private fun hookDeviceUtils(classLoader: ClassLoader) {
        try {
            // 查找DeviceUtils类
            val deviceUtilsClass = classLoader.loadClass("com.zui.util.DeviceUtils")

            // Hook getBuildModel方法，强制返回目标型号
            val getBuildModelMethod = deviceUtilsClass.getDeclaredMethod("getBuildModel")
            hookWithId(
                getBuildModelMethod,
                "get_build_model"
            ) { "TB322FC" }

            logger.info("Successfully hooked DeviceUtils.getBuildModel for com.zui.game.service")
        } catch (e: Exception) {
            logger.error("Failed to hook DeviceUtils.getBuildModel", e)
        }
    }
}
