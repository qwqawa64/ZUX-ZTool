package com.qimian233.ztool.hook.modules.systemframework

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Freeform edge bubble: system_server side of the "shrink a freeform window into an
 * edge bubble" replication (see docs/research/oplus-float-handle-gesture-spec.md).
 *
 * App↔system_server channel: **ordered broadcasts**, not a published binder service —
 * SELinux only allows servicemanager names listed in service_contexts, so a custom
 * service cannot be registered. Commands arrive as ordered broadcasts targeted at the
 * "android" package (system_server's receiver) and return values travel back through
 * the broadcast result (resultCode + resultExtras); events go the other way as a
 * package-targeted broadcast to ZTool.
 *
 * Responsibilities:
 * 1. Register the command receiver on the system context (retry loop — the context is
 *    only complete a moment after system-server start).
 * 2. Execute task minimize/restore inside the WMS global lock via ATMS reflection,
 *    keeping restore-bounds in a server-side map.
 * 3. Pass ZTool's uid through [com.android.server.wm.OvFreeformService.checkPermission]
 *    so the app can use IOvFreeformService helpers without privileged permissions.
 * 4. Watch freeform task bounds ([com.android.server.wm.WindowContainer.setBounds]) to
 *    detect a window dragged to the screen edge and broadcast bubble add/remove events.
 *
 * The feature switch is [PreferenceKeys.FREEFORM_EDGE_BUBBLE] (read by
 * [com.qimian233.ztool.hook.base.BaseHookModule.isEnabled]). Toggling it requires a
 * system restart (scope HowToRestart.Reboot).
 */
@Suppress("PrivateApi", "SdkLintPrivateApi")
class FreeformEdgeBubbleHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FREEFORM_EDGE_BUBBLE.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    companion object {
        private const val ACTION_COMMAND = "com.qimian233.ztool.action.FREEFORM_BUBBLE_COMMAND"
        private const val ACTION_EVENT = "com.qimian233.ztool.action.FREEFORM_BUBBLE_EVENT"
        private const val ZTOOL_PACKAGE = "com.qimian233.ztool"

        // Command ids (app→server, extra "cmd"), mirrored in app-side FreeformBubbleBridge.
        private const val CMD_MINIMIZE_TASK = 1
        private const val CMD_RESTORE_TASK = 2
        private const val CMD_LIST_MINIMIZED = 3
        private const val CMD_GET_PACKAGE = 4

        // Event ids (server→app, extra "event").
        const val EVENT_BUBBLE_ADDED = 11
        const val EVENT_BUBBLE_REMOVED = 12

        private const val WINDOWING_MODE_FREEFORM = 5
        private const val MATCH_TASK_MODE_ANY = 0
        /** Fully-offscreen placement leaves this much extra gap beyond the window edge. */
        private const val OFFSCREEN_GAP_PX = 32
        /** Fraction of task width allowed to hang offscreen before the bubble triggers. */
        private const val EDGE_TRIGGER_FRACTION = 0.5f
        /** Window flush to the display edge within this margin counts as "at edge". */
        private const val EDGE_TOUCH_MARGIN_PX = 48
        private const val BRIDGE_REGISTER_MAX_ATTEMPTS = 120
        private const val BRIDGE_REGISTER_RETRY_INTERVAL_MS = 500L
        private const val ADB_SHELL_UID = 2000

        /** taskId → restore bounds, server-side session state (lost on reboot is fine). */
        private val restoreRects = ConcurrentHashMap<Int, Rect>()
        /** taskId → side (0 left / 1 right) of currently minimized tasks. */
        private val minimizedSides = ConcurrentHashMap<Int, Int>()
        /** taskId → package name, cached for events. */
        private val taskPackages = ConcurrentHashMap<Int, String>()
        /** Guards against re-entering the setBounds hook from our own moves. */
        private val internalMove = ThreadLocal.withInitial { false }

        @Volatile private var ztoolUid = -1
        @Volatile private var atmsHandlesCache: AtmsHandles? = null
        @Volatile private var hideShowControllerCache: Triple<Any?, Method?, Method?>? = null
        @Volatile private var bridgeRegistered = false
        private var eventPostHandler: Handler? = null
    }

    // region server-start wiring

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader

        installCheckPermissionPassthrough(classLoader)
        installEdgeDetectionHook(classLoader)
        registerBroadcastBridge(classLoader)
    }

    /**
     * Registers the command receiver on the system context. The context is not fully
     * built at system-server-start time, hence the retry loop.
     */
    private fun registerBroadcastBridge(classLoader: ClassLoader) {
        if (bridgeRegistered) return
        Thread({
            var lastError: Throwable? = null
            for (attempt in 1..BRIDGE_REGISTER_MAX_ATTEMPTS) {
                try {
                    if (registerCommandReceiver(classLoader)) return@Thread
                } catch (t: Throwable) {
                    lastError = (t as? java.lang.reflect.InvocationTargetException)?.cause ?: t
                }
                try {
                    Thread.sleep(BRIDGE_REGISTER_RETRY_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            logger.error(
                "register broadcast bridge failed after $BRIDGE_REGISTER_MAX_ATTEMPTS attempts",
                lastError)
        }, "ztool-bubble-bridge").start()
    }

    /** @return true when the receiver is registered. */
    private fun registerCommandReceiver(classLoader: ClassLoader): Boolean {
        val systemContext = resolveSystemContext(classLoader) ?: return false
        if (eventPostHandler == null) {
            val thread = HandlerThread("ztool-bubble-events")
            thread.start()
            eventPostHandler = Handler(thread.looper)
        }
        val filter = IntentFilter(ACTION_COMMAND)
        val receiver = CommandReceiver(classLoader)
        val registerReceiver = systemContext.javaClass.getMethod(
            "registerReceiver",
            BroadcastReceiver::class.java, IntentFilter::class.java, Int::class.javaPrimitiveType)
        val flags = if (Build.VERSION.SDK_INT >= 33) 0x2 /* RECEIVER_EXPORTED */ else 0
        registerReceiver.invoke(systemContext, receiver, filter, flags)
        eventPostContext = systemContext
        bridgeRegistered = true
        logger.info("freeform bubble broadcast bridge registered")
        return true
    }

    private fun resolveSystemContext(classLoader: ClassLoader): Context? {
        return try {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val current = atClass.getMethod("currentActivityThread").invoke(null) ?: return null
            val getSystemContext = atClass.getMethod("getSystemContext")
            getSystemContext.invoke(current) as? Context
        } catch (t: Throwable) {
            logger.debug("system context resolve failed: ${t.message}")
            null
        }
    }

    // endregion

    // region command receiver

    private inner class CommandReceiver(private val classLoader: ClassLoader) : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val uid = try {
                // Available since API 34; compileSdk 37.
                javaClass.getMethod("getSentFromUid").invoke(this) as Int
            } catch (_: Throwable) {
                -1
            }
            if (!isAllowedCaller(uid, classLoader)) {
                logger.warn("bridge command from untrusted uid=$uid")
                resultCode = 0
                return
            }
            // Execute off the system_server main thread: the WMS lock and reflection
            // resolution here must never contend with system UI work, and goAsync keeps
            // the ordered-broadcast result channel open until the handler finishes.
            val pending = goAsync()
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val handler = eventPostHandler
            if (handler == null) {
                val (code, extras) = executeCommand(intent, startedAt)
                pending.setResultCode(code)
                if (extras != null) pending.setResultExtras(extras)
                pending.finish()
                return
            }
            handler.post {
                try {
                    val (code, extras) = executeCommand(intent, startedAt)
                    pending.setResultCode(code)
                    if (extras != null) pending.setResultExtras(extras)
                } catch (t: Throwable) {
                    logger.error("bridge command crashed", t)
                    pending.setResultCode(0)
                } finally {
                    pending.finish()
                }
            }
        }

        private fun executeCommand(intent: Intent, startedAt: Long): Pair<Int, Bundle?> {
            var result: Int
            var extras: Bundle? = null
            when (intent.getIntExtra("cmd", -1)) {
                CMD_MINIMIZE_TASK -> {
                    val taskId = intent.getIntExtra("task_id", -1)
                    val side = intent.getIntExtra("side", 0)
                    result = if (withTaskHandles(classLoader) { doMinimize(it, taskId, side) }) 1 else 0
                }
                CMD_RESTORE_TASK -> {
                    val taskId = intent.getIntExtra("task_id", -1)
                    result = if (withTaskHandles(classLoader) { doRestore(it, taskId) }) 1 else 0
                }
                CMD_LIST_MINIMIZED -> {
                    val bundle = Bundle()
                    val taskIds = IntArray(minimizedSides.size)
                    val sides = IntArray(minimizedSides.size)
                    var i = 0
                    for ((taskId, side) in minimizedSides) {
                        taskIds[i] = taskId
                        sides[i] = side
                        i++
                    }
                    bundle.putIntArray("task_ids", taskIds)
                    bundle.putIntArray("sides", sides)
                    result = 1
                    extras = bundle
                }
                CMD_GET_PACKAGE -> {
                    val bundle = Bundle()
                    bundle.putString("pkg", taskPackages[intent.getIntExtra("task_id", -1)])
                    result = 1
                    extras = bundle
                }
                else -> result = 0
            }
            logger.info(
                "cmd=${intent.getIntExtra("cmd", -1)} result=$result " +
                    "took=${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
            return Pair(result, extras)
        }

        private fun isAllowedCaller(uid: Int, classLoader: ClassLoader): Boolean {
            if (uid == 0 || uid == ADB_SHELL_UID) return true
            if (uid == ztoolUid) return true
            if (uid <= 0) return false
            return try {
                val context = resolveSystemContext(classLoader) ?: return false
                val uidResolved = context.packageManager.getPackageUid(ZTOOL_PACKAGE, 0)
                ztoolUid = uidResolved
                uid == uidResolved
            } catch (_: Throwable) {
                false
            }
        }
    }

    // endregion

    // region task operations (WMS lock held)

    private inline fun withTaskHandles(
        classLoader: ClassLoader,
        block: (AtmsHandles) -> Boolean
    ): Boolean {
        return try {
            val handles = resolveAtmsHandles(classLoader)
            synchronized(handles.globalLock) { block(handles) }
        } catch (t: Throwable) {
            logger.error("bridge operation failed", t)
            false
        }
    }


    private class AtmsHandles(
        val atms: Any,
        val globalLock: Any,
        val rootWindowContainer: Any,
        val hideShowController: Any?,
        val bringToBack: Method?,
        val bringToFront: Method?,
        val anyTaskForId: Method,
        val taskClass: Class<*>,
        val getBounds: Method,
        val setBounds: Method,
        val moveToFront: Method?,
        val setLastNonFullscreenBounds: Method?,
        val getBaseIntent: Method?
    )

    private fun resolveAtms(classLoader: ClassLoader): Any {
        val atmsClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
        return try {
            val getInstance = atmsClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            getInstance.invoke(null)
        } catch (_: Throwable) {
            // Fallback: LocalServices holds the ActivityTaskManagerInternal impl; scan
            // its fields for a value that IS the ATMS instance (ZUX renames mService).
            val localServicesClass = classLoader.loadClass("com.android.server.LocalServices")
            val internalClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerInternal")
            val getService = localServicesClass.getDeclaredMethod("getService", Class::class.java)
            val internal = getService.invoke(null, internalClass)
                ?: error("ActivityTaskManagerInternal not in LocalServices")
            for (cls in generateSequence(internal.javaClass) { it.superclass }) {
                for (field in cls.declaredFields) {
                    if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                        try {
                            field.isAccessible = true
                            val value = field.get(internal)
                            if (value != null && value.javaClass.name == atmsClass.name) return value
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            error("no ATMS reference found on " + internal.javaClass.name)
        }
    }

    private fun resolveAtmsHandles(classLoader: ClassLoader): AtmsHandles {
        atmsHandlesCache?.let { cached ->
            if (cached.taskClass.classLoader === classLoader) return cached
        }
        val handles = resolveAtmsHandlesUncached(classLoader)
        atmsHandlesCache = handles
        return handles
    }

    private fun resolveAtmsHandlesUncached(classLoader: ClassLoader): AtmsHandles {
        val atms = resolveAtms(classLoader)
        val atmsClass = atms.javaClass
        val globalLock = findField(atmsClass, "mGlobalLock").get(atms)
            ?: error("ATMS.mGlobalLock is null")
        val rwc = findField(atmsClass, "mRootWindowContainer").get(atms)
            ?: error("ATMS.mRootWindowContainer is null")
        val rwcClass = classLoader.loadClass("com.android.server.wm.RootWindowContainer")
        val anyTaskForId = rwcClass.getMethod(
            "anyTaskForId", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

        val taskClass = classLoader.loadClass("com.android.server.wm.Task")
        val hideShow = resolveHideShowController(classLoader, atms)
        return AtmsHandles(
            atms, globalLock, rwc, hideShow?.first, hideShow?.second, hideShow?.third,
            anyTaskForId, taskClass,
            taskClass.getMethod("getBounds"),
            taskClass.getMethod("setBounds", Rect::class.java),
            try { taskClass.getMethod("moveToFront", String::class.java) } catch (_: Throwable) { null },
            try { taskClass.getMethod("setLastNonFullscreenBounds", Rect::class.java) } catch (_: Throwable) { null },
            try { taskClass.getMethod("getBaseIntent") } catch (_: Throwable) { null }
        )
    }

    /**
     * Resolves ZUI's native freeform hide/show controller ([OvfWmHideShowController]):
     * minimize = [bringToBack] (task docks behind home via shell transition, immune to
     * the freeform bounds clamp), restore = [bringToFront]. Returns null when absent;
     * the plain Task.setBounds path stays as fallback.
     */
    private fun resolveHideShowController(classLoader: ClassLoader, atms: Any): Triple<Any?, Method?, Method?>? {
        if (hideShowControllerCache != null) return hideShowControllerCache
        return try {
            var manager: Any? = null
            val steps = StringBuilder()
            // Path 1/2: named getters on ATMS.
            for (getter in arrayOf("getOvfMgr", "getOvcWmManager")) {
                try {
                    val result = atms.javaClass.getMethod(getter).invoke(atms)
                    if (result != null) {
                        steps.append(getter).append("=ok ")
                        if (result.javaClass.simpleName == "OvfWmFreeformManager") {
                            manager = result
                            break
                        }
                        // OvcWmManager: drill into its freeform manager.
                        try {
                            val fm = result.javaClass.getMethod("getFreeformManager").invoke(result)
                            if (fm != null) {
                                steps.append("getFreeformManager=ok ")
                                manager = fm
                                break
                            }
                        } catch (t: Throwable) {
                            steps.append("getFreeformManager=fail ").append(t.message).append(' ')
                        }
                    }
                } catch (t: Throwable) {
                    steps.append(getter).append("=fail ").append(t.message).append(' ')
                }
            }
            // Path 3: field-type scan on ATMS itself.
            if (manager == null) {
                for (cls in generateSequence(atms.javaClass) { it.superclass }) {
                    for (field in cls.declaredFields) {
                        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                        try {
                            field.isAccessible = true
                            val value = field.get(atms)
                            if (value != null && value.javaClass.simpleName == "OvfWmFreeformManager") {
                                manager = value
                                steps.append("atmsFieldScan=ok ")
                                break
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    if (manager != null) break
                }
            }
            if (manager == null) {
                logger.info("hideShow resolve failed: no OvfWmFreeformManager; " + steps)
                return null
            }
            var controller: Any? = null
            // Preferred: named accessor on OvfWmFreeformManager.
            for (getter in arrayOf("getHideShowCtrl", "getHideShowController")) {
                try {
                    controller = manager.javaClass.getMethod(getter).invoke(manager)
                    steps.append(getter).append(if (controller != null) "=ok " else "=null ")
                    if (controller != null) break
                } catch (t: Throwable) {
                    steps.append(getter).append("=fail ").append(t.message).append(' ')
                }
            }
            // Fallback: field-type scan on the manager.
            if (controller == null) {
                for (cls in generateSequence(manager.javaClass) { it.superclass }) {
                    for (field in cls.declaredFields) {
                        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                        try {
                            field.isAccessible = true
                            val value = field.get(manager)
                            if (value != null && value.javaClass.simpleName == "OvfWmHideShowController") {
                                controller = value
                                steps.append("mgrFieldScan=ok ")
                                break
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    if (controller != null) break
                }
            }
            if (controller == null) {
                logger.info("hideShow resolve failed: no controller; " + steps)
                return null
            }
            val toBack = controller.javaClass.getMethod("bringToBack",
                classLoader.loadClass("com.android.server.wm.Task"))
            val toFront = controller.javaClass.getMethod("bringToFront",
                classLoader.loadClass("com.android.server.wm.Task"),
                String::class.java,
                classLoader.loadClass("com.android.server.wm.TransitionController"),
                classLoader.loadClass("com.android.server.wm.Transition"))
            hideShowControllerCache = Triple(controller, toBack, toFront)
            logger.info("hideShow resolved: " + steps)
            hideShowControllerCache
        } catch (t: Throwable) {
            logger.debug("resolveHideShowController failed: ${t.message}")
            null
        }
    }

    private fun findTask(handles: AtmsHandles, taskId: Int): Any? =
        handles.anyTaskForId.invoke(handles.rootWindowContainer, taskId, MATCH_TASK_MODE_ANY)

    private fun doMinimize(handles: AtmsHandles, taskId: Int, side: Int): Boolean {
        val task = findTask(handles, taskId) ?: run {
            logger.warn("minimize: task $taskId not found")
            return false
        }
        val bounds = handles.getBounds.invoke(task) as Rect
        if (bounds.isEmpty) return false
        restoreRects[taskId] = Rect(bounds)
        minimizedSides[taskId] = side
        try {
            handles.setLastNonFullscreenBounds?.invoke(task, bounds)
        } catch (t: Throwable) {
            logger.debug("setLastNonFullscreenBounds failed: ${t.message}")
        }
        if (handles.hideShowController != null && handles.bringToBack != null) {
            // ZUI native path: dock the task behind home with a shell hide transition.
            // The freeform layout policy would clamp arbitrary offscreen bounds back
            // on-screen, so bounds-shifting is only a fallback here.
            handles.bringToBack.invoke(handles.hideShowController, task)
            logger.info("minimize task=$taskId side=$side via bringToBack, bounds=$bounds")
            return true
        }
        val offscreen = Rect(bounds)
        offscreen.offset(if (side == 0) -(bounds.width() + OFFSCREEN_GAP_PX) else bounds.width() + OFFSCREEN_GAP_PX, 0)
        internalMove.set(true)
        try {
            handles.setBounds.invoke(task, offscreen)
        } finally {
            internalMove.set(false)
        }
        logger.info("minimize task=$taskId side=$side bounds=$bounds -> $offscreen")
        return true
    }

    private fun doRestore(handles: AtmsHandles, taskId: Int): Boolean {
        val task = findTask(handles, taskId) ?: run {
            logger.warn("restore: task $taskId not found")
            restoreRects.remove(taskId)
            minimizedSides.remove(taskId)
            return false
        }
        val target = restoreRects.remove(taskId) ?: run {
            logger.warn("restore: no stored bounds for task $taskId")
            return false
        }
        minimizedSides.remove(taskId)
        if (handles.hideShowController != null && handles.bringToFront != null) {
            handles.bringToFront.invoke(
                handles.hideShowController, task, "ztool_freeform_bubble", null, null)
            notifyBubbleRemoved(taskId)
            logger.info("restore task=$taskId via bringToFront -> $target")
            return true
        }
        internalMove.set(true)
        try {
            handles.setBounds.invoke(task, target)
            try {
                handles.moveToFront?.invoke(task, "ztool_freeform_bubble")
            } catch (t: Throwable) {
                logger.debug("moveToFront failed: ${t.message}")
            }
        } finally {
            internalMove.set(false)
        }
        // internalMove suppressed the setBounds hook, so the removal event must be
        // sent here explicitly.
        notifyBubbleRemoved(taskId)
        logger.info("restore task=$taskId -> $target")
        return true
    }

    // endregion

    // region hooks

    /**
     * Lets ZTool call IOvFreeformService helpers (setLastNonFullscreenBounds, bounds
     * queries) without the signature|privileged zui.permission.OVFREEFORM_* grants.
     * Only the calling uid of the ZTool app bypasses; everyone else proceeds unchanged.
     */
    @Throws(Throwable::class)
    private fun installCheckPermissionPassthrough(classLoader: ClassLoader) {
        val serviceClass = try {
            classLoader.loadClass("com.android.server.wm.OvFreeformService")
        } catch (_: Throwable) {
            logger.debug("OvFreeformService not present on this build, skip passthrough")
            return
        }
        val checkPermission: Method = try {
            findMethod(serviceClass, "checkPermission", String::class.java)
        } catch (_: Throwable) {
            logger.debug("OvFreeformService.checkPermission not found, skip passthrough")
            return
        }
        hookWithId(checkPermission, "freeform_edge_bubble_ovf_perm") { chain ->
            val callingUid = Binder.getCallingUid()
            if (callingUid == ztoolUid || callingUid == ADB_SHELL_UID) {
                logger.debug("checkPermission passthrough for uid=$callingUid")
                return@hookWithId null
            }
            chain.proceed()
        }
    }

    /**
     * Detects freeform tasks dragged to the screen edge. When a freeform task's new
     * bounds hang offscreen by more than [EDGE_TRIGGER_FRACTION] of its width, the app
     * overlay is told to show a bubble; when a tracked task comes back mostly onscreen,
     * the bubble is dismissed.
     */
    @Throws(Throwable::class)
    private fun installEdgeDetectionHook(classLoader: ClassLoader) {
        val windowContainerClass = classLoader.loadClass("com.android.server.wm.WindowContainer")
        val setBounds: Method = findMethod(windowContainerClass, "setBounds", Rect::class.java)
        val taskClass = classLoader.loadClass("com.android.server.wm.Task")
        hookWithId(setBounds, "freeform_edge_bubble_detect") { chain ->
            val task = chain.thisObject
            if (internalMove.get() || !taskClass.isInstance(task)) {
                return@hookWithId chain.proceed()
            }
            val newBounds = chain.getArg(0) as Rect
            val windowingMode = try {
                taskClass.getMethod("getWindowingMode").invoke(task) as Int
            } catch (_: Throwable) { -1 }
            if (windowingMode != WINDOWING_MODE_FREEFORM || newBounds.isEmpty) {
                return@hookWithId chain.proceed()
            }
            val displayBounds = try {
                val dc = taskClass.getMethod("getDisplayContent").invoke(task)
                dc?.javaClass?.getMethod("getBounds")?.invoke(dc) as? Rect
            } catch (_: Throwable) { null }
            val width = newBounds.width().coerceAtLeast(1)
            val offLeft = displayBounds?.left?.let { (it - newBounds.left).coerceAtLeast(0) } ?: 0
            val offRight = displayBounds?.right?.let { (newBounds.right - it).coerceAtLeast(0) } ?: 0
            val taskId = task.taskIdCompat()
            val wasTracked = minimizedSides.containsKey(taskId)
            val side = when {
                offLeft >= width * EDGE_TRIGGER_FRACTION ||
                    (displayBounds != null && newBounds.left - displayBounds.left <= EDGE_TOUCH_MARGIN_PX) -> 0
                offRight >= width * EDGE_TRIGGER_FRACTION ||
                    (displayBounds != null && displayBounds.right - newBounds.right <= EDGE_TOUCH_MARGIN_PX) -> 1
                else -> -1
            }
            chain.proceed()
            if (side >= 0 && !wasTracked) {
                // First time reaching the edge: record the onscreen bounds the user had,
                // then dock the task behind home (ZUI native hide) so only the bubble
                // remains visible.
                restoreRects[taskId] = Rect(newBounds)
                minimizedSides[taskId] = side
                notifyBubbleAdded(task, taskClass, side)
                try {
                    val h = resolveAtmsHandles(classLoader)
                    if (h.hideShowController != null && h.bringToBack != null) {
                        h.bringToBack.invoke(h.hideShowController, task)
                        logger.info("auto-dock task=$taskId via bringToBack")
                    }
                } catch (t: Throwable) {
                    logger.warn("auto-dock failed: ${t.message}")
                }
            } else if (side < 0 && wasTracked) {
                minimizedSides.remove(taskId)
                restoreRects.remove(taskId)
                notifyBubbleRemoved(taskId)
            }
            null
        }
    }

    private fun Any.taskIdCompat(): Int {
        return try {
            this.javaClass.getMethod("getTaskId").invoke(this) as Int
        } catch (_: Throwable) {
            val f = this.javaClass.getField("mTaskId")
            f.getInt(this)
        }
    }

    private fun notifyBubbleAdded(task: Any, taskClass: Class<*>, side: Int) {
        val taskId = task.taskIdCompat()
        val pkg = try {
            val intent = taskClass.getMethod("getBaseIntent").invoke(task) as? Intent
            intent?.component?.packageName ?: intent?.`package`
        } catch (_: Throwable) { null }
        if (pkg != null) taskPackages[taskId] = pkg
        minimizedSides[taskId] = side
        sendEvent(EVENT_BUBBLE_ADDED) { intent ->
            intent.putExtra("task_id", taskId)
            intent.putExtra("side", side)
            intent.putExtra("pkg", pkg)
        }
        logger.info("bubble add task=$taskId side=$side pkg=$pkg")
    }

    private fun notifyBubbleRemoved(taskId: Int) {
        taskPackages.remove(taskId)
        sendEvent(EVENT_BUBBLE_REMOVED) { intent ->
            intent.putExtra("task_id", taskId)
        }
        logger.info("bubble remove task=$taskId")
    }

    private fun sendEvent(event: Int, payload: (Intent) -> Unit) {
        val handler = eventPostHandler ?: return
        handler.post {
            try {
                val context = eventPostContext ?: return@post
                val intent = Intent(ACTION_EVENT).setPackage(ZTOOL_PACKAGE)
                intent.putExtra("event", event)
                payload(intent)
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                logger.warn("sendEvent event=$event failed: ${t.message}")
            }
        }
    }

    @Volatile private var eventPostContext: Context? = null

    // endregion
}
