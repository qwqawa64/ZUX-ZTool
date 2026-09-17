package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Forced Split Screen hook module (system framework side)
 * Clears the split screen blacklist by hooking OneModeService to enable forced split screen
 *
 * Shares the same preference key name [Split_Screen_mandatory] with
 * setting.SplitScreenMandatory, ensuring both sides are enabled/disabled together.
 */
@SuppressLint("PrivateApi")
class SplitScreenMandatory : SystemHookModule() {
    override fun getModuleName(): String = "Split_Screen_mandatory"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val m = classLoader
                .loadClass("com.android.server.wm.OneModeService")
                .getDeclaredMethod("initLocalBlackList")
            hookWithId(m, "hook_50") { chain: XposedInterface.Chain? ->
                // Check at runtime whether the module is enabled, supporting dynamic toggling
                if (!isEnabled()) {
                    return@hookWithId chain!!.proceed()
                }

                // Get the OneModeService instance
                val instance = chain!!.thisObject

                // Get the mLocalmap field (HashMap storing the split screen blacklist)
                val field = instance.javaClass.getDeclaredField("mLocalmap")
                field.isAccessible = true
                val mLocalmap = field.get(instance) as HashMap<*, *>?

                // Clear mLocalmap to ensure the split screen blacklist is empty
                if (mLocalmap != null) {
                    mLocalmap.clear()
                    logger.debug("Successfully cleared split screen blacklist")
                }
                null
            }

            logger.info("Successfully hooked OneModeService.initLocalBlackList")
        } catch (t: Throwable) {
            logger.error("Failed to hook OneModeService", t)
        }
    }
}
