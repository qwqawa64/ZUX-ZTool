package com.qimian233.ztool.hook.modules.systemui.misc

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Fixes the ZUI control-center "delayed rebound": after the shade flings open
 * to full screen, the visible overExpansion spring-back starts ~1.6s late.
 *
 * Root causes measured on device (see ShadeReboundTimingHook logs):
 *
 *  1. NPVC.onLayoutChange -> cancelHeightAnimator cancels the expanding
 *     SpringAnimation near its target (curH 996/1000), and the same layout
 *     pass re-fires fling() with ~zero velocity. The re-fired spring creeps
 *     the last few px for ~900ms before its EndListener fires.
 *  2. The ZUI rebound spring runs stiffness=100, dampingRatio=0.72; a soft
 *     spring's EndListener only fires after full exponential convergence, so
 *     springBack() (the visible rebound) waits out the long decay tail.
 *
 * Fix (keeping the ZUI spring feel):
 *  - cancelHeightAnimator: block cancels while the expanding height spring
 *    (mHeightSpringAnimator) is still running, so the layout pass cannot
 *    cancel-and-refire the fling.
 *  - SpringForce stiffness: the rebound spring is built with stiffness 100;
 *    after a SpringForce.setStiffness call on a spring matching the ZUI
 *    rebound signature (stiffness 100, damping 0.72), rewrite the stiffness
 *    to the user-configured value (default 500) so the spring converges
 *    quickly and springBack starts right after the panel visually fills.
 *
 * All androidx.dynamicanimation types are accessed reflectively because the
 * app module does not depend on that library. Hooks are scoped to
 * com.android.systemui.
 */
class ShadeReboundFix : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SHADE_REBOUND_FIX.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return

        val cl = param.defaultClassLoader
        val npvcClass = try {
            cl.loadClass("com.android.systemui.shade.NotificationPanelViewController")
        } catch (t: Throwable) {
            logger.error("ShadeReboundFix: NPVC class not found", t)
            return
        }

        val stiffness = readStiffness().toFloat()
        logger.info("ShadeReboundFix installing, stiffness=$stiffness")

        // ---- Fix 1: protect the expanding height spring from layout-cancel ----
        // Measured call chain of the bad cancel:
        //   onLayoutChange -> cancelHeightAnimator -> SpringAnimation.cancel
        // Gate on the caller stack containing "onLayoutChange" instead of
        // reading mHeightSpringAnimator: SpringUI is R8-obfuscated and field
        // names may be renamed on other builds. All other cancel paths
        // (user touch, collapse, etc.) pass through untouched.
        try {
            val cancelHeightAnimator = findMethod(npvcClass, "cancelHeightAnimator")
            hookWithId(cancelHeightAnimator, "rebound_cancel_guard") { chain ->
                val fromLayoutChange = Throwable().stackTrace.any {
                    it.className.endsWith("NotificationPanelViewController") &&
                        it.methodName.contains("onLayoutChange")
                }
                if (fromLayoutChange) {
                    logger.debug("cancelHeightAnimator blocked: onLayoutChange cancel")
                    return@hookWithId null
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundFix: hook cancelHeightAnimator failed", t)
        }

        // ---- Fix 2: speed up the rebound spring convergence ----
        // flingToHeight builds: new SpringAnimation(new FloatValueHolder(h))
        //   spring = SpringForce(target).setDampingRatio(0.72f).setStiffness(100f)
        // SystemUI is R8-obfuscated, so SpringForce field names (mStiffness etc.)
        // are NOT reliable. Instead, rewrite the setStiffness argument itself,
        // gated on the caller stack containing NPVC.flingToHeight (class names
        // are kept in this ROM). Other setStiffness callers pass through
        // untouched.
        try {
            val springForceClass = cl.loadClass("androidx.dynamicanimation.animation.SpringForce")
            val setStiffness = springForceClass.getMethod(
                "setStiffness", Float::class.javaPrimitiveType!!
            )
            hookWithId(setStiffness, "rebound_spring_stiffness") { chain ->
                val fromFlingToHeight = Throwable().stackTrace.any {
                    it.className.endsWith("NotificationPanelViewController") &&
                        it.methodName.contains("flingToHeight")
                }
                if (fromFlingToHeight) {
                    logger.debug("Rebound spring stiffness -> $stiffness")
                    chain.proceed(arrayOf<Any>(stiffness))
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundFix: hook SpringForce.setStiffness failed", t)
        }

        logger.info("ShadeReboundFix installed")
    }

    private fun readStiffness(): Int {
        val raw = try {
            remotePreferences.getInt(
                PreferenceKeys.SHADE_REBOUND_STIFFNESS.name,
                PreferenceKeys.SHADE_REBOUND_STIFFNESS.default
            )
        } catch (_: Throwable) {
            PreferenceKeys.SHADE_REBOUND_STIFFNESS.default
        }
        return raw.coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)
    }

    companion object {
        private const val MIN_STIFFNESS = 100
        private const val MAX_STIFFNESS = 2000
    }
}
