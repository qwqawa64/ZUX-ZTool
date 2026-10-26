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
import java.lang.reflect.Method

/**
 * Replaces the SystemUI freeform corner-resize fullscreen trigger with user percentages.
 *
 * On Android 17 the live logic runs inside DragPositioningCallbackUtility.changeBounds and
 * reaches OvfDpcUtility through WindowDecorationWrapper.ovWindowDecor; the legacy
 * WindowDecoration + seven-argument signature stays supported as a fallback.
 */
@SuppressLint("PrivateApi")
class FreeformFullscreenThresholdHook : AppHookModule() {
    @Volatile
    private var firstCheckLogged = false

    @Volatile
    private var triggerLogged = false

    @Volatile
    private var maxSizeLogged = false

    @Volatile
    private var bottomResizeLogged = false

    @Volatile
    private var utilityMissingLogged = false

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
        val bottomResizeEnabled = prefs.getBoolean(
            PreferenceKeys.SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE.name,
            PreferenceKeys.SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE.default
        )
        logger.info(
            "Freeform fullscreen threshold installing: " +
                "width=$widthPercent% (${if (widthEnabled) "on" else "off"}), " +
                "height=$heightPercent% (${if (heightEnabled) "on" else "off"})"
        )

        val target = resolveChangeBounds(loader, dragUtilityClass, displayControllerClass)
            ?: return
        val wrapperClass = if (target.usesWrapper) {
            loadClass(loader, WRAPPER_CLASS) ?: return
        } else {
            null
        }
        val ownerClass = if (target.usesWrapper) {
            loadClass(loader, OV_DECOR_CLASS) ?: return
        } else {
            loadClass(loader, DECOR_CLASS) ?: return
        }
        val wrapperDecorField = wrapperClass?.let { field(it, "ovWindowDecor") }
        if (target.usesWrapper && wrapperDecorField == null) {
            logger.error("WindowDecorationWrapper.ovWindowDecor field missing")
            return
        }
        val utilityField = field(ownerClass, "ovfDpcUtility") ?: return
        val hideVeil = method(ownerClass, "ovfHideFullBlackVeil")

        val triggerResultField = field(utilityClass, "triggerResult") ?: return
        val veilAddedField = field(utilityClass, "blackVeilAdded")
        val dragLayoutInfoField = utilityClass.declaredFields.firstOrNull {
            it.type.name == layoutInfoClass.name
        } ?: run {
            logger.warn("OvfDpcUtility.dragLayoutInfo field missing")
            return
        }
        dragLayoutInfoField.isAccessible = true
        val maxField = field(layoutInfoClass, "maxWinSizeWhEdge") ?: return
        val safeMarginField = field(layoutInfoClass, "safeMargin")
        val taskStartField = field(utilityClass, "mTaskBoundsAtDragStart")
        val finalBoundsField = field(utilityClass, "finalBounds")
        val minWinField = field(layoutInfoClass, "minWinSize")
        val minReboundField = field(layoutInfoClass, "minReboundWinSize")

