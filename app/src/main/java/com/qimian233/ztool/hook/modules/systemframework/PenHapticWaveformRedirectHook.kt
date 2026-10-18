package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.pen.PenHapticWaveformIds
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

/**
 * Rewrites pen haptic waveform ids in system_server from the user rule table, e.g.
 * play the pencil waveform for every Lenovo Pen request. Covers both
 * setHapticContinuousParams(int,int,int) and setHapticImpactParams(int,int,int,int).
 * See docs/research/lenovo-pen-haptic/waveform-kinds.md.
 */
class PenHapticWaveformRedirectHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PEN_HAPTIC_WAVEFORM_REDIRECT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val rules = PenHapticWaveformIds.parseRules(
            remotePreferences.getString(
                PreferenceKeys.PEN_HAPTIC_WAVEFORM_REDIRECT_RULES.name,
                PreferenceKeys.PEN_HAPTIC_WAVEFORM_REDIRECT_RULES.default
            ).orEmpty()
        )
        if (rules.isEmpty()) {
            logger.info("No pen haptic waveform redirect rules configured")
            return
        }

        try {
            val serviceClass = param.classLoader.loadClass(SERVICE_CLASS)
            hookWithId(continuousMethod(serviceClass), CONTINUOUS_HOOK_ID) { chain ->
                val target = rules[chain.args[0] as Int]
                if (target == null) {
                    chain.proceed()
                } else {
                    chain.proceed(arrayOf<Any?>(target, chain.args[1], chain.args[2]))
                }
            }
            hookWithId(impactMethod(serviceClass), IMPACT_HOOK_ID) { chain ->
                val target = rules[chain.args[0] as Int]
                if (target == null) {
                    chain.proceed()
                } else {
                    chain.proceed(
                        arrayOf<Any?>(target, chain.args[1], chain.args[2], chain.args[3])
                    )
                }
            }
            logger.info("Pen haptic waveform redirect installed, table: $rules")
        } catch (t: Throwable) {
            logger.error("Failed to install pen haptic waveform redirect", t)
        }
    }

    private fun continuousMethod(serviceClass: Class<*>): Method = findMethod(
        serviceClass,
        "setHapticContinuousParams",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType
    )

    private fun impactMethod(serviceClass: Class<*>): Method = findMethod(
        serviceClass,
        "setHapticImpactParams",
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType
    )

    private companion object {
        const val SERVICE_CLASS =
            "com.zui.server.input.styluspen.pen.bluetooth.haptic.ZuiPenHapticService"
        const val CONTINUOUS_HOOK_ID = "pen_haptic_waveform_redirect_continuous"
        const val IMPACT_HOOK_ID = "pen_haptic_waveform_redirect_impact"
    }
}
