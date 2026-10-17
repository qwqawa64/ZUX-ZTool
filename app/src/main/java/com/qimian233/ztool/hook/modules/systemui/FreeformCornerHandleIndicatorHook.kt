package com.qimian233.ztool.hook.modules.systemui

import android.app.ActivityManager
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private val injectors = ConcurrentHashMap<Int, Any>()
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
        val loader = param.defaultClassLoader
        val injectorClass = loadClass(loader, INJECTOR_CLASS) ?: return
        val positionerClass = loadClass(loader, POSITIONER_CLASS) ?: return
        val decorationClass = loadClass(loader, DECORATION_CLASS) ?: return

        val layoutField = field(injectorClass, "t") ?: return
        val positionerCtrl = field(positionerClass, "mCtrlType") ?: return
        val positionerDecoration = field(positionerClass, "mDesktopWindowDecoration") ?: return
        val decorationTaskInfo = field(decorationClass, "mTaskInfo") ?: return

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
            val firstLayout = injector != null && layoutField.get(injector) == null
            val result = chain.proceed()
            if (firstLayout && injector != null) {
                val taskInfo = chain.args.getOrNull(2) as? ActivityManager.RunningTaskInfo
                if (taskInfo != null) {
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
        val injector = injectors[taskId] ?: return
        val layout = injectorLayout(injector) ?: return
        findView(layout, "ovc_wdb_bottom_gesture_left")?.visibility = visibility
        findView(layout, "ovc_wdb_bottom_gesture_right")?.visibility = visibility
    }

    private fun injectorLayout(injector: Any): View? {
        val field = injector.javaClass.declaredFields.firstOrNull { it.name == "t" }
            ?: return null
        field.isAccessible = true
        return field.get(injector) as? View
    }

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
        handler.postDelayed({
            if (hideTokens[taskId] == token && resizing[taskId] != true) {
                hide(taskId)
            }
        }, HIDE_DELAY_MS)
    }

    private fun field(owner: Class<*>, name: String): Field? =
        try {
            owner.getDeclaredField(name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.error("Field $name not found on ${owner.name}", t)
            null
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
        const val HIDE_DELAY_MS = 5_000L
    }
}
