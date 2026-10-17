package com.qimian233.ztool.hook.modules.systemui

import android.app.ActivityManager
import android.os.Handler
import android.os.SystemClock
import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows the bottom-corner handles only after creation or corner resizing, then hides them after 5 s.
 */
@Suppress("PrivateApi")
class FreeformCornerHandleIndicatorHook : AppHookModule() {
    private val injectors = ConcurrentHashMap<Int, Any>()
    private val hideTokens = ConcurrentHashMap<Int, Long>()
    private val resizing = ConcurrentHashMap<Int, Boolean>()
    private val seenTasks = ConcurrentHashMap.newKeySet<Int>()
    private var borderViewField: Field? = null

    override fun getModuleName(): String = "system_ui_corner_handle_auto_hide"

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun isEnabled(): Boolean = true

    override fun handleLoadPackage(param: PackageLoadedParam) {
        logger.info("Corner handle indicator loadPackage")
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("Corner handle indicator install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        logger.info("Corner handle indicator install start")
        val loader = param.defaultClassLoader
        val injectorClass = loadClass(loader, INJECTOR_CLASS) ?: return
        val positionerClass = loadClass(loader, POSITIONER_CLASS) ?: return
        val decorationClass = loadClass(loader, DECORATION_CLASS) ?: return

        val borderViewType = loadClass(loader, BORDER_VIEW_CLASS)
        borderViewField = field(injectorClass, "mBorderView")
            ?: borderViewType?.let { fieldByType(injectorClass, it) }
            ?: run {
                logger.error("Border view field not found")
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

        val updateMethod = injectorClass.declaredMethods.firstOrNull {
            it.name == "updateViewsAndSurfaces_Ovf"
        } ?: run {
            logger.warn("updateViewsAndSurfaces_Ovf not found")
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

        hookWithId(updateMethod, "corner_handle_creation") { chain ->
            val injector = chain.thisObject
            val result = chain.proceed()
            val taskInfo = chain.args.getOrNull(2) as? ActivityManager.RunningTaskInfo
            if (injector != null && taskInfo != null &&
                !seenTasks.contains(taskInfo.taskId)
            ) {
                val layout = injectorLayout(injector)
                if (layout == null || !viewsReady(layout)) {
                    logger.debug(
                        "Corner handle views not ready for task ${taskInfo.taskId}; " +
                            "layout=${layout?.javaClass?.simpleName}, " +
                            "children=${(layout as? android.view.ViewGroup)?.childCount}"
                    )
                } else {
                    seenTasks.add(taskInfo.taskId)
                    logger.info(
                        "Corner handle first update: taskId=${taskInfo.taskId}, " +
                            "resizeable=${isResizeable(taskInfo)}"
                    )
                    if (isResizeable(taskInfo)) {
                        injectors[taskInfo.taskId] = injector
                        show(taskInfo.taskId)
                        scheduleHide(taskInfo.taskId)
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

    private fun isResizeable(taskInfo: ActivityManager.RunningTaskInfo): Boolean =
        try {
            taskInfo.javaClass.getField("isResizeable").getBoolean(taskInfo)
        } catch (_: Throwable) {
            true
        }

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

    private fun setVisibility(taskId: Int, visibility: Int) {
        val injector = injectors[taskId]
        if (injector == null) {
            logger.warn("Corner handle injector missing for task $taskId")
            return
        }
        val layout = injectorLayout(injector)
        if (layout == null) {
            logger.warn("Corner handle layout missing for task $taskId")
            return
        }
        findView(layout, "ovc_wdb_bottom_gesture_left")?.let { left ->
            val right = findView(layout, "ovc_wdb_bottom_gesture_right")
            val handler = left.handler ?: layout.handler ?: return
            handler.post {
                left.visibility = visibility
                right?.visibility = visibility
            }
        }
        logger.info("Corner handle visibility=$visibility task=$taskId")
    }

    private fun viewsReady(layout: View): Boolean =
        findView(layout, "ovc_wdb_bottom_gesture_left") != null &&
            findView(layout, "ovc_wdb_bottom_gesture_right") != null

    private fun injectorLayout(injector: Any): View? {
        borderViewField?.let { return it.get(injector) as? View }
        val type = try {
            injector.javaClass.classLoader?.loadClass(BORDER_VIEW_CLASS)
        } catch (_: Throwable) {
            null
        } ?: return null
        return fieldByType(injector.javaClass, type)?.get(injector) as? View
    }

    private fun findView(layout: View, name: String): View? {
        val id = layout.context.resources.getIdentifier(
            name,
            "id",
            layout.context.packageName
        )
        if (id == 0) {
            logger.warn("Corner handle id not found: $name")
            return null
        }
        return layout.findViewById(id)
    }

    private fun scheduleHide(taskId: Int) {
        val token = SystemClock.uptimeMillis()
        hideTokens[taskId] = token
        logger.info("Corner handle hide scheduled: task=$taskId token=$token")
        val injector = injectors[taskId] ?: return
        val layout = injectorLayout(injector) ?: return
        val view = findView(layout, "ovc_wdb_bottom_gesture_left") ?: return
        val handler: Handler = view.handler ?: layout.handler ?: return
        handler.postDelayed({
            logger.info("Corner handle hide fired: task=$taskId token=$token")
            if (hideTokens[taskId] == token && resizing[taskId] != true) {
                hide(taskId)
            }
        }, HIDE_DELAY_MS)
    }

    private fun field(owner: Class<*>, name: String): Field? =
        try {
            findField(owner, name)
        } catch (t: Throwable) {
            logger.warn("Field $name not found on ${owner.name}")
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
        const val INJECTOR_CLASS = "com.android.wm.shell.windowdecor.OvWindowDecorationInjector"
        const val POSITIONER_CLASS = "com.android.wm.shell.windowdecor.VeiledResizeTaskPositioner"
        const val DECORATION_CLASS = "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        const val BORDER_VIEW_CLASS = "com.android.wm.shell.windowdecor.WindowDecorLinearLayout"
        const val HIDE_DELAY_MS = 5_000L
    }
}
