package com.qimian233.ztool.hook.modules.tbengine

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 禁用 UDS 实时连接引擎 (com.lenovo.tbengine) 的固件包自动下载。
 *
 * 自动路径收敛在 ServiceController.startOrResumeDownload(Context)，
 * 用户手动下载走 userStartOrResumeDownload(Context)，互不影响。
 * 同时钳制 OtaPolicy 的 Wi-Fi 自动下载策略位，防止其它路径重新打开。
 */
class DisableTbEngineAutoDownload : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_DOWNLOAD.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val serviceControllerClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.services.ServiceController"
            )
            val startOrResumeDownload = findMethod(
                serviceControllerClass,
                "startOrResumeDownload",
                android.content.Context::class.java
            )
            hookWithId(startOrResumeDownload, "tbengine_auto_download_start") { _ ->
                logger.debug("Blocked automatic OTA download trigger.")
                // no-op：吞掉自动下载启动
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ServiceController.startOrResumeDownload", t)
        }

        try {
            val otaPolicyClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.policy.OtaPolicy"
            )
            val autoDownloadSetter = findMethod(
                otaPolicyClass,
                "setmSettingNormalAutoDownload",
                Boolean::class.java
            )
            hookWithId(autoDownloadSetter, "tbengine_auto_download_policy_set") { chain ->
                chain.proceed(arrayOf(false))
                logger.debug("Forced OtaPolicy.mSettingNormalAutoDownload to false.")
            }
            val autoDownloadGetter = findMethod(otaPolicyClass, "getmSettingNormalAutoDownload")
            hookWithId(autoDownloadGetter, "tbengine_auto_download_policy_get") { _ ->
                logger.debug("Forced OtaPolicy.mSettingNormalAutoDownload read to false.")
                false
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook OtaPolicy auto download policy", t)
        }
    }
}
