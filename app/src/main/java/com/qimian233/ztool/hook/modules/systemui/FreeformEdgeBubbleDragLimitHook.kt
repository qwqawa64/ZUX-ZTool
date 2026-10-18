package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.Rect
import com.qimian233.ztool.data.freeform.FreeformEdgeBubbleConfig
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlin.math.ceil

/**
 * Widens the horizontal drag-end limit to the configured edge-bubble trigger ratio.
 *
 * ZUI's valid drag area keeps about half of the window width on screen, so a window can
 * never hang far enough offscreen for ratios at or above 50 %. The vertical limit stays.
 */
@SuppressLint("PrivateApi")
class FreeformEdgeBubbleDragLimitHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FREEFORM_EDGE_BUBBLE.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("edge bubble drag limit install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val decorClass = try {
            loader.loadClass(DECOR_CLASS)
        } catch (t: Throwable) {
            logger.warn("$DECOR_CLASS not found: ${t.message}")
            return
        }
        val calculateValidDragArea = try {
            findMethod(decorClass, "calculateValidDragArea")
        } catch (t: Throwable) {
            logger.warn("calculateValidDragArea not found: ${t.message}")
            return
        }
        val taskInfoField = try {
            findField(decorClass, "mTaskInfo")
        } catch (t: Throwable) {
            logger.warn("DesktopModeWindowDecoration.mTaskInfo not found: ${t.message}")
            return
        }
        val displayControllerField = try {
            findField(decorClass, "mDisplayController")
        } catch (t: Throwable) {
            logger.warn("DesktopModeWindowDecoration.mDisplayController not found: ${t.message}")
            return
        }
        val getDisplayLayout = try {
            displayControllerField.type.getMethod("getDisplayLayout", Int::class.javaPrimitiveType)
        } catch (t: Throwable) {
            logger.warn("DisplayController.getDisplayLayout not found: ${t.message}")
            return
        }
        val displayWidthField = try {
            getDisplayLayout.returnType.getField("mWidth")
        } catch (t: Throwable) {
            logger.warn("DisplayLayout.mWidth not found: ${t.message}")
            return
        }
        // TaskInfo.configuration / displayId are hidden API: reflect them.
        val taskInfoClass = ActivityManager.RunningTaskInfo::class.java
        val configurationGetter = try {
            taskInfoClass.getMethod("getConfiguration")
        } catch (_: Throwable) {
            null
        }
        val configurationField = try {
            taskInfoClass.getField("configuration")
        } catch (_: Throwable) {
            null
        }
        if (configurationGetter == null && configurationField == null) {
            logger.warn("RunningTaskInfo configuration not found")
            return
        }
        // WindowConfiguration is not in the public SDK; read it reflectively.
        val windowConfigField = try {
            android.content.res.Configuration::class.java.getField("windowConfiguration")
        } catch (_: Throwable) {
            null
        }
        val windowConfigGetter = try {
            android.content.res.Configuration::class.java.getMethod("getWindowConfiguration")
        } catch (_: Throwable) {
            null
        }
        if (windowConfigField == null && windowConfigGetter == null) {
            logger.warn("Configuration.windowConfiguration not found")
            return
        }
        val windowConfigBoundsMethod = try {
            (windowConfigField?.type ?: windowConfigGetter!!.returnType)
                .getMethod("getBounds")
        } catch (t: Throwable) {
            logger.warn("WindowConfiguration.getBounds not found: ${t.message}")
            return
        }
        val displayIdField = try {
            taskInfoClass.getField("displayId")
        } catch (_: Throwable) {
            null
        }
        val displayIdGetter = try {
            taskInfoClass.getMethod("getDisplayId")
        } catch (_: Throwable) {
            null
        }
        if (displayIdField == null && displayIdGetter == null) {
            logger.warn("RunningTaskInfo displayId not found")
            return
        }
        val triggerPercent = FreeformEdgeBubbleConfig.normalizeTriggerPercent(
            try {
                remotePreferences.getInt(
                    PreferenceKeys.FREEFORM_EDGE_BUBBLE_TRIGGER_PERCENT.name,
                    PreferenceKeys.FREEFORM_EDGE_BUBBLE_TRIGGER_PERCENT.default
                )
            } catch (_: Throwable) {
                PreferenceKeys.FREEFORM_EDGE_BUBBLE_TRIGGER_PERCENT.default
            }
        )

        hookWithId(calculateValidDragArea, "freeform_edge_bubble_drag_limit") { chain ->
            val result = chain.proceed()
            val area = result as? Rect ?: return@hookWithId result
            val decor = chain.thisObject ?: return@hookWithId area
            val taskInfo = taskInfoField.get(decor) as? ActivityManager.RunningTaskInfo
                ?: return@hookWithId area
            val configuration = try {
                configurationGetter?.invoke(taskInfo) ?: configurationField?.get(taskInfo)
            } catch (_: Throwable) {
                null
            } as? android.content.res.Configuration ?: return@hookWithId area
            val windowConfig = try {
                windowConfigField?.get(configuration)
                    ?: windowConfigGetter?.invoke(configuration)
            } catch (_: Throwable) {
                null
            } ?: return@hookWithId area
            val width = try {
                (windowConfigBoundsMethod.invoke(windowConfig) as? Rect)?.width() ?: 0
            } catch (_: Throwable) {
                0
            }
            if (width <= 0) return@hookWithId area
            val displayId = try {
                displayIdField?.getInt(taskInfo)
                    ?: (displayIdGetter?.invoke(taskInfo) as? Int)
            } catch (_: Throwable) {
                null
            } ?: return@hookWithId area
            val displayController = displayControllerField.get(decor)
                ?: return@hookWithId area
            val displayLayout = try {
                getDisplayLayout.invoke(displayController, displayId)
            } catch (_: Throwable) {
                null
            } ?: return@hookWithId area
            val displayWidth = try {
                displayWidthField.getInt(displayLayout)
            } catch (_: Throwable) {
                return@hookWithId area
            }
            val triggerPx = ceil(width * triggerPercent / 100.0).toInt() + TRIGGER_SLACK_PX
            area.left = minOf(area.left, -triggerPx)
            area.right = maxOf(area.right, displayWidth + triggerPx - width)
            area
        }
    }

    private companion object {
        const val DECOR_CLASS = "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"
        /** Extra overshoot so the >= trigger comparison stays reachable after rounding. */
        const val TRIGGER_SLACK_PX = 64
    }
}
