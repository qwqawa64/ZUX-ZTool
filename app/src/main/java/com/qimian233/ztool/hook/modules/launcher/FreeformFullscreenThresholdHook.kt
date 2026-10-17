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
    @Volatile
    private var firstCheckLogged = false

    @Volatile
    private var triggerLogged = false

    @Volatile
    private var maxSizeLogged = false

    override fun getModuleName(): String =
        PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("Freeform fullscreen threshold install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        if (!isPad(loader)) {
            logger.info("Freeform fullscreen threshold skipped: not a pad build")
            return
        }
        val utilityClass = try {
            loader.loadClass(UTILITY_CLASS)
        } catch (t: Throwable) {
            logger.error("$UTILITY_CLASS not found", t)
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
        logger.info(
            "Freeform fullscreen threshold installing: " +
                "width=$widthPercent% (${if (widthEnabled) "on" else "off"}), " +
                "height=$heightPercent% (${if (heightEnabled) "on" else "off"})"
        )

        val displayLayoutClass = try {
            loader.loadClass("com.android.wm.shell.common.DisplayLayout")
        } catch (t: Throwable) {
            logger.error("DisplayLayout not found; skip freeform fullscreen threshold", t)
            return
        }

        val checks = try {
            utilityClass.declaredMethods.filter { method ->
                !method.isSynthetic && !method.isBridge &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == displayLayoutClass &&
                    method.parameterTypes[1] == Rect::class.java &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == PointF::class.java
            }
        } catch (t: Throwable) {
            logger.error("OvfDpcUtility method scan failed", t)
            return
        }
        if (checks.isEmpty()) {
            logger.warn("OvfDpcUtility fullscreen check not found; skip")
            return
        }

        checks.forEachIndexed { index, method ->
            hookWithId(method, "freeform_fullscreen_check_${method.name}_$index") { chain ->
                try {
                    val rect = chain.args[1] as? Rect
                    if (rect == null) {
                        logger.warn("Freeform fullscreen check got null rect")
                        return@hookWithId chain.proceed()
                    }
                    val ctrlType = (chain.args[2] as? Number)?.toInt() ?: 0
                    val metrics = Resources.getSystem().displayMetrics
                    val widthHit = ctrlType != 0 && widthEnabled &&
                        rect.width() >= metrics.widthPixels * widthRatio
                    val heightHit = ctrlType != 0 && heightEnabled &&
                        rect.height() >= metrics.heightPixels * heightRatio
                    val trigger = widthHit || heightHit
                    if (!firstCheckLogged) {
                        firstCheckLogged = true
                        logger.info(
                            "Freeform fullscreen check first call: ctrlType=0x" +
                                Integer.toHexString(ctrlType) +
                                ", rect=$rect, screen=${metrics.widthPixels}x${metrics.heightPixels}" +
                                ", widthHit=$widthHit, heightHit=$heightHit"
                        )
                    } else {
                        logger.debug(
                            "Freeform fullscreen check: ctrlType=0x" +
                                Integer.toHexString(ctrlType) +
                                ", rect=$rect, widthHit=$widthHit, heightHit=$heightHit"
                        )
                    }
                    if (trigger) {
                        if (!triggerLogged) {
                            triggerLogged = true
                            logger.info(
                                "Freeform fullscreen triggered: ctrlType=0x" +
                                    Integer.toHexString(ctrlType) +
                                    ", rect=$rect, widthHit=$widthHit, heightHit=$heightHit"
                            )
                        }
                    } else {
                        triggerLogged = false
                    }
                    trigger
                } catch (t: Throwable) {
                    logger.error("Freeform fullscreen check failed", t)
                    chain.proceed()
                }
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
                "checks=${checks.joinToString { it.name }}, " +
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
        try {
            val infoField: Field = utilityClass.declaredFields.firstOrNull {
                it.type.name == "com.android.internal.ov.OvfDragLayoutInfo"
            } ?: run {
                logger.warn("OvfDragLayoutInfo field missing; max window size left unchanged")
                return
            }
            infoField.isAccessible = true
            val maxField = try {
                infoField.type.getField("maxWinSizeWhEdge").apply { isAccessible = true }
            } catch (t: Throwable) {
                logger.error("maxWinSizeWhEdge field missing; max window size left unchanged", t)
                return
            }
            val minField = try {
                infoField.type.getField("minWinSize").apply { isAccessible = true }
            } catch (t: Throwable) {
                logger.warn("minWinSize field missing; using zero as the lower bound: ${t.message}")
                null
            }
            val decorClass = try {
                loader.loadClass("com.android.wm.shell.windowdecor.DesktopModeWindowDecoration")
            } catch (t: Throwable) {
                logger.error("DesktopModeWindowDecoration not found; max override skipped", t)
                return
            }
            val displayControllerClass = try {
                loader.loadClass("com.android.wm.shell.common.DisplayController")
            } catch (t: Throwable) {
                logger.error("DisplayController not found; max override skipped", t)
                return
            }
            val changeBounds = try {
                findMethod(
                    utilityClass,
                    "changeBounds",
                    decorClass,
                    displayControllerClass,
                    Rect::class.java,
                    Rect::class.java,
                    PointF::class.java
                )
            } catch (t: Throwable) {
                logger.error("OvfDpcUtility.changeBounds not found; max override skipped", t)
                return
            }

            hookWithId(changeBounds, "freeform_fullscreen_max_size") { chain ->
                try {
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
                        if (!maxSizeLogged) {
                            maxSizeLogged = true
                            logger.info(
                                "Freeform fullscreen max size first call: " +
                                    "max=${max.x}x${max.y}, " +
                                    "screen=${metrics.widthPixels}x${metrics.heightPixels}"
                            )
                        }
                    } else {
                        logger.warn("maxWinSizeWhEdge value is null; max override skipped")
                    }
                } catch (t: Throwable) {
                    logger.error("Freeform fullscreen max size override failed", t)
                }
                chain.proceed()
            }
            logger.info("Freeform fullscreen max size override installed")
        } catch (t: Throwable) {
            logger.error("Freeform fullscreen max size override install failed", t)
        }
    }

    private fun isPad(loader: ClassLoader): Boolean {
        return try {
            loader.loadClass("android.util.OvcFeatureFlags")
                .getField("isPad")
                .getBoolean(null)
        } catch (t: Throwable) {
            logger.warn("OvcFeatureFlags.isPad lookup failed; treating as non-pad: ${t.message}")
            false
        }
    }

    private companion object {
        const val UTILITY_CLASS = "com.android.wm.shell.windowdecor.OvfDpcUtility"
        const val MIN_PERCENT = 50
        const val MAX_PERCENT = 100
    }
}
