package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Test hook: forces ZUI's per-package freeform (small window) whitelist check to
 * always pass.
 *
 * Hooks com.android.server.wm.OvPackageConfigsFreeform.supportsOvFreeform(String, int);
 * the original implementation gates small-window entry behind the dynamic allow/deny
 * sets, static region whitelists and the resizablePkgMap. Replacing its return value
 * with true lets every package enter freeform regardless of the whitelist. The
 * OvcFeatureFlags.enableOvFreeform() feature gate is bypassed along with it, because
 * that check happens inside the hooked method body.
 *
 * Module name is "hook_test" so it always runs without a frontend switch.
 */
@SuppressLint("PrivateApi")
class ForceOvFreeformWhitelist : SystemHookModule() {
    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val configClass: Class<*> = param.classLoader.loadClass(
            "com.android.server.wm.OvPackageConfigsFreeform")

        val supportsOvFreeformMethod: Method = findMethod(
            configClass, "supportsOvFreeform",
            String::class.java,                       // pkgName
            Int::class.javaPrimitiveType              // userId
        )

        hookWithId(supportsOvFreeformMethod, "force_ov_freeform_whitelist") { chain ->
            val pkg = chain.getArg(0) as String?
            val user = chain.getArg(1) as Int
            logger.debug("supportsOvFreeform($pkg, $user) -> true (whitelist bypassed)")
            true
        }
    }
}
