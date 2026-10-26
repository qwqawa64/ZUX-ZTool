package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.HashMap

/**
 * Forced split screen hook module (system framework side).
 *
 * Clears the split screen blacklist through `OneModeService.initLocalBlackList` on ZUXOS 2.0
 * and older; ZUXOS 2.5 (Android 17) removed that service from system_server, so the module
 * logs one warning and skips instead of failing.
 * Shares the preference key `Split_Screen_mandatory` with the settings side.
 */
@SuppressLint("PrivateApi")
class SplitScreenMandatory : SystemHookModule() {
    override fun getModuleName(): String = "Split_Screen_mandatory"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        val oneModeServiceClass = LEGACY_ONE_MODE_SERVICES.firstNotNullOfOrNull { className ->
            runCatching { classLoader.loadClass(className) }.getOrNull()
        }
        if (oneModeServiceClass == null) {
            logger.warn("OneModeService absent from system_server; split screen blacklist hook skipped")
            return
        }

        val initBlackList = runCatching {
            findMethod(oneModeServiceClass, "initLocalBlackList")
        }.getOrNull()
        val localMapField = runCatching {
            findField(oneModeServiceClass, "mLocalmap")
        }.getOrNull()
        if (initBlackList == null || localMapField == null) {
            logger.warn("OneModeService has no initLocalBlackList/mLocalmap; split screen blacklist hook skipped")
            return
        }

        hookWithId(initBlackList, "hook_50") { chain ->
            if (!isEnabled()) {
                return@hookWithId chain.proceed()
            }
            val localMap = localMapField.get(chain.thisObject) as? HashMap<*, *>
            if (localMap != null) {
                localMap.clear()
                logger.debug("Successfully cleared split screen blacklist")
            }
            null
        }

        logger.info("Successfully hooked OneModeService.initLocalBlackList")
    }

    companion object {
        // ZUXOS 2.0 and older shipped OneModeService in system_server; ZUXOS 2.5 no longer does
        private val LEGACY_ONE_MODE_SERVICES = listOf("com.android.server.wm.OneModeService")
    }
}
