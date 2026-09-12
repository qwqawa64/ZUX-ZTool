package com.qimian233.ztool.hook.modules.tbengine

import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 禁用 UDS 实时连接引擎 (com.lenovo.tbengine) 的预装应用/数据包自动更新。
 *
 * AppData 工作流的自动推进（AppDataNewVersionFound）与 Whatsnew 自动确认路径
 * 均收敛到 ServiceController.startOrResumeAppDataDownload(Context)；
 * 用户在联想中心手动确认走 userStartOrResumeAppDataDownload(Context)，保留不动。
 */
class DisableTbEngineAppUpdate : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_APP_UPDATE.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val serviceControllerClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.services.ServiceController"
            )
            val startOrResumeAppDataDownload = findMethod(
                serviceControllerClass,
                "startOrResumeAppDataDownload",
                Context::class.java
            )
            hookWithId(startOrResumeAppDataDownload, "tbengine_appdata_auto_download") { _ ->
                logger.debug("Blocked automatic pre-installed app data update.")
                // no-op：吞掉预装应用/数据包自动更新
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ServiceController.startOrResumeAppDataDownload", t)
        }
    }
}
