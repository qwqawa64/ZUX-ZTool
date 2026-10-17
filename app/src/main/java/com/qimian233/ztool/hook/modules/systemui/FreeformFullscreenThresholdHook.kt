package com.qimian233.ztool.hook.modules.systemui

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
 * Replaces the SystemUI freeform corner-resize fullscreen trigger with user percentages.
 *
 * SystemUI 17.5.27 hosts `OvfDpcUtility` and the live `changeBounds` logic in
 * `DragPositioningCallbackUtility`; this hook raises the max bounds and replaces the
 * stable/safe-margin rect with the full display, then rewrites the trigger result.
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
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

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

        val utilityClass = loadClass(loader, UTILITY_CLASS) ?: return
        val dragUtilityClass = loadClass(loader, DRAG_UTILITY_CLASS) ?: return
        val displayControllerClass =
            loadClass(loader, "com.android.wm.shell.common.DisplayController") ?: return
        val windowDecorationClass =
            loadClass(loader, "com.android.wm.shell.windowdecor.WindowDecoration") ?: return
        val decorClass = loadClass(
            loader,
            "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        ) ?: return
        val layoutInfoClass = loadClass(loader, "com.android.internal.ov.OvfDragLayoutInfo") ?: return

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

        val changeBounds = try {
            findMethod(
                dragUtilityClass,
                "changeBounds",
                Int::class.javaPrimitiveType,
                Rect::class.java,
                Rect::class.java,
                Rect::class.java,
                PointF::class.java,
                displayControllerClass,
                windowDecorationClass
            )
        } catch (t: Throwable) {
            logger.error("DragPositioningCallbackUtility.changeBounds not found", t)
            return
        }

        val triggerResultField = field(utilityClass, "triggerResult") ?: return
        val veilAddedField = field(utilityClass, "blackVeilAdded")
        val dragLayoutInfoField = utilityClass.declaredFields.firstOrNull {
            it.type.name == layoutInfoClass.name
        } ?: run {
            logger.warn("OvfDpcUtility.dragLayoutInfo field missing")
            return
        }
        dragLayoutInfoField.isAccessible = true
        val utilityField = field(decorClass, "ovfDpcUtility") ?: return
        val hideVeil = method(decorClass, "ovfHideFullBlackVeil")
        val maxField = field(layoutInfoClass, "maxWinSizeWhEdge") ?: return
        val safeMarginField = field(layoutInfoClass, "safeMargin")

        hookWithId(changeBounds, "freeform_fullscreen_systemui") { chain ->
            try {
                val decor = chain.args.getOrNull(6)
                val utility = decor?.let { utilityField.get(it) }
                if (decor == null || utility == null) {
                    logger.warn("Freeform fullscreen changeBounds has null decor/utility")
                    return@hookWithId chain.proceed()
                }
                val rect = chain.args.getOrNull(1) as? Rect
                if (rect == null) {
                    logger.warn("Freeform fullscreen changeBounds has null rect")
                    return@hookWithId chain.proceed()
                }

                val metrics = Resources.getSystem().displayMetrics
                val targetWidth =
                    if (widthEnabled) (metrics.widthPixels * widthRatio).toInt()
                    else metrics.widthPixels
                val targetHeight =
                    if (heightEnabled) (metrics.heightPixels * heightRatio).toInt()
                    else metrics.heightPixels
                val layoutInfo = dragLayoutInfoField.get(utility)
                val max = maxField.get(layoutInfo) as? Point
                if (max != null) {
                    max.x = targetWidth
                    max.y = targetHeight
                    if (!maxSizeLogged) {
                        maxSizeLogged = true
                        logger.info(
                            "Freeform fullscreen max size first call: " +
                                "max=${max.x}x${max.y}, " +
                                "screen=${metrics.widthPixels}x${metrics.heightPixels}"
                        )
                    }
                } else {
                    logger.warn("OvfDpcUtility.maxWinSizeWhEdge value is null")
                }

                // The host clamps to the stable rect minus safeMargin; replace both with the
                // full display so the window can actually cover the whole screen.
                safeMarginField?.setInt(layoutInfo, 0)
                val fullBounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
                val args = chain.args.toMutableList()
                if (args.size > 3) {
                    args[3] = fullBounds
                }
                val result = chain.proceed(args.toTypedArray())

                val ctrlType = (chain.args.getOrNull(0) as? Number)?.toInt() ?: 0
                val widthHit = ctrlType != 0 && widthEnabled &&
                    rect.width() >= targetWidth
                val heightHit = ctrlType != 0 && heightEnabled &&
                    rect.height() >= targetHeight
                val trigger = widthHit || heightHit
                val oldBits = triggerResultField.getInt(utility)
                val newBits = (oldBits and (1 or 8)) or (if (trigger) 4 else 0)
                triggerResultField.setInt(utility, newBits)

                if (!trigger && veilAddedField != null &&
                    veilAddedField.getBoolean(utility)
                ) {
                    try {
                        hideVeil?.invoke(decor)
                        veilAddedField.setBoolean(utility, false)
                    } catch (t: Throwable) {
                        logger.warn("Failed to hide freeform fullscreen veil: ${t.message}")
                    }
                }

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
                result
            } catch (t: Throwable) {
                logger.error("Freeform fullscreen changeBounds check failed", t)
                chain.proceed()
            }
        }

        logger.info(
            "Freeform fullscreen threshold installed: SystemUI changeBounds, " +
                "width=$widthPercent%, height=$heightPercent%"
        )
    }

    private fun loadClass(loader: ClassLoader, name: String): Class<*>? {
        return try {
            loader.loadClass(name)
        } catch (t: Throwable) {
            logger.error("$name not found", t)
            null
        }
    }

    private fun field(owner: Class<*>, name: String): Field? {
        return try {
            findField(owner, name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.error("$owner.$name field missing", t)
            null
        }
    }

    private fun method(owner: Class<*>, name: String): java.lang.reflect.Method? {
        return try {
            findMethod(owner, name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.error("$owner.$name method missing", t)
            null
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
        const val DRAG_UTILITY_CLASS =
            "com.android.wm.shell.windowdecor.DragPositioningCallbackUtility"
        const val MIN_PERCENT = 50
        const val MAX_PERCENT = 100
    }
}
