package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Disables the install verification agent:
 * Android 16 centralizes install verification decisions in VerifyingSession.
 * - Injects the INSTALL_DISABLE_VERIFICATION flag before handleStartVerify
 * - isVerificationEnabled / isAdbVerificationEnabled always return false,
 *   so verification requests are no longer broadcast to the verification agent
 */
class PackageManagerVerificationAgentHook : SystemHookModule() {

    override fun getModuleName(): String =
        PreferenceKeys.PKG_MGR_DISABLE_VERIFICATION_AGENT.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val verifyingSessionClass =
                classLoader.loadClass("com.android.server.pm.VerifyingSession")
            val installFlagsField = findField(verifyingSessionClass, "mInstallFlags")

            val handleStartVerify = verifyingSessionClass.declaredMethods.first {
                it.name == "handleStartVerify"
            }
            hookWithId(handleStartVerify, "pkgmgr_verify_agent_start") { chain ->
                val session = chain.thisObject
                if (session != null) {
                    installFlagsField.setInt(
                        session,
                        installFlagsField.getInt(session) or INSTALL_DISABLE_VERIFICATION
                    )
                }
                chain.proceed()
            }

            // On Android 16 isVerificationEnabled moved into VerifyingSession
            val isVerificationEnabled = verifyingSessionClass.declaredMethods.first {
                it.name == "isVerificationEnabled" &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            hookWithId(isVerificationEnabled, "pkgmgr_verify_agent_enabled") { _ -> false }

            val isAdbVerificationEnabled = verifyingSessionClass.declaredMethods.first {
                it.name == "isAdbVerificationEnabled" &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            hookWithId(isAdbVerificationEnabled, "pkgmgr_verify_agent_adb_enabled") { _ -> false }
            logger.info("Hooked VerifyingSession verification agent switches")
        } catch (e: Throwable) {
            logger.error("Failed hooking VerifyingSession", e)
        }
    }

    companion object {
        private const val INSTALL_DISABLE_VERIFICATION = 0x00080000
    }
}
