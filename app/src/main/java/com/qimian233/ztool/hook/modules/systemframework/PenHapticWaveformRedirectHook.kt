package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Test hook: redirects pen haptic waveform ids before they reach the pen, currently
 * mapping Lenovo Pen (36) to Pencil (33); the table is id -> id so sources can later
 * target different waveforms. See docs/research/lenovo-pen-haptic/waveform-kinds.md.
 *
 * The module name "test_hook" auto-enables it without a frontend switch.
 */
class PenHapticWaveformRedirectHook : SystemHookModule() {

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        try {
            val serviceClass = param.classLoader.loadClass(SERVICE_CLASS)
            val setContinuousParams = findMethod(
                serviceClass,
                "setHapticContinuousParams",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hookWithId(setContinuousParams, HOOK_ID) { chain ->
                val source = chain.args[0] as Int
                val target = WAVEFORM_REDIRECTS[source]
                if (target == null) {
                    chain.proceed()
                } else {
                    chain.proceed(arrayOf<Any?>(target, chain.args[1], chain.args[2]))
                }
            }
            logger.info("Pen haptic waveform redirect installed, table: $WAVEFORM_REDIRECTS")
        } catch (t: Throwable) {
            logger.error("Failed to install pen haptic waveform redirect", t)
        }
    }

    private companion object {
        const val SERVICE_CLASS =
            "com.zui.server.input.styluspen.pen.bluetooth.haptic.ZuiPenHapticService"
        const val HOOK_ID = "pen_haptic_waveform_redirect"

        /** ZuiPenHapticConstants.WAVE_FORM_ID_PENCIL */
        const val WAVE_FORM_ID_PENCIL = 33

        /** ZuiPenHapticConstants.WAVE_FORM_ID_LENOVO_BRUSH */
        const val WAVE_FORM_ID_LENOVO_PEN = 36

        /** Source waveform id -> replacement waveform id. */
        val WAVEFORM_REDIRECTS: Map<Int, Int> = mapOf(
            WAVE_FORM_ID_LENOVO_PEN to WAVE_FORM_ID_PENCIL,
        )
    }
}