        hookWithId(target.method, "freeform_fullscreen_systemui") { chain ->
            try {
                val decorationArg = chain.args.getOrNull(6)
                val owner = if (target.usesWrapper) {
                    decorationArg?.let { wrapperDecorField?.get(it) }
                } else {
                    decorationArg
                }
                val utility = if (owner != null) utilityField.get(owner) else null
                if (owner == null || utility == null) {
                    if (!utilityMissingLogged) {
                        utilityMissingLogged = true
                        logger.warn("Freeform fullscreen changeBounds has null decor/utility")
                    }
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
                if (bottomResizeEnabled && (ctrlType and 16) != 0) {
                    val start = taskStartField?.get(utility) as? Rect
                    val delta = chain.args.getOrNull(4) as? PointF
                    if (start != null && start.height() > 0 && delta != null) {
                        val aspect = start.width().toFloat() / start.height()
                        val minSize = (minReboundField?.get(layoutInfo) as? Point)
                            ?: (minWinField?.get(layoutInfo) as? Point)
                        val minWidth = ((minSize?.x ?: 1).coerceIn(1, metrics.widthPixels))
                        val minHeight = ((minSize?.y ?: 1).coerceIn(1, metrics.heightPixels))
                        val desiredHeight = (start.height() + delta.y).toInt()
                        val desiredWidth = (desiredHeight * aspect).toInt()
                        val top = start.top.coerceIn(0, metrics.heightPixels - 1)
                        if (desiredHeight <= minHeight || desiredWidth <= minWidth) {
                            // Corner handles bottom out at the rebound mini size; snap to it
                            // instead of the larger host minWinSize so shrinking can continue.
                            val height = minHeight
                            val width = minWidth
                            val safeTop = top.coerceIn(
                                0,
                                (metrics.heightPixels - height).coerceAtLeast(0)
                            )
                            val left = (start.centerX() - width / 2).coerceIn(
                                0,
                                (metrics.widthPixels - width).coerceAtLeast(0)
                            )
                            rect.set(left, safeTop, left + width, safeTop + height)
                            if (!bottomResizeLogged) {
                                bottomResizeLogged = true
                                logger.info(
                                    "Freeform bottom edge snap to mini window: rect=$rect"
                                )
                            }
                        } else {
                            val maxHeight = (metrics.heightPixels - top).coerceAtLeast(minHeight)
                            val height = desiredHeight.coerceIn(minHeight, maxHeight)
                            val width = (height * aspect).toInt()
                                .coerceIn(minWidth, metrics.widthPixels)
                            val left = (start.centerX() - width / 2).coerceIn(
                                0,
                                (metrics.widthPixels - width).coerceAtLeast(0)
                            )
                            rect.set(left, top, left + width, top + height)
                            if (!bottomResizeLogged) {
                                bottomResizeLogged = true
                                logger.info(
                                    "Freeform bottom edge proportional resize: " +
                                        "rect=$rect, aspect=$aspect"
                                )
                            }
                        }
                        (finalBoundsField?.get(utility) as? Rect)?.set(rect)
                    }
                }
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
                        hideVeil?.invoke(owner)
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
                "width=$widthPercent%, height=$heightPercent%, " +
                "bottomResize=$bottomResizeEnabled"
        )
    }

    /**
     * Resolves changeBounds with the Android 17 nine-argument signature first, then the legacy
     * seven-argument one. [DragTarget.usesWrapper] reports which decoration argument to expect.
     */
    private fun resolveChangeBounds(
        loader: ClassLoader,
        dragUtilityClass: Class<*>,
        displayControllerClass: Class<*>
    ): DragTarget? {
        val wrapperClass = runCatching { loader.loadClass(WRAPPER_CLASS) }.getOrNull()
        val resultClass = runCatching { loader.loadClass(CHANGE_BOUNDS_RESULT_CLASS) }.getOrNull()
        if (wrapperClass != null && resultClass != null) {
            val method = runCatching {
                findMethod(
                    dragUtilityClass,
                    "changeBounds",
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Rect::class.java,
                    Rect::class.java,
                    PointF::class.java,
                    displayControllerClass,
                    wrapperClass,
                    Boolean::class.javaPrimitiveType!!,
                    resultClass
                )
            }.getOrNull()
            if (method != null) {
                logger.debug("Freeform fullscreen using changeBounds/WindowDecorationWrapper")
                return DragTarget(method, true)
            }
        }
        val windowDecorationClass = runCatching {
            loader.loadClass("com.android.wm.shell.windowdecor.WindowDecoration")
        }.getOrNull()
        if (windowDecorationClass != null) {
            val method = runCatching {
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
            }.getOrNull()
            if (method != null) {
                logger.debug("Freeform fullscreen using legacy changeBounds/WindowDecoration")
                return DragTarget(method, false)
            }
        }
        logger.error("DragPositioningCallbackUtility.changeBounds not found")
        return null
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

    private fun method(owner: Class<*>, name: String): Method? {
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

    /** Resolved changeBounds entry point plus the decoration argument flavour it expects. */
    private class DragTarget(val method: Method, val usesWrapper: Boolean)

    private companion object {
        const val UTILITY_CLASS = "com.android.wm.shell.windowdecor.OvfDpcUtility"
        const val DRAG_UTILITY_CLASS =
            "com.android.wm.shell.windowdecor.DragPositioningCallbackUtility"
        const val WRAPPER_CLASS = "com.android.wm.shell.windowdecor.WindowDecorationWrapper"
        const val OV_DECOR_CLASS = "com.android.wm.shell.windowdecor.OvWindowDecoration"
        const val DECOR_CLASS = "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        const val CHANGE_BOUNDS_RESULT_CLASS =
            $$"com.android.wm.shell.windowdecor.DragPositioningCallbackUtility$ChangeBoundsResult"
        const val MIN_PERCENT = 50
        const val MAX_PERCENT = 100
    }
}
