package com.qimian233.ztool.hook.modules.systemui

import android.app.ActivityManager
import android.os.Handler
import android.os.SystemClock
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows the bottom-corner handles after creation or a corner resize, then hides them after 5 s.
 */
@Suppress("PrivateApi")
class FreeformCornerHandleIndicatorHook : AppHookModule() {
    private val layouts = ConcurrentHashMap<Int, View>()
    private val hideTokens = ConcurrentHashMap<Int, Long>()
    private val resizing = ConcurrentHashMap<Int, Boolean>()

    override fun getModuleName(): String = "system_ui_corner_handle_auto_hide"

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun isEnabled(): Boolean = true

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("Corner handle indicator install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        logger.info("Corner handle indicator install start")
        val loader = param.defaultClassLoader
        val hostClass = loadClass(loader, HOST_CLASS) ?: return
        val positionerClass = loadClass(loader, POSITIONER_CLASS) ?: return
        val decorationClass = loadClass(loader, DECORATION_CLASS) ?: return

        val setViewMethod = try {
            findMethod(
                hostClass,
                "setView",
                View::class.java,
                WindowManager.LayoutParams::class.java
            )
        } catch (t: Throwable) {
            logger.error("SurfaceControlViewHost.setView not found", t)
            return
        }
        val positionerCtrl = field(positionerClass, "mCtrlType") ?: return
        val positionerDecoration = field(positionerClass, "mDesktopWindowDecoration") ?: return
        val decorationTaskInfo = field(decorationClass, "mTaskInfo")
            ?: fieldByType(decorationClass, ActivityManager.RunningTaskInfo::class.java)
            ?: run {
                logger.error("RunningTaskInfo field not found")
                return
            }
        val startMethod = try {
            findMethod(
                positionerClass,
                "onDragPositioningStart",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        } catch (t: Throwable) {
            logger.error("onDragPositioningStart not found", t)
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
            logger.error("onDragPositioningEnd not found", t)
            return
        }

        hookWithId(setViewMethod, "corner_handle_capture") { chain ->
            val view = chain.args.getOrNull(0) as? View
            val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams
            val result = chain.proceed()
            if (view != null && params != null) {
                val title = params.title?.toString().orEmpty()
                val taskId = TASK_PATTERN.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (taskId != null && viewsReady(view)) {
                    layouts[taskId] = view
                    logger.info("Corner handle layout captured: task=$taskId")
                    val left = findView(view, "ovc_wdb_bottom_gesture_left")
                    if (left?.visibility != View.GONE) {
                        whenAttached(view) {
                            show(taskId)
                            scheduleHide(taskId)
                        }
                    }
                }
            }
            result
        }

        hookWithId(startMethod, "corner_handle_resize_show") { chain ->
            val result = chain.proceed()
            val positioner = chain.thisObject
            if (positioner != null) {
                val ctrlType = positionerCtrl.getInt(positioner)
                if (isBottomCorner(ctrlType)) {
                    val taskInfo = taskInfoOf(positioner, positionerDecoration, decorationTaskInfo)
                    if (taskInfo != null) {
                        resizing[taskInfo.taskId] = true
                        hideTokens.remove(taskInfo.taskId)
                        show(taskInfo.taskId)
                    }
                }
            }
            result
        }

        hookWithId(endMethod, "corner_handle_resize_hide") { chain ->
            val result = chain.proceed()
            val positioner = chain.thisObject
            if (positioner != null) {
                val taskInfo = taskInfoOf(positioner, positionerDecoration, decorationTaskInfo)
                if (taskInfo != null && resizing.remove(taskInfo.taskId) == true) {
                    show(taskInfo.taskId)
                    scheduleHide(taskInfo.taskId)
                }
            }
            result
        }

        logger.info("Corner handle indicator hook installed")
    }

    private fun isBottomCorner(ctrlType: Int): Boolean =
        (ctrlType and 8) != 0 && (ctrlType and 3) != 0

    private fun taskInfoOf(
        positioner: Any,
        decorationField: Field,
        taskInfoField: Field
    ): ActivityManager.RunningTaskInfo? {
        val decoration = decorationField.get(positioner) ?: return null
        return taskInfoField.get(decoration) as? ActivityManager.RunningTaskInfo
    }

    private fun show(taskId: Int) {
        setVisibility(taskId, View.VISIBLE)
    }

    private fun hide(taskId: Int) {
        setVisibility(taskId, View.GONE)
    }

    private fun whenAttached(view: View, action: () -> Unit) {
        if (view.isAttachedToWindow && view.handler != null) {
            action()
            return
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                action()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    private fun setVisibility(taskId: Int, visibility: Int) {
        val layout = layouts[taskId]
        if (layout == null) {
            logger.warn("Corner handle layout missing for task $taskId")
            return
        }
        val left = findView(layout, "ovc_wdb_bottom_gesture_left") ?: return
        val right = findView(layout, "ovc_wdb_bottom_gesture_right")
        val handler: Handler = left.handler ?: layout.handler ?: return
        handler.post {
            left.visibility = visibility
            right?.visibility = visibility
        }
        logger.info("Corner handle visibility=$visibility task=$taskId")
    }

    private fun viewsReady(layout: View): Boolean =
        findView(layout, "ovc_wdb_bottom_gesture_left") != null &&
            findView(layout, "ovc_wdb_bottom_gesture_right") != null

    private fun findView(layout: View, name: String): View? {
        val id = layout.context.resources.getIdentifier(
            name,
            "id",
            layout.context.packageName
        )
        if (id == 0) return null
        return layout.findViewById(id)
    }

    private fun scheduleHide(taskId: Int) {
        val token = SystemClock.uptimeMillis()
        hideTokens[taskId] = token
        val layout = layouts[taskId] ?: return
        val view = findView(layout, "ovc_wdb_bottom_gesture_left") ?: return
        val handler: Handler = view.handler ?: layout.handler ?: return
        logger.info("Corner handle hide scheduled: task=$taskId token=$token")
        handler.postDelayed({
            if (hideTokens[taskId] == token && resizing[taskId] != true) {
                hide(taskId)
            }
        }, HIDE_DELAY_MS)
    }

    private fun field(owner: Class<*>, name: String): Field? =
        try {
            findField(owner, name)
        } catch (_: Throwable) {
            null
        }

    private fun fieldByType(owner: Class<*>, type: Class<*>): Field? {
        var current: Class<*>? = owner
        while (current != null) {
            current.declaredFields.firstOrNull {
                type.isAssignableFrom(it.type)
            }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
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

    private companion object {
        const val HOST_CLASS = "android.view.SurfaceControlViewHost"
        const val POSITIONER_CLASS = "com.android.wm.shell.windowdecor.VeiledResizeTaskPositioner"
        const val DECORATION_CLASS = "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        const val HIDE_DELAY_MS = 5_000L
        val TASK_PATTERN = Regex("Task=(\\d+)")
    }
}
