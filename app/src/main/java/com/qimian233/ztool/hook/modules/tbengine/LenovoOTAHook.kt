package com.qimian233.ztool.hook.modules.tbengine

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.Properties

/**
 * Lenovo OTA parameter modification module
 * Function: intercepts OTA requests and modifies curfirmwarever and deviceid
 * Modification rule: only modify when the configured value is valid (non-empty),
 * otherwise keep the stock behavior
 */
class LenovoOTAHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.CUSTOM_OTA_PARAMETERS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookOTARequest(classLoader)
    }

    private fun hookOTARequest(classLoader: ClassLoader) {
        try {
            val serverApiClass: Class<*>
            try {
                serverApiClass = classLoader.loadClass("com.lenovo.tbengine.core.serverapi.ServerApi")
            } catch (_: ClassNotFoundException) {
                return
            }

            val targetMethod = findMethod(serverApiClass, "geServerResponseOrThrowError",
                String::class.java,  // str
                Properties::class.java,  // properties (the target object to modify)
                String::class.java // str2 (URL)
            )

            hookWithId(targetMethod, "target") { chain ->
                val properties = chain.args[1] as Properties
                val url = chain.args[2] as String?

                // Only intercept requests containing "upgrade"
                if (url != null && url.contains("upgrade")) {
                    var modified = false

                    // 1. Handle firmware version
                    val targetVer = try {
                        remotePreferences.getString(
                            PreferenceKeys.CUSTOM_OTA_TARGET_VERSION_NAME.name,
                            ""
                        )
                    } catch (_: Throwable) {
                        ""
                    }
                    // Only modify when targetVer is non-null and non-blank after trimming
                    if (isConfigValid(targetVer)) {
                        properties["curfirmwarever"] = targetVer!!.trim { it <= ' ' }
                        logger.debug("Modified curfirmwarever: $targetVer")
                        modified = true
                    }

                    // 2. Handle deviceid
                    val targetId = try {
                        remotePreferences.getString(
                            PreferenceKeys.CUSTOM_OTA_TARGET_DEVICE_ID.name,
                            ""
                        )
                    } catch (_: Throwable) {
                        ""
                    }
                    // Same as above
                    if (isConfigValid(targetId)) {
                        properties["deviceid"] = targetId!!.trim { it <= ' ' }
                        logger.debug("Modified deviceid: $targetId")
                        modified = true
                    }

                    if (modified) {
                        logger.debug("OTA Request intercepted!")
                    }
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook OTA parameters", t)
        }
    }

    /**
     * Helper: check whether a config string is valid
     * @param value the string read from config
     * @return true if non-null and length > 0
     */
    private fun isConfigValid(value: String?): Boolean {
        return value != null && !value.trim { it <= ' ' }.isEmpty()
    }
}