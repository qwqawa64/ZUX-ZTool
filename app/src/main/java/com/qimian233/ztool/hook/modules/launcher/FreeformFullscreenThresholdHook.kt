package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

/**
 * Replaces the launcher-side freeform corner-resize fullscreen trigger with user percentages.
 *
 * The host checks the window area (phone) or fixed width/height ratios (tablet) inside
 * `OvfDpcUtility`; this hook triggers fullscreen as soon as either the configured
 * display-width or display-height percentage is reached, whichever comes first.
 * A 100% dimension is disabled; both at 100% keeps the window out of fullscreen.
 */
@SuppressLint("PrivateApi")
class FreeformFullscreenThresholdHook : AppHookModule() {
    override fun getModuleName(): String =
        PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        if (!isPad(loader)) {
            logger.info("Freeform fullscreen threshold skipped: not a pad build")
            return
        }
        val utilityClass = runCatching { loader.loadClass(UTILITY_CLASS) }.getOrNull()
        if (utilityClass == null) {
            logger.warn("$UTILITY_CLASS not found; skip freeform fullscreen threshold")
            return
        }

        val prefs = remotePreferences
        val widthPercent = prefs.getInt(
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT.name,
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT.default
        ).coerceIn(MIN_PERCENT, MAX_PERCENT)
        val heightPercent = prefs.getInt(
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT.name,
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT.default
        ).coerceIn(MIN_PERCENT, MAX_PERCENT)
        val widthRatio = widthPercent / 100f
        val heightRatio = heightPercent / 100f
        val widthEnabled = widthPercent < MAX_PERCENT
        val heightEnabled = heightPercent < MAX_PERCENT

        val displayLayoutClass = runCatching {
            loader.loadClass("com.android.wm.shell.common.DisplayLayout")
        }.getOrNull()
        if (displayLayoutClass == null) {
            logger.warn("DisplayLayout not found; skip freeform fullscreen threshold")
            return
        }

        val checks = utilityClass.declaredMethods.filter { method ->
            !method.isSynthetic && !method.isBridge &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == displayLayoutClass &&
                method.parameterTypes[1] == Rect::class.java &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == PointF::class.java
        }
        if (checks.isEmpty()) {
            logger.warn("OvfDpcUtility fullscreen check not found; skip")
            return
        }

        checks.forEachIndexed { index, method ->
            hookWithId(method, "freeform_fullscreen_check_${method.name}_$index") { chain ->
                val rect = chain.args[1] as? Rect
                val ctrlType = (chain.args[2] as? Number)?.toInt() ?: 0
                if (rect == null) {
                    return@hookWithId chain.proceed()
                }
                if (ctrlType == 0) {
                    return@hookWithId false
                }
                if (!widthEnabled && !heightEnabled) {
                    return@hookWithId false
                }
                val metrics = Resources.getSystem().displayMetrics
                (widthEnabled && rect.width() >= metrics.widthPixels * widthRatio) ||
                    (heightEnabled && rect.height() >= metrics.heightPixels * heightRatio)
            }
        }

        installMaxSizeOverride(
            loader,
            utilityClass,
            widthRatio,
            heightRatio,
            widthEnabled,
            heightEnabled
        )
        logger.info(
            "Freeform fullscreen threshold installed: " +
                "width=$widthPercent%, height=$heightPercent%"
        )
    }

    /** Raises the host's max window bounds to the same thresholds before bounds are computed. */
    private fun installMaxSizeOverride(
        loader: ClassLoader,
        utilityClass: Class<*>,
        widthRatio: Float,
        heightRatio: Float,
        widthEnabled: Boolean,
        heightEnabled: Boolean
    ) {
        val infoField: Field = utilityClass.declaredFields.firstOrNull {
            it.type.name == "com.android.internal.ov.OvfDragLayoutInfo"
        } ?: run {
            logger.warn("OvfDragLayoutInfo field missing; max window size left unchanged")
            return
        }
        infoField.isAccessible = true
        val maxField = runCatching {
            infoField.type.getField("maxWinSizeWhEdge").apply { isAccessible = true }
        }.getOrNull() ?: return
        val minField = runCatching {
            infoField.type.getField("minWinSize").apply { isAccessible = true }
        }.getOrNull()
        val decorClass = runCatching {
            loader.loadClass("com.android.wm.shell.windowdecor.DesktopModeWindowDecoration")
        }.getOrNull() ?: return
        val displayControllerClass = runCatching {
            loader.loadClass("com.android.wm.shell.common.DisplayController")
        }.getOrNull() ?: return
        val changeBounds = runCatching {
            findMethod(
                utilityClass,
                "changeBounds",
                decorClass,
                displayControllerClass,
                Rect::class.java,
                Rect::class.java,
                PointF::class.java
            )
        }.getOrNull() ?: return

        hookWithId(changeBounds, "freeform_fullscreen_max_size") { chain ->
            val info = infoField.get(chain.thisObject)
            val max = maxField.get(info) as? Point
            if (max != null) {
                val metrics = Resources.getSystem().displayMetrics
                val min = minField?.get(info) as? Point
                val targetWidth =
                    if (widthEnabled) (metrics.widthPixels * widthRatio).toInt()
                    else metrics.widthPixels
                val targetHeight =
                    if (heightEnabled) (metrics.heightPixels * heightRatio).toInt()
                    else metrics.heightPixels
                max.x = maxOf(targetWidth, min?.x ?: 0)
                max.y = maxOf(targetHeight, min?.y ?: 0)
            }
            chain.proceed()
        }
    }

    private fun isPad(loader: ClassLoader): Boolean = runCatching {
        loader.loadClass("android.util.OvcFeatureFlags")
            .getField("isPad")
            .getBoolean(null)
    }.getOrDefault(false)

    private companion object {
        const val UTILITY_CLASS = "com.android.wm.shell.windowdecor.OvfDpcUtility"
        const val MIN_PERCENT = 50
        const val MAX_PERCENT = 100
    }
}
