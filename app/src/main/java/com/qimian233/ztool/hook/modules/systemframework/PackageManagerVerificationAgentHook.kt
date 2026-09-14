package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 关闭安装校验代理：
 * Android 16 的安装校验决策集中在 VerifyingSession。
 * - handleStartVerify 前注入 INSTALL_DISABLE_VERIFICATION flag
 * - isVerificationEnabled / isAdbVerificationEnabled 恒返 false，
 *   不再向校验代理广播验证请求
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

            // Android 16 上 isVerificationEnabled 已移入 VerifyingSession
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
