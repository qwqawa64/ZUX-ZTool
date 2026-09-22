package com.qimian233.ztool.hook.modules.systemui.misc

import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Timing instrumentation for the ZUI shade rebound investigation.
 *
 * ZUI splits the "shade fills the screen" fling into two chained animations
 * inside com.android.systemui.shade.NotificationPanelViewController:
 *
 *   endMotionEvent -> fling() -> flingToHeight()
 *     -> [QsReboundFeature branch] SpringAnimation(stiffness=100, damping=0.72)
 *        driving mExpandedHeight; its EndListener fires only after the spring
 *        fully converges
 *     -> EndListener -> springBack() (400ms FAST_OUT_SLOW_IN anim collapsing
 *        mOverExpansion back to 0, the rebound the user actually sees)
 *
 * The visible rebound therefore starts only after the soft spring's tail has
 * decayed, which is the suspected cause of the "delayed rebound" complaint.
 * This hook logs wall-clock timestamps (elapsedRealtime) at each milestone so
 * the fling->springBack interval can be measured on device:
 *
 *   endMotionEvent | flingToHeight(entry/exit) | springBack(entry/exit)
 *   onFlingEnd
 *
 * Registered as a hook_test module (TEST_HOOK auto-enables, no frontend
 * switch). Read logcat tag "ZTool-Rebound".
 */
class ShadeReboundTimingHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return

        val cl = param.defaultClassLoader
        val npvcClass = try {
            cl.loadClass("com.android.systemui.shade.NotificationPanelViewController")
        } catch (t: Throwable) {
            logger.error("ShadeReboundTimingHook: NPVC class not found", t)
            return
        }

        // 1. endMotionEvent (synthetic access bridge) — touch release timestamp
        try {
            val endMotionEvent = npvcClass.declaredMethods.first {
                it.name.endsWith("endMotionEvent") ||
                    it.name.contains("endMotionEvent")
            }
            hookWithId(endMotionEvent, "rebound_end_motion_event") { chain ->
                logTs("endMotionEvent start")
                chain.proceed()
                logTs("endMotionEvent end")
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundTimingHook: hook endMotionEvent failed", t)
        }

        // 2. flingToHeight — fling animation start
        try {
            val flingToHeight = findMethod(
                npvcClass, "flingToHeight",
                Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!
            )
            hookWithId(flingToHeight, "rebound_fling_to_height") { chain ->
                val target = chain.args[2]
                val expanding = chain.args[1]
                val overExpansion = readFloat(npvcClass, chain.thisObject, "mOverExpansion")
                val expandedHeight = readFloat(npvcClass, chain.thisObject, "mExpandedHeight")
                logTs(
                    "flingToHeight start expand=$expanding targetH=$target " +
                        "curH=$expandedHeight overExp=$overExpansion"
                )
                val t0 = SystemClock.elapsedRealtime()
                chain.proceed()
                logTs("flingToHeight exit dt=${SystemClock.elapsedRealtime() - t0}ms")
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundTimingHook: hook flingToHeight failed", t)
        }

        // 3. springBack — the visible rebound start
        try {
            val springBack = findMethod(npvcClass, "springBack")
            hookWithId(springBack, "rebound_spring_back") { chain ->
                val overExpansion = readFloat(npvcClass, chain.thisObject, "mOverExpansion")
                logTs("springBack start overExp=$overExpansion")
                val t0 = SystemClock.elapsedRealtime()
                chain.proceed()
                logTs("springBack exit dt=${SystemClock.elapsedRealtime() - t0}ms")
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundTimingHook: hook springBack failed", t)
        }

        // 4. onFlingEnd — terminal milestone
        try {
            val onFlingEnd = findMethod(npvcClass, "onFlingEnd", Boolean::class.javaPrimitiveType!!)
            hookWithId(onFlingEnd, "rebound_on_fling_end") { chain ->
                logTs("onFlingEnd cancelled=${chain.args[0]}")
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("ShadeReboundTimingHook: hook onFlingEnd failed", t)
        }

        logger.info("ShadeReboundTimingHook installed (tag ZTool-Rebound)")
    }

    private fun readFloat(clazz: Class<*>, thisObject: Any?, fieldName: String): Float {
        return if (thisObject == null) {
            Float.NaN
        } else {
            try {
                findField(clazz, fieldName).getFloat(thisObject)
            } catch (_: Throwable) {
                Float.NaN
            }
        }
    }

    private fun logTs(message: String) {
        val ts = SystemClock.elapsedRealtime()
        // Module log for LSPosed manager + android.util.Log for immediate logcat reading
        logger.debug("[$ts] $message")
        android.util.Log.i("ZTool-Rebound", "[$ts] $message")
    }
}
