package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Lifts Enhanced Confirmation Mode restrictions for every package.
 *
 * See docs/research/restricted_settings_ecm_chain.md for the decompiled chain.
 */
@SuppressLint("PrivateApi")
class AllowAllRestrictedSettings : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ALLOW_ALL_RESTRICTED_SETTINGS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        val stubClass = try {
            classLoader.loadClass(ECM_STUB_CLASS)
        } catch (e: Throwable) {
            logger.warn("Enhanced Confirmation Mode service is absent: ${e.message}")
            return
        }
        logger.info("Start hooking $ECM_STUB_CLASS, SystemFramework")

        // Single decision point behind both isRestricted and getRestrictionReason
        try {
            val getRestrictionReason = findMethod(
                stubClass,
                "getRestrictionReason",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(getRestrictionReason, "ecm_get_restriction_reason") { null }
        } catch (e: Exception) {
            logger.error("Failed hooking getRestrictionReason", e)
        }

        try {
            val isRestricted = findMethod(
                stubClass,
                "isRestricted",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(isRestricted, "ecm_is_restricted") { false }
        } catch (e: Exception) {
            logger.error("Failed hooking isRestricted", e)
        }

        // Hides the now-redundant "allow restricted settings" app-detail entry
        try {
            val isClearRestrictionAllowed = findMethod(
                stubClass,
                "isClearRestrictionAllowed",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(isClearRestrictionAllowed, "ecm_is_clear_restriction_allowed") { false }
        } catch (e: Exception) {
            logger.error("Failed hooking isClearRestrictionAllowed", e)
        }

        logger.info("Hooked $ECM_STUB_CLASS")
    }

    companion object {
        private const val ECM_STUB_CLASS = $$"com.android.ecm.EnhancedConfirmationService$Stub"
    }
}
