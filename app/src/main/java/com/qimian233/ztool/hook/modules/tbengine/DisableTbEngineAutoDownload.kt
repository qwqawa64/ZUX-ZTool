package com.qimian233.ztool.hook.modules.tbengine

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables automatic firmware package downloads in the UDS real-time
 * connection engine (com.lenovo.tbengine).
 *
 * The automatic path converges on ServiceController.startOrResumeDownload(Context);
 * user-initiated downloads go through userStartOrResumeDownload(Context) and stay
 * unaffected. Also clamps the OtaPolicy Wi-Fi auto-download policy bit to prevent
 * other paths from re-enabling it.
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
                // no-op: swallow automatic download start
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
