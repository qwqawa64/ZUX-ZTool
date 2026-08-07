package com.qimian233.ztool.hook.modules.ota

import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Properties

/**
 * Lenovo OTA 参数修改模块
 * 功能：拦截OTA请求，修改 curfirmwarever 和 deviceid
 * 修改原则：仅在配置值有效（非空）时才修改，否则保持原厂逻辑
 */
class LenovoOTAHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.CUSTOM_OTA_PARAMETERS.name

    override fun getTargetPackages(): Array<String> = arrayOf("com.lenovo.tbengine")

    override fun handleLoadPackage(param: PackageLoadedParam) {
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
                Properties::class.java,  // properties (目标修改对象)
                String::class.java // str2 (URL)
            )

            hookWithId(targetMethod, "target") { chain ->
                val properties = chain.args[1] as Properties
                val url = chain.args[2] as String?

                // 仅拦截包含 "upgrade" 的请求
                if (url != null && url.contains("upgrade")) {
                    var modified = false

                    // 1. 处理 firmware 版本
                    val targetVer = try {
                        remotePreferences.getString(
                            PreferenceKeys.CUSTOM_OTA_TARGET_VERSION_NAME.name,
                            ""
                        )
                    } catch (_: Throwable) {
                        ""
                    }
                    // 只有当 targetVer 不为 null 且去除空格后不为空时才修改
                    if (isConfigValid(targetVer)) {
                        properties["curfirmwarever"] = targetVer!!.trim { it <= ' ' }
                        logger.debug("Modified curfirmwarever: $targetVer")
                        modified = true
                    }

                    // 2. 处理 deviceid
                    val targetId = try {
                        remotePreferences.getString(
                            PreferenceKeys.CUSTOM_OTA_TARGET_DEVICE_ID.name,
                            ""
                        )
                    } catch (_: Throwable) {
                        ""
                    }
                    // 同上
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
     * 辅助方法：检查配置字符串是否有效
     * @param value 从配置读取的字符串
     * @return 如果不为null且长度大于0，则返回true
     */
    private fun isConfigValid(value: String?): Boolean {
        return value != null && !value.trim { it <= ' ' }.isEmpty()
    }
}
