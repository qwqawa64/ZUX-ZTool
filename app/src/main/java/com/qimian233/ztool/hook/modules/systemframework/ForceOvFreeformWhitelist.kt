package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Test hook: forces ZUI's per-package freeform (small window) checks to always pass.
 *
 * The real gate on current ZUX builds is com.android.server.wm.OvCommonCompatManager:
 * OvCommonService.supportsFreeform(Task, ...) delegates there, and it reads the
 * ov_common_*.xml package configs plus the runtime persist
 * (/data/system/zui/ov_common_persist_user_0.xml). OvPackageConfigsFreeform exists but
 * is NOT on this path.
 *
 * Hooks on com.android.server.wm.OvCommonCompatManager:
 * - supportsOvFreeform(String, int): entry permission for freeform
 *   (OvcFeatureFlags.enableOvFreeform() + supportOvCommonOnPkg(pkg, user, 0)).
 * - visibleOvCommon(String, int, int): whether the freeform entry is shown
 *   (recent tasks / sidebar).
 *
 * Module name is "hook_test" so it always runs without a frontend switch.
 */
@SuppressLint("PrivateApi")
class ForceOvFreeformWhitelist : SystemHookModule() {
    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName, ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val compatClass: Class<*> = param.classLoader.loadClass(
            "com.android.server.wm.OvCommonCompatManager")

        val supportsOvFreeformMethod: Method = findMethod(
            compatClass, "supportsOvFreeform",
            String::class.java,                       // pkgName
            Int::class.javaPrimitiveType              // userId
        )
        hookWithId(supportsOvFreeformMethod, "force_ov_freeform_whitelist") { chain ->
            val pkg = chain.getArg(0) as String?
            val user = chain.getArg(1) as Int
            logger.debug("supportsOvFreeform($pkg, $user) -> true (bypassed)")
            true
        }

        val visibleOvCommonMethod: Method = findMethod(
            compatClass, "visibleOvCommon",
            String::class.java,                       // pkgName
            Int::class.javaPrimitiveType,             // userId
            Int::class.javaPrimitiveType              // feature type
        )
        hookWithId(visibleOvCommonMethod, "force_ov_freeform_visible") { chain ->
            val pkg = chain.getArg(0) as String?
            val user = chain.getArg(1) as Int
            val type = chain.getArg(2) as Int
            logger.debug("visibleOvCommon($pkg, $user, $type) -> true (bypassed)")
            true
        }
    }
}
