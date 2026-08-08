package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

@SuppressLint("SoonBlockedPrivateApi", "PrivateApi")
class AllowGetPackages : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ALLOW_GET_PACKAGES.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            logger.info("Start hooking android.app.AppOpsManager, SystemFramework")
            val opToDefaultMode = findMethod(
                classLoader.loadClass("android.app.AppOpsManager"),
                "opToDefaultMode",
                Int::class.javaPrimitiveType)
            hookWithId(
                opToDefaultMode,
                "op_to_default_mode"
            ) { chain ->
                val op = chain.getArg(0) as Int
                if (op == OP_GET_INSTALLED_APP) {
                    return@hookWithId 0
                }
                chain.proceed()
            }
            logger.info("Hooked android.app.AppOpsManager")
        } catch (e: Exception) {
            logger.error("Failed hooking android.app.AppOpsManager", e)
        }
        try {
            logger.info("Start hooking com.android.server.appop.AppOpsService, SystemFramework")
            val checkOperation = findMethod(
                classLoader.loadClass("com.android.server.appop.AppOpsService"),
                    "checkOperationRawZui",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
            hookWithId(
                checkOperation,
                "check_operation_raw_zui"
            ) { chain ->
                val op = chain.getArg(0) as Int
                if (op == OP_GET_INSTALLED_APP) {
                    return@hookWithId 0
                }
                chain.proceed()
            }
            logger.info("Hooked com.android.server.appop.AppOpsService [OK]")
        } catch (e: Exception) {
            logger.error("Failed hooking com.android.server.appop.AppOpsService", e)
        }
    }

    companion object {

        private const val OP_GET_INSTALLED_APP = 214
    }
}
