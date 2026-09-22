package com.qimian233.ztool.hook.modules.systemui.misc

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Fixes the ZUI control-center "delayed rebound" by falling back to the AOSP
 * shade fling animation instead of the ZUI custom spring implementation.
 *
 * Root cause measured on device (see ShadeReboundTimingHook logs): the ZUI
 * branch in NotificationPanelViewController.flingToHeight
 * (QsReboundFeature.animEnable()) drives the panel height with a soft
 * SpringAnimation (stiffness=100, damping=0.72) whose EndListener only fires
 * after full convergence. A layout pass cancels it near target and re-fires a
 * zero-velocity fling that creeps ~900ms before the visible springBack starts
 * — ~1.6s of perceived delay after touch release.
 *
 * This hook short-circuits QsReboundFeature.animEnable() to false, which makes
 * flingToHeight take the AOSP ValueAnimator path: a ~350ms fling with the
 * standard overshoot, followed immediately by the 400ms springBack rebound.
 *
 * Rebound shaping is ZUI-side only; the AOSP path preserves the overExpansion
 * mechanism. Once a ZUI release ships a proper fix, gate this hook off by
 * version in the UI switch (frontend-side version gating).
 */
class ShadeReboundFix : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SHADE_REBOUND_FIX.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return

        val cl = param.defaultClassLoader
        val featureClass = try {
            cl.loadClass("com.android.systemui.shade.util.QsReboundFeature")
        } catch (t: Throwable) {
            logger.error("ShadeReboundFix: QsReboundFeature class not found", t)
            return
        }

        try {
            val animEnable = featureClass.getDeclaredMethod("animEnable")
            hookWithId(animEnable, "shade_rebound_aosp_fallback") { chain ->
                logger.debug("QsReboundFeature.animEnable -> false (AOSP fallback)")
                false
            }
            logger.info("ShadeReboundFix installed (AOSP fling fallback)")
        } catch (t: Throwable) {
            logger.error("ShadeReboundFix: hook QsReboundFeature.animEnable failed", t)
        }
    }
}
