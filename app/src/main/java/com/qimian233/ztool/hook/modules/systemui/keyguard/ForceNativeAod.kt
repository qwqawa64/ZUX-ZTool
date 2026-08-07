package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 强制启用原生 AOSP AOD（Always-On Display），忽略电池省电模式限制。
 */
@SuppressLint("PrivateApi")
class ForceNativeAod : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FORCE_NATIVE_AOD.name

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("Loading module ForceNativeAOD.")
        hookGetAlwaysOn(classLoader)
        hookAlwaysOnEnabled()
    }

    /**
     * Hook `DozeParameters.getAlwaysOn()`，始终返回 true，
     * 确保 `updateControlScreenOff()` 中正确设置
     * `PowerManager.setDozeAfterScreenOff(false)`。
     */
    private fun hookGetAlwaysOn(classLoader: ClassLoader) {
        try {
            val dozeParamsClass = classLoader.loadClass(DOZE_PARAMETERS_CLASS)
            val getAlwaysOnMethod = dozeParamsClass.getDeclaredMethod("getAlwaysOn")
            hookWithId(getAlwaysOnMethod, "get_always_on") {
                logger.debug("ForceNativeAOD: getAlwaysOn() -> true")
                true
            }
            logger.info("Hooked DozeParameters.getAlwaysOn() [OK]")
        } catch (t: Throwable) {
            logger.error("Failed to hook DozeParameters.getAlwaysOn()", t)
        }
    }

    /**
     * Hook `AmbientDisplayConfiguration.alwaysOnEnabled(int)`，始终返回 true。
     * 
     * 
     * 该方法是 `DozeSuppressor` 决定状态机走向（DOZE vs DOZE_AOD）
     * 以及 `DozeSensors` 传感器注册策略的核心判断点，
     * 直接读取 `Settings.Secure.doze_always_on`。
     * 因为不再通过 shell 写入该值，必须用 Hook 覆盖。
     * 
     */
    @SuppressLint("BlockedPrivateApi")
    private fun hookAlwaysOnEnabled() {
        try {
            val configClass = Class.forName(AMBIENT_DISPLAY_CONFIG_CLASS)
            val alwaysOnEnabledMethod = configClass.getDeclaredMethod(
                "alwaysOnEnabled", Int::class.javaPrimitiveType
            )
            hookWithId(alwaysOnEnabledMethod, "always_on_enabled") {
                logger.debug("ForceNativeAOD: alwaysOnEnabled() -> true")
                true
            }
            logger.info("Hooked AmbientDisplayConfiguration.alwaysOnEnabled() [OK]")
        } catch (t: Throwable) {
            logger.error("Failed to hook AmbientDisplayConfiguration.alwaysOnEnabled()", t)
        }
    }

    companion object {
        private const val DOZE_PARAMETERS_CLASS =
            "com.android.systemui.statusbar.phone.DozeParameters"
        private const val AMBIENT_DISPLAY_CONFIG_CLASS =
            "android.hardware.display.AmbientDisplayConfiguration"
    }
}
