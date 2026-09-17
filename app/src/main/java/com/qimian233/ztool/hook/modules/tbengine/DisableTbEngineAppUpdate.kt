package com.qimian233.ztool.hook.modules.tbengine

import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables automatic pre-installed app/data package updates in the UDS
 * real-time connection engine (com.lenovo.tbengine).
 *
 * Both the AppData workflow auto-advance (AppDataNewVersionFound) and the
 * Whatsnew auto-confirm path converge on
 * ServiceController.startOrResumeAppDataDownload(Context);
 * the user's manual confirmation in Lenovo Center goes through
 * userStartOrResumeAppDataDownload(Context) and is left untouched.
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
                // no-op: swallow automatic pre-installed app/data package update
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ServiceController.startOrResumeAppDataDownload", t)
        }
    }
}
