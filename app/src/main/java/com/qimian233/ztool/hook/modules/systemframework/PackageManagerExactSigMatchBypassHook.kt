package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Bypasses exact signature matching: SigningDetails.signaturesMatchExactly always returns true.
 * Affects the installer's "must exactly match the installed version's signature" check.
 */
class PackageManagerExactSigMatchBypassHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PKG_MGR_BYPASS_EXACT_SIG_MATCH.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val signingDetailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
            val signaturesMatchExactly = findMethod(
                signingDetailsClass, "signaturesMatchExactly", signingDetailsClass
            )
            hookWithId(signaturesMatchExactly, "pkgmgr_signatures_match_exactly") { _ -> true }
            logger.info("Hooked SigningDetails.signaturesMatchExactly")
        } catch (e: Throwable) {
            logger.error("Failed hooking signaturesMatchExactly", e)
        }
    }
}
