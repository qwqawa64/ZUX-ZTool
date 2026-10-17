package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.res.Resources
import android.graphics.Rect
import android.view.MotionEvent
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Makes the freeform bottom edge start a normal resize instead of the quick-swipe gesture.
 *
 * SystemUI treats ctrlType 8 as the bottom gesture bar and returns before
 * `onDragPositioningStart`; returning 8|16 marks the same edge while bypassing that check.
 * A fast upward drag closes the task; a fast downward drag calls moveToFullscreen directly.
 */
@SuppressLint("PrivateApi")
class FreeformBottomEdgeResizeHook : AppHookModule() {
    @Volatile
    private var transformedLogged = false

    private val velocityByTaskId = ConcurrentHashMap<Int, Float>()

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
        try {
            installVelocityClose(loader)
        } catch (t: Throwable) {
            logger.error("Freeform bottom edge velocity install failed", t)
        }
        logger.info("Freeform bottom edge resize installed")
    }

    private fun installVelocityClose(loader: ClassLoader) {
        logger.info("Installing freeform bottom edge velocity close")
        val injectorClass = loadClass(loader, INJECTOR_CLASS) ?: return
        val positionerClass = loadClass(loader, POSITIONER_CLASS) ?: return
        val decorationClass = loadClass(loader, DECORATION_CLASS) ?: return

        val injectorCtrl = field(injectorClass, "mCtrlType") ?: return
        val injectorTaskInfo = field(injectorClass, "mTaskInfo") ?: return
        val dragStartY = field(injectorClass, "mDragStartY") ?: return
        val quickSwipeStart = field(injectorClass, "mQuickSwipeStartTimeMs") ?: return
        val touchSlop = field(injectorClass, "SCALED_TOUCH_SLOP") ?: return
        val positionerCtrl = field(positionerClass, "mCtrlType") ?: return
        val positionerDecoration = field(positionerClass, "mDesktopWindowDecoration") ?: return
        val positionerTransitions = field(positionerClass, "mTransitions") ?: return
        val positionerBounds = field(positionerClass, "mRepositionTaskBounds") ?: return
        val decorationTaskInfo = field(decorationClass, "mTaskInfo") ?: return

        val injectorMethod = try {
            findMethod(
                injectorClass,
                "handleDragGestureEvent",
                Float::class.javaPrimitiveType,
                MotionEvent::class.java
            )
        } catch (t: Throwable) {
            logger.error("OvfDriListenerInjector.handleDragGestureEvent not found", t)
            return
        }
        val endMethod = try {
            findMethod(
                positionerClass,
                "onDragPositioningEnd",
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
        } catch (t: Throwable) {
            logger.error("VeiledResizeTaskPositioner.onDragPositioningEnd not found", t)
            return
        }

        val closeVelocity = CLOSE_SPEED_DP_PER_SECOND *
            Resources.getSystem().displayMetrics.density
        logger.info(
            "Freeform bottom edge close threshold: " +
                "${CLOSE_SPEED_DP_PER_SECOND}dp/s (${closeVelocity}px/s)"
        )

        hookWithId(injectorMethod, "freeform_bottom_velocity_track") { chain ->
            try {
                val injector = chain.thisObject
                if (injector == null) return@hookWithId chain.proceed()
                val ctrlType: Int = injectorCtrl.getInt(injector)
                if ((ctrlType and CTRL_TYPE_PROPORTIONAL_FLAG) == 0) {
                    return@hookWithId chain.proceed()
                }
                val event = chain.args.getOrNull(1) as? MotionEvent
                    ?: return@hookWithId false
                val rawY = (chain.args.getOrNull(0) as? Number)?.toFloat()
                    ?: event.rawY
                val taskInfo = injectorTaskInfo.get(injector)
                    as? ActivityManager.RunningTaskInfo
                val taskId = taskInfo?.taskId
                if (taskId != null) {
                    var start = quickSwipeStart.getLong(injector)
                    val startY = dragStartY.getFloat(injector)
                    if (start == 0L &&
                        abs(rawY - startY) > touchSlop.getInt(injector)
                    ) {
                        start = event.eventTime
                        quickSwipeStart.setLong(injector, start)
                    }
                    when (event.actionMasked) {
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                            if (start != 0L) {
                                val elapsed = event.eventTime - start
                                if (elapsed > 0) {
                                    velocityByTaskId[taskId] =
                                        (rawY - startY) * 1000f / elapsed
                                }
                            }
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                quickSwipeStart.setLong(injector, 0L)
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            velocityByTaskId.remove(taskId)
                            quickSwipeStart.setLong(injector, 0L)
                        }
                    }
                }
                false
            } catch (t: Throwable) {
                logger.error("Freeform bottom edge velocity tracking failed", t)
                false
            }
        }

        hookWithId(endMethod, "freeform_bottom_velocity_close") { chain ->
            try {
                val positioner = chain.thisObject
                if (positioner == null) return@hookWithId chain.proceed()
                val ctrlType: Int = positionerCtrl.getInt(positioner)
                val decoration = positionerDecoration.get(positioner)
                val taskInfo = decoration?.let { decorationTaskInfo.get(it) }
                    as? ActivityManager.RunningTaskInfo
                val velocity = taskInfo?.let { velocityByTaskId.remove(it.taskId) } ?: 0f
                val bounds = readBounds(positionerBounds, positioner)
                logger.info(
                    "Freeform bottom edge end: taskId=${taskInfo?.taskId}, " +
                        "ctrlType=0x${Integer.toHexString(ctrlType)}, " +
                        "velocity=${velocity.toInt()}px/s, bounds=$bounds"
                )
                if ((ctrlType and CTRL_TYPE_PROPORTIONAL_FLAG) != 0 &&
                    taskInfo != null &&
                    abs(velocity) >= closeVelocity
                ) {
                    resetResizeVeil(positioner)
                    if (velocity > 0f) {
                        if (fullscreenTask(positioner, taskInfo)) {
                            logger.info(
                                "Freeform bottom edge fast drag fullscreens task: " +
                                    "taskId=${taskInfo.taskId}, velocity=${velocity.toInt()}px/s"
                            )
                            return@hookWithId bounds ?: Rect()
                        }
                    } else if (closeTask(
                            positioner,
                            taskInfo,
                            positionerTransitions,
                            bounds
                        )
                    ) {
                        logger.info(
                            "Freeform bottom edge fast drag closes task: " +
                                "taskId=${taskInfo.taskId}, velocity=${velocity.toInt()}px/s"
                        )
                        return@hookWithId bounds ?: Rect()
                    }
                }
            } catch (t: Throwable) {
                logger.error("Freeform bottom edge velocity close failed", t)
            }
            chain.proceed()
        }
    }

    private fun readBounds(field: Field, target: Any): Rect? =
        try {
            val value: Any? = field.get(target)
            value as? Rect
        } catch (t: Throwable) {
            logger.warn("Failed to read reposition bounds: ${t.message}")
            null
        }

    private fun resetResizeVeil(positioner: Any) {
        try {
            val reset = positioner.javaClass.methods.firstOrNull {
                it.name == "resetVeilIfVisible$2" && it.parameterTypes.isEmpty()
            }
            if (reset != null) {
                reset.isAccessible = true
                reset.invoke(positioner)
                logger.debug("Freeform bottom edge resize veil reset")
            } else {
                logger.warn("resetVeilIfVisible$2 not found")
            }
        } catch (t: Throwable) {
            logger.warn("Failed to reset resize veil: ${t.message}")
        }
    }

    private fun fullscreenTask(
        positioner: Any,
        taskInfo: ActivityManager.RunningTaskInfo
    ): Boolean {
        return try {
            logger.info("Fullscreen attempt: taskId=${taskInfo.taskId}")
            val controllerAccessor = positioner.javaClass.methods.firstOrNull {
                it.name == "ovGetDesktopTasksController" && it.parameterTypes.isEmpty()
            }
            if (controllerAccessor == null) {
                logger.warn("ovGetDesktopTasksController not found on ${positioner.javaClass.name}")
                return false
            }
            val controller = controllerAccessor.invoke(positioner)
            if (controller == null) {
                logger.warn("DesktopTasksController is null for task ${taskInfo.taskId}")
                return false
            }
            val sourceClass = Class.forName(
                "com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource"
            )
            val source = sourceClass.getField("TASK_DRAG").get(null)
            val moveToFullscreen = controller.javaClass.methods.firstOrNull {
                it.name == "moveToFullscreen" && it.parameterTypes.size == 2
            }
            if (moveToFullscreen == null) {
                logger.warn("moveToFullscreen not found on ${controller.javaClass.name}")
                return false
            }
            moveToFullscreen.isAccessible = true
            moveToFullscreen.invoke(controller, taskInfo.taskId, source)
            logger.info("Fullscreen requested: taskId=${taskInfo.taskId}")
            true
        } catch (t: Throwable) {
            logger.error("Failed to fullscreen freeform task ${taskInfo.taskId}", t)
            false
        }
    }

    private fun closeTask(
        positioner: Any,
        taskInfo: ActivityManager.RunningTaskInfo,
        transitionsField: Field,
        bounds: Rect?
    ): Boolean {
        try {
            val service = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService")
                .invoke(null)
            service.javaClass
                .getMethod("removeTask", Int::class.javaPrimitiveType)
                .invoke(service, taskInfo.taskId)
            return true
        } catch (t: Throwable) {
            logger.warn("Direct task close failed, falling back to transition: ${t.message}")
        }
        val transaction = try {
            Class.forName("android.window.WindowContainerTransaction")
                .getConstructor()
                .newInstance()
        } catch (t: Throwable) {
            logger.error("WindowContainerTransaction unavailable", t)
            null
        } ?: return false
        val token = taskToken(taskInfo) ?: return false
        try {
            val transactionClass = Class.forName("android.window.WindowContainerTransaction")
            val tokenClass = Class.forName("android.window.WindowContainerToken")
            if (bounds != null) {
                // Commit the live resize bounds before removal so the close transition
                // does not replay the pre-drag bounds.
                transactionClass
                    .getMethod("setBounds", tokenClass, Rect::class.java)
                    .invoke(transaction, token, bounds)
            }
            transactionClass
                .getMethod("removeTask", tokenClass)
                .invoke(transaction, token)
        } catch (t: Throwable) {
            logger.error("Failed to build removeTask transaction", t)
            return false
        }
        val transitions = transitionsField.get(positioner)
        if (transitions != null) {
            try {
                val start = transitions.javaClass.methods.firstOrNull {
                    it.name == "startTransition" && it.parameterTypes.size == 3
                }
                if (start != null) {
                    start.isAccessible = true
                    start.invoke(transitions, REMOVE_TRANSITION, transaction, null)
                    return true
                }
            } catch (t: Throwable) {
                logger.warn("Remove transition failed, falling back to ATM: ${t.message}")
            }
        }
        return try {
            val service = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService")
                .invoke(null)
            service.javaClass
                .getMethod("removeTask", Int::class.javaPrimitiveType)
                .invoke(service, taskInfo.taskId)
            true
        } catch (t: Throwable) {
            logger.error("Failed to close freeform task ${taskInfo.taskId}", t)
            false
        }
    }

    private fun taskToken(taskInfo: Any): Any? {
        var type: Class<*>? = taskInfo.javaClass
        while (type != null) {
            try {
                val tokenField = type.getDeclaredField("token")
                tokenField.isAccessible = true
                return tokenField.get(taskInfo)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            } catch (t: Throwable) {
                logger.error("Failed to read task token", t)
                return null
            }
        }
        return null
    }

    private fun loadClass(loader: ClassLoader, name: String): Class<*>? =
        try {
            loader.loadClass(name)
        } catch (t: Throwable) {
            logger.error("$name not found", t)
            null
        }

    private fun field(owner: Class<*>, name: String): Field? =
        try {
            findField(owner, name)
        } catch (t: Throwable) {
            logger.error("Field $name not found on ${owner.name}", t)
            null
        }

    private companion object {
        const val GEOMETRY_CLASS = "com.android.wm.shell.windowdecor.DragResizeWindowGeometry"
        const val INJECTOR_CLASS = "com.android.wm.shell.windowdecor.OvfDriListenerInjector"
        const val POSITIONER_CLASS = "com.android.wm.shell.windowdecor.VeiledResizeTaskPositioner"
        const val DECORATION_CLASS = "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        const val CTRL_TYPE_BOTTOM = 8
        const val CTRL_TYPE_BOTTOM_PROPORTIONAL = 8 or 16
        const val CTRL_TYPE_PROPORTIONAL_FLAG = 16
        const val REMOVE_TRANSITION = 2
        // Original freeform quick-swipe threshold.
        const val CLOSE_SPEED_DP_PER_SECOND = 100f
    }
}
