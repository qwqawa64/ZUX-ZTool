package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Makes the freeform bottom edge start a normal resize instead of the quick-swipe gesture.
 *
 * SystemUI treats ctrlType 8 as the bottom gesture bar and returns before
 * `onDragPositioningStart`; returning 8|16 marks the same edge while bypassing that check.
 */
@SuppressLint("PrivateApi")
class FreeformBottomEdgeResizeHook : AppHookModule() {
    @Volatile
    private var transformedLogged = false

    override fun getModuleName(): String =
        PreferenceKeys.SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("Freeform bottom edge resize install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val prefs = remotePreferences
        val thresholdEnabled = prefs.getBoolean(
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD.name,
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD.default
        )
        if (!thresholdEnabled) {
            logger.info("Freeform bottom edge resize skipped: fullscreen threshold disabled")
            return
        }
        val geometryClass = try {
            loader.loadClass(GEOMETRY_CLASS)
        } catch (t: Throwable) {
            logger.error("$GEOMETRY_CLASS not found", t)
            return
        }
        val calculateCtrlType = try {
            findMethod(
                geometryClass,
                "calculateCtrlType",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
        } catch (t: Throwable) {
            logger.error("DragResizeWindowGeometry.calculateCtrlType not found", t)
            return
        }

        val isUnderPcMode = try {
            loader.loadClass("com.android.wm.shell.util.OvcShellUtils")
                .getMethod("isUnderPcMode")
        } catch (t: Throwable) {
            logger.warn("OvcShellUtils.isUnderPcMode not found; transform always: ${t.message}")
            null
        }

        hookWithId(calculateCtrlType, "freeform_bottom_edge_resize") { chain ->
            try {
                val ctrlType = chain.proceed() as? Int ?: 0
                val pcMode = isUnderPcMode?.invoke(null) as? Boolean ?: false
                if (ctrlType == CTRL_TYPE_BOTTOM && !pcMode) {
                    if (!transformedLogged) {
                        transformedLogged = true
                        logger.info(
                            "Freeform bottom edge transformed: ctrlType=$ctrlType -> $CTRL_TYPE_BOTTOM_PROPORTIONAL"
                        )
                    }
                    CTRL_TYPE_BOTTOM_PROPORTIONAL
                } else {
                    ctrlType
                }
            } catch (t: Throwable) {
                logger.error("Freeform bottom edge transform failed", t)
                chain.proceed()
            }
        }
        logger.info("Freeform bottom edge resize installed")
    }

    private companion object {
        const val GEOMETRY_CLASS = "com.android.wm.shell.windowdecor.DragResizeWindowGeometry"
        const val CTRL_TYPE_BOTTOM = 8
        const val CTRL_TYPE_BOTTOM_PROPORTIONAL = 8 or 16
    }
}
